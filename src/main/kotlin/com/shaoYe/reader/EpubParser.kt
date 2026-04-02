package com.shaoYe.reader

import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.domain.TOCReference
import nl.siegmann.epublib.epub.EpubReader
import org.jsoup.Jsoup
import java.io.File
import java.nio.charset.Charset
import java.util.Base64
import java.util.Locale

object EpubParser {

    private fun getAppHtml(contentHtml: String, colors: ThemeColors, fontSize: Int, theme: String?, fontFamily: String?, bridgeJs: String? = null): String {
        val actualTheme = theme ?: "white"
        val actualFontFamily = fontFamily ?: "sans"
        val appleFontStack = "'PingFang SC', 'Hiragino Sans GB', 'Heiti SC', 'Microsoft YaHei', 'WenQuanYi Micro Hei', sans-serif"
        val cssFontFamily = if (actualFontFamily == "serif") "Palatino, \"Palatino Linotype\", \"Book Antiqua\", Georgia, serif" else "-apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, $appleFontStack"

        val bridgeScript = bridgeJs ?: ""

        return """
            <!DOCTYPE html>
            <html lang='en' data-theme='$actualTheme'>
            <head>
                <meta charset='UTF-8'>
                <style>
                    :root {
                        /* Base System Colors */
                        --bg: ${colors.bg}; --text: ${colors.text};
                        --sidebar-bg: ${colors.sidebarBg};
                        --border: ${colors.border};
                        --icon-stroke: ${colors.text};
                        --hover-bg: ${if (colors.bg.startsWith("#2")) "rgba(255,255,255,0.08)" else "rgba(0,0,0,0.04)"};
                        --font-size: ${fontSize}px;
                        --font-family: $cssFontFamily;
                        --line-height: 1.8;
                        --letter-spacing: 0.03em;
                    }

                    /* Theme: White (Soft) */
                    :root[data-theme="white"] {
                        --bg: #f9f9f9; --text: #2c2c2c;
                        --sidebar-bg: rgba(249, 249, 249, 0.9);
                        --border: #e0e0e0; --icon-stroke: #2c2c2c;
                        --hover-bg: rgba(0,0,0,0.05);
                    }

                    /* Theme: Sepia (Eye Care) */
                    :root[data-theme="sepia"] {
                        --bg: #f4ecd8; --text: #5b4636;
                        --sidebar-bg: rgba(244, 236, 216, 0.95);
                        --border: #e2d7bd; --icon-stroke: #5b4636;
                        --hover-bg: rgba(91, 70, 54, 0.08);
                    }

                    /* Theme: Dark (Night) */
                    :root[data-theme="dark"] {
                        --bg: #1a1a1a; --text: #b0b0b0;
                        --sidebar-bg: rgba(26, 26, 26, 0.9);
                        --border: #333333; --icon-stroke: #b0b0b0;
                        --hover-bg: rgba(255,255,255,0.08);
                    }
                    
                    body { 
                        font-family: var(--font-family);
                        font-size: var(--font-size) !important;
                        line-height: var(--line-height);
                        letter-spacing: var(--letter-spacing);
                        -webkit-font-smoothing: antialiased;
                        -moz-osx-font-smoothing: grayscale;
                        margin: 0 !important; padding: 0 !important; width: 100% !important; 
                        max-width: none !important; box-sizing: border-box !important;
                        background-color: var(--bg); color: var(--text); overflow: hidden; 
                        transition: background-color 0.3s ease, color 0.3s ease;
                        overflow-wrap: break-word; word-wrap: break-word; word-break: break-word;
                    }
                    * { box-sizing: border-box; }
                    
                    /* --- 核心优化：抗闪烁 CSS --- */
                    /* 当处于 resizing 状态时，强制关闭所有动画和吸附，像石头一样稳 */
                    .resizing, .resizing * {
                        transition: none !important;
                        scroll-behavior: auto !important;
                    }


                    body, html { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: var(--bg); color: var(--text); font-family: var(--font-family); font-size: var(--font-size) !important; }
                    
                    #content { width: 100%; height: 100%; display: flex; flex-direction: column; overflow: hidden; position: relative; background: var(--bg); }
                    #reader-wrapper { flex: 1; width: 100%; height: 100%; overflow-x: scroll; overflow-y: hidden; outline: none; transition: opacity 0.2s; will-change: transform; }
                    #reader-wrapper.resizing { cursor: col-resize; opacity: 0.8; }
                    #reader-wrapper.resizing * { transition: none !important; animation: none !important; }
                    #reader-text { height: 100%; box-sizing: border-box; column-fill: auto; position: relative; column-gap: 0; }
                    
                    .chapter { break-before: column; }
                    .page-content { padding: 0; margin: 0; width: 100%; box-sizing: border-box; }
                    h1, h2, h3, h4, h5, h6 { break-inside: avoid; break-after: avoid; font-weight: bold; line-height: 1.2; }
                    h1, h2, h3, h4, h5, h6 { line-height: 1.2; margin: 0 !important; padding: 0 !important; font-weight: 600; break-after: avoid; }
                    h1 { font-size: 1.1em !important; }
                    h2 { font-size: 1.05em !important; }
                    h3, h4, h5, h6 { font-size: 1.0em !important; }
                    
                    p { margin: 0 !important; padding: 8px 0 !important; line-height: var(--line-height); text-align: justify; letter-spacing: var(--letter-spacing); }
                    img { max-width: 100%; max-height: 80vh; height: auto; display: block; margin: 10px auto; border-radius: 8px; break-inside: avoid; }
                    
                    /* Disable all book links */
                    a { pointer-events: none !important; cursor: default !important; color: inherit !important; text-decoration: none !important; }

                    /* Calibration for perfect column alignment & Anti-Tearing (Requirement 13) */
                    #reader-text { 
                        padding: 0 !important; margin: 0 !important; box-sizing: border-box; 
                        column-fill: auto;
                    }
                    .chapter { break-before: column; }
                    .chapter:not(.active-chapter) { display: none !important; }
                    .page-content { padding: 5px !important; box-sizing: border-box; min-height: 100%; }
                    
                    ::-webkit-scrollbar { display: none !important; }
                    .search-highlight { background-color: #ffeb3b; color: #000; border-radius: 2px; }
                    .active-match { background-color: #ff9800 !important; color: #fff !important; box-shadow: 0 0 4px rgba(0,0,0,0.4); }

                    /* --- Privacy: Absolute focus-lost hiding (Requirement 14) --- */
                    body.focus-lost:not(.search-active):not(.dialog-active) #content { 
                        opacity: 0 !important; visibility: hidden !important; pointer-events: none !important; transition-delay: 0s; 
                    }
                    #content { transition: opacity 0.2s ease-in-out; }
                </style>
            </head>
            <body>
                <div id="content">
                    <div id="reader-wrapper" tabindex="0">
                        <div id="reader-text">$contentHtml</div>
                    </div>
                </div>
                
                <script>
                    // --- Bridge Injection ---
                    $bridgeScript
                </script>

                <script>
                    // --- Diagnostic & Global State ---
                    // --- Diagnostic Panel ---
                    var diag = null;
                    function dbg(m) { 
                        console.log("[RM] " + m);
                        if(!diag) diag = document.getElementById('diag');
                        if(diag) diag.textContent = m.substring(0, 100);
                    }

                    window.onerror = function(m, u, l, c, e) {
                        dbg("ERR: " + m + " at " + l + ":" + c);
                        if(window.readerBridge && window.readerBridge.sendSearchResults) window.readerBridge.sendSearchResults("info|||ERR: " + m);
                        return false;
                    };

                    // Define bridge early
                    try {
                        var bridgeScriptContent = "${(bridgeJs ?: "").replace("\"", "\\\"").replace("\n", " ")}";
                        // Using eval as a fallback for bridge injection if needed
                        $bridgeScript
                    } catch(e) { dbg("Bridge Fail: " + e.message); }

                    var wrapper = null;
                    var textContainer = null;
                    var allElements = [];
                    var isResizing = false;
                    var resizeTimer = null;
                    var currentAnchorIndex = 0;
                    var lastWheelTime = 0;
                    var saveTimeout = null;
                    var progressTimeout = null;
                    var searchMatches = [];
                    var activeForcedBreaks = []; // Optimization: track forced breaks
                    var layoutCache = { w: 0, h: 0, scrollWidth: 0, totalPages: 1, maxScroll: 0 };
                    var lastBridgeTime = 0;
                    
                    var currentChapterIndex = 0;
                    var chapterElements = [];
                    var chapterLengths = [];
                    var totalTextLength = 0;
                    var chapterElementBounds = []; // Stores [startIndex, endIndex] of allElements for each chapter

                    // --- Global Functions (Defined early for reliability) ---
                    window.readerNext = function() { 
                        if (!wrapper) return;
                        if (layoutCache.w <= 0) refreshLayoutCache();
                        var w = layoutCache.w;
                        if (w <= 0) return;
                        var target = (Math.floor((wrapper.scrollLeft + 10) / w) + 1) * w;
                        if (target > layoutCache.maxScroll + 10) {
                            if (currentChapterIndex < chapterElements.length - 1) {
                                switchChapter(currentChapterIndex + 1, 0);
                            }
                        } else {
                            wrapper.scrollLeft = target;
                        }
                    };
                    window.readerPrev = function() { 
                        if (!wrapper) return;
                        if (layoutCache.w <= 0) refreshLayoutCache();
                        var w = layoutCache.w;
                        if (w <= 0) return;
                        var target = Math.max(0, (Math.ceil((wrapper.scrollLeft - 10) / w) - 1) * w);
                        if (wrapper.scrollLeft <= 5) {
                            if (currentChapterIndex > 0) {
                                switchChapter(currentChapterIndex - 1, 'end');
                            }
                        } else {
                            wrapper.scrollLeft = target;
                        }
                    };

                    function switchChapter(idx, snapTo) {
                        if (idx < 0 || idx >= chapterElements.length) return;
                        currentChapterIndex = idx;
                        for (var i = 0; i < chapterElements.length; i++) {
                            var isCurrent = (i === idx);
                            chapterElements[i].style.display = isCurrent ? 'block' : 'none';
                            chapterElements[i].classList.toggle('active-chapter', isCurrent);
                        }
                        
                        // Force layout refresh and snap
                        setTimeout(function() {
                            refreshLayoutCache();
                            if (snapTo === 'skip') {
                                // Skip snapping scroll position to avoid overriding external jumps
                            } else if (snapTo === 'end') {
                                wrapper.scrollLeft = layoutCache.maxScroll;
                            } else if (typeof snapTo === 'number') {
                                wrapper.scrollLeft = snapTo;
                            } else {
                                wrapper.scrollLeft = 0;
                            }
                            findCurrentAnchor();
                            updateProgress();
                        }, 50);
                    }

                    window.readerRestore = function(s) {
                        try {
                            var parts = s.split('|');
                            var cIdx = parseInt(parts[0]);
                            var pct = parseFloat(parts[1]);
                            if (!isNaN(cIdx)) {
                                setTimeout(function() {
                                    switchChapter(cIdx, 0); // Temporary jump to chapter to trigger layout
                                    setTimeout(function() {
                                        if (layoutCache.maxScroll > 0) {
                                            wrapper.scrollLeft = pct * layoutCache.maxScroll;
                                        }
                                        findCurrentAnchor();
                                        updateProgress();
                                    }, 200);
                                }, 500);
                            }
                        } catch(e) {}
                    };

                    function refreshLayoutCache() {
                        if (!wrapper) return;
                        layoutCache.w = wrapper.clientWidth;
                        layoutCache.h = wrapper.clientHeight;
                        layoutCache.scrollWidth = wrapper.scrollWidth;
                        layoutCache.maxScroll = layoutCache.scrollWidth - layoutCache.w;
                        layoutCache.totalPages = Math.max(1, Math.ceil(layoutCache.scrollWidth / layoutCache.w));
                    }

                    function updateLayout(forcedWidth) {
                        try {
                             if (!wrapper || !textContainer) return;
                             var w = forcedWidth || wrapper.clientWidth;
                             var h = wrapper.clientHeight;
                             
                             if(w > 20) {
                                  // Scientific Optimization: Use viewport-relative widths to prevent "three-page-overlap"
                                  var maxReadingW = 960;
                                  var lateralPadding = (w > maxReadingW) ? (w - maxReadingW) / 2 : 60;
                                  
                                  var styleTag = document.getElementById('dynamic-layout-style');
                                  if (!styleTag) {
                                      styleTag = document.createElement('style');
                                      styleTag.id = 'dynamic-layout-style';
                                      document.head.appendChild(styleTag);
                                  }

                                  styleTag.textContent =
                                      '#reader-text { ' +
                                      '    box-sizing: border-box !important; ' +
                                      '    width: 100vw !important; ' +
                                      '    min-width: 100vw !important; ' +
                                      '    column-width: 100vw !important; ' +
                                      '    column-gap: 0px !important; ' +
                                      '    height: ' + h + 'px !important; ' +
                                      '    padding: 0 !important; ' +
                                      '    margin: 0 !important; ' +
                                      '} ' +
                                      '.page-content { ' +
                                      '    padding-left: ' + lateralPadding + 'px !important; ' +
                                      '    padding-right: ' + lateralPadding + 'px !important; ' +
                                      '    box-sizing: border-box !important; ' +
                                      '    min-height: 100%; ' +
                                      '}';

                                  // Update cache after layout changes
                                  setTimeout(refreshLayoutCache, 50);
                             }
                        } catch(e) {}
                    }

                    function findCurrentAnchor() {
                        if (layoutCache.w <= 0) refreshLayoutCache();
                        var w = layoutCache.w;
                        if (w <= 0) return;
                        
                        var wRect = wrapper.getBoundingClientRect();

                        // Limit search to current chapter bounds to avoid checking hidden elements (width 0)
                        var bounds = chapterElementBounds[currentChapterIndex];
                        if (!bounds) return;
                        var low = bounds[0];
                        var high = bounds[1];
                        var bestIndex = currentAnchorIndex;

                        // Fallback bestIndex to within current chapter if it was out of bounds
                        if (bestIndex < low || bestIndex > high) {
                            bestIndex = low;
                        }

                        // Binary Search for O(log N) instead of O(N)
                        while (low <= high) {
                            var mid = Math.floor((low + high) / 2);
                            var rect = allElements[mid].getBoundingClientRect();
                            var relLeft = rect.left - wRect.left;

                            if (relLeft < 0) {
                                low = mid + 1;
                            } else if (relLeft >= w - 1) {
                                high = mid - 1;
                            } else {
                                bestIndex = mid;
                                // Scan slightly backward to find the absolute first in this column
                                for (var j = mid - 1; j >= Math.max(bounds[0], mid - 20); j--) {
                                    var r2 = allElements[j].getBoundingClientRect();
                                    if (r2.left - wRect.left < 0) break;
                                    bestIndex = j;
                                }
                                break;
                            }
                        }
                        currentAnchorIndex = bestIndex;
                    }

                    function updateProgress() {
                        if (isResizing || !window.readerBridge || !window.readerBridge.sendProgressInfo) return;
                        
                        if (progressTimeout) clearTimeout(progressTimeout);
                        
                        progressTimeout = setTimeout(function() {
                            if (layoutCache.w <= 0) refreshLayoutCache();

                            findCurrentAnchor(); // Ensure anchor is up to date

                            // Per-chapter percentage
                            var scrollPct = layoutCache.maxScroll > 0 ? (wrapper.scrollLeft / layoutCache.maxScroll) : 0;

                            // Global percentage using character positions
                            var charIndex = 0;
                            for (var c = 0; c < currentChapterIndex; c++) {
                                charIndex += chapterLengths[c];
                            }

                            var anchorEl = allElements[currentAnchorIndex];
                            if (anchorEl && anchorEl.hasAttribute('data-char-offset')) {
                                charIndex += parseInt(anchorEl.getAttribute('data-char-offset'));
                            } else {
                                // Fallback if anchor lacks offset
                                var currentChapterLen = chapterLengths[currentChapterIndex] || 0;
                                charIndex += scrollPct * currentChapterLen;
                            }

                            var globalPct = totalTextLength > 0 ? Math.round((charIndex / totalTextLength) * 100) : 0;

                            // Bounds checks for first and last pages
                            if (currentChapterIndex === 0 && wrapper.scrollLeft <= 5) {
                                globalPct = 0;
                            } else if (currentChapterIndex === chapterElements.length - 1 && wrapper.scrollLeft >= layoutCache.maxScroll - 5) {
                                globalPct = 100;
                            } else {
                                if (globalPct > 100) globalPct = 100;
                                if (globalPct < 0) globalPct = 0;
                            }

                            window.readerBridge.sendProgressInfo(globalPct + '%');

                            // Save progress in new format: chapterIndex|scrollPct
                            if (window.isReadyToSave && window.readerBridge.saveProgress) {
                                 window.readerBridge.saveProgress(currentChapterIndex + '|' + scrollPct.toFixed(4));
                            }
                        }, 100);
                    }
                    
                    /* Requirement 12: Implementation of Progress Info Request */
                    window.requestProgressInfo = function() {
                        updateProgress();
                    };

                    function start() {
                        try {
                             wrapper = document.getElementById('reader-wrapper');
                             textContainer = document.getElementById('reader-text');
                             if (!textContainer || !wrapper) {
                                  setTimeout(start, 200); return;
                             }
                             
                             // 1. Populate Chapters & Elements
                             var rawChapters = textContainer.querySelectorAll('.chapter');
                             chapterElements = [];
                             chapterLengths = [];
                             totalTextLength = 0;
                             for(var k=0; k<rawChapters.length; k++) {
                                 chapterElements.push(rawChapters[k]);
                                 var len = rawChapters[k].textContent.length || 0;
                                 chapterLengths.push(len);
                                 totalTextLength += len;
                             }
                             
                             var rawNodes = textContainer.querySelectorAll('p, h1, h2, h3, h4, h5, h6, img, li, blockquote, .page-content div, .chapter div');
                             allElements = [];
                             for(var i=0; i<rawNodes.length; i++) { allElements.push(rawNodes[i]); }
                             
                             if (allElements.length === 0) {
                                 var allNodes = textContainer.querySelectorAll('*');
                                 var ignoredTags = {'STYLE':1, 'SCRIPT':1, 'META':1, 'HEAD':1, 'TITLE':1, 'NOSCRIPT':1};
                                 for(var j=0; j<allNodes.length; j++) {
                                     var el = allNodes[j];
                                     if (el.textContent.trim().length > 0 && !ignoredTags[el.tagName]) allElements.push(el);
                                 }
                             }

                             // Calculate character offset for each element
                             for (var i = 0; i < chapterElements.length; i++) {
                                 var chap = chapterElements[i];
                                 var walker = document.createTreeWalker(chap, NodeFilter.SHOW_TEXT, null, false);
                                 var offset = 0;
                                 var lastEl = null;

                                 var elementsInChapter = [];
                                 for (var j = 0; j < allElements.length; j++) {
                                     if (chap.contains(allElements[j])) {
                                         elementsInChapter.push(allElements[j]);
                                     }
                                 }

                                 var elIdx = 0;
                                 while (walker.nextNode()) {
                                     var node = walker.currentNode;
                                     if (node.parentNode.tagName === 'SCRIPT' || node.parentNode.tagName === 'STYLE') continue;

                                     // Find which element contains this text node
                                     var parentEl = node.parentElement;
                                     while (parentEl && parentEl !== chap && elementsInChapter.indexOf(parentEl) === -1) {
                                         parentEl = parentEl.parentElement;
                                     }

                                     if (parentEl && elementsInChapter.indexOf(parentEl) !== -1) {
                                         if (parentEl !== lastEl) {
                                             if (!parentEl.hasAttribute('data-char-offset')) {
                                                 parentEl.setAttribute('data-char-offset', offset);
                                             }
                                             lastEl = parentEl;
                                         }
                                     }

                                     offset += node.nodeValue.length;
                                 }
                                 chapterLengths[i] = offset; // override with accurate length
                             }

                             totalTextLength = 0;
                             for (var i = 0; i < chapterLengths.length; i++) {
                                 totalTextLength += chapterLengths[i];
                             }

                             // Calculate element bounds for each chapter
                             chapterElementBounds = [];
                             var currentChapIdx = 0;
                             var startIdx = 0;
                             for (var i = 0; i < allElements.length; i++) {
                                 var el = allElements[i];
                                 while (currentChapIdx < chapterElements.length && !chapterElements[currentChapIdx].contains(el)) {
                                     chapterElementBounds.push([startIdx, Math.max(0, i - 1)]);
                                     startIdx = i;
                                     currentChapIdx++;
                                 }
                             }
                             while (currentChapIdx < chapterElements.length) {
                                 chapterElementBounds.push([startIdx, Math.max(0, allElements.length - 1)]);
                                 startIdx = allElements.length;
                                 currentChapIdx++;
                             }

                             // Show first chapter by default if no restore happened yet
                             if (!window.isRestoring) switchChapter(0, 0);
                             
                             // 2. Attach Listeners
                             window.addEventListener('blur', function() { document.body.classList.add('focus-lost'); });
                             window.addEventListener('focus', function() { document.body.classList.remove('focus-lost'); });
                             if (!document.hasFocus()) document.body.classList.add('focus-lost');

                             wrapper.addEventListener('scroll', function() {
                                 if (isResizing) return;
                                 updateProgress(); 
                             });
                             
                             wrapper.addEventListener('wheel', function(e) {
                                  var now = Date.now();
                                  if (Math.abs(e.deltaX) > 10 || Math.abs(e.deltaY) > 10) {
                                      e.preventDefault();
                                      if (now - lastWheelTime > 200) {
                                          if (e.deltaY > 0 || e.deltaX > 0) window.readerNext();
                                          else if (e.deltaY < 0 || e.deltaX < 0) window.readerPrev();
                                          lastWheelTime = now;
                                      }
                                  }
                             }, { passive: false });

                             // Catch global wheel events in case mouse is not exactly over the wrapper
                             window.addEventListener('wheel', function(e) {
                                  if(e.target === wrapper || wrapper.contains(e.target)) return;
                                  var now = Date.now();
                                  if (Math.abs(e.deltaX) > 10 || Math.abs(e.deltaY) > 10) {
                                      e.preventDefault();
                                      if (now - lastWheelTime > 200) {
                                          if (e.deltaY > 0 || e.deltaX > 0) window.readerNext();
                                          else if (e.deltaY < 0 || e.deltaX < 0) window.readerPrev();
                                          lastWheelTime = now;
                                      }
                                  }
                             }, { passive: false });

                             if (typeof ResizeObserver !== 'undefined') {
                                 var observer = new ResizeObserver(function() {
                                     if (!wrapper || isResizing) return;
                                     isResizing = true; wrapper.classList.add('resizing');
                                     var oldAnchorIdx = currentAnchorIndex;
                                     updateLayout();
                                     if (allElements[oldAnchorIdx]) {
                                         var el = allElements[oldAnchorIdx];
                                         // Requirement 9: anchor first line
                                         if (typeof forceBreakBefore === 'function') {
                                             forceBreakBefore(el);
                                         }
                                         setTimeout(function() {
                                             if (!el || !wrapper) return;
                                             var rect = el.getBoundingClientRect();
                                             var wRect = wrapper.getBoundingClientRect();
                                             if (layoutCache.w <= 0) refreshLayoutCache();
                                             var targetL = Math.round((wrapper.scrollLeft + rect.left - wRect.left) / layoutCache.w) * layoutCache.w;
                                             wrapper.scrollLeft = targetL;
                                         }, 10);
                                     }

                                     if(resizeTimer) clearTimeout(resizeTimer);
                                     resizeTimer = setTimeout(function() {
                                         isResizing = false;
                                         if(wrapper) {
                                             wrapper.classList.remove('resizing');
                                             if (document.activeElement !== wrapper) wrapper.focus();
                                         }
                                         findCurrentAnchor();
                                         updateProgress();
                                     }, 100);
                                 });
                                 observer.observe(wrapper);
                             }

                             // 3. Initial Layout
                             updateLayout(); 
                             setTimeout(function() { 
                                 updateLayout(); findCurrentAnchor(); 

                                 // Requirement 6: Disable all links strictly
                                 var allLinks = document.querySelectorAll('a, [onclick]');
                                 for (var k = 0; k < allLinks.length; k++) {
                                     allLinks[k].onclick = function(e) { e.preventDefault(); e.stopPropagation(); return false; };
                                     if(allLinks[k].tagName === 'A') allLinks[k].removeAttribute("href");
                                 }

                                 document.addEventListener('click', function(e) {
                                     var a = e.target.closest('a');
                                     if(a) { e.preventDefault(); e.stopPropagation(); return false; }
                                 }, true);

                                 if(window.readerBridge && window.readerBridge.ready) window.readerBridge.ready();
                             }, 500);
                             wrapper.focus();
                        } catch(e) { console.error("INIT ERR: " + e.message); }
                    }
                    
                    if (document.readyState === 'complete') start(); else window.addEventListener('load', start);
                </script>
                <script>
                    window.addEventListener('keydown', function(e) {
                        if (document.activeElement && (document.activeElement.tagName === 'INPUT' || document.activeElement.tagName === 'TEXTAREA')) return;
                        var key = e.key.toLowerCase();
                        if (key === 'arrowleft' || key === 'a') { e.preventDefault(); e.stopPropagation(); window.readerPrev(); return false; }
                        else if (key === 'arrowright' || key === 'd') { e.preventDefault(); e.stopPropagation(); window.readerNext(); return false; }
                    }, true);

                    window.readerZoomIn = function() {
                        var root = document.documentElement;
                        var cur = parseInt(getComputedStyle(root).getPropertyValue('--font-size')) || 16;
                        var next = cur + 2;
                        root.style.setProperty('--font-size', next + 'px');
                        if (window.readerBridge && window.readerBridge.saveFontSize) window.readerBridge.saveFontSize(next.toString());
                        setTimeout(function() { updateLayout(); findCurrentAnchor(); }, 150);
                    };
                    window.readerZoomOut = function() {
                        var root = document.documentElement;
                        var cur = parseInt(getComputedStyle(root).getPropertyValue('--font-size')) || 16;
                        if(cur > 10) {
                            var next = cur - 2;
                            root.style.setProperty('--font-size', next + 'px');
                            if (window.readerBridge && window.readerBridge.saveFontSize) window.readerBridge.saveFontSize(next.toString());
                            setTimeout(function() { updateLayout(); findCurrentAnchor(); }, 150);
                        }
                    };

                    function forceBreakBefore(el) {
                         // Optimization: only clear tracked elements instead of querySelectorAll
                         while(activeForcedBreaks.length > 0) {
                             var p = activeForcedBreaks.pop();
                             if(p) { p.style.breakBefore = ''; p.classList.remove('forced-break'); }
                         }
                         if (el) {
                             var blockEl = el;
                             var inlineTags = {'SPAN':1, 'A':1, 'B':1, 'I':1, 'STRONG':1, 'EM':1, 'U':1, 'FONT':1, 'SUB':1, 'SUP':1, 'MARK':1};
                             while(blockEl && blockEl !== textContainer && inlineTags[blockEl.tagName]) {
                                 blockEl = blockEl.parentElement;
                             }
                             if(blockEl && blockEl !== textContainer) {
                                 blockEl.style.breakBefore = 'column';
                                 blockEl.classList.add('forced-break');
                                 activeForcedBreaks.push(blockEl);
                             }
                         }
                    }

                    window.scrollToId = function(id) {
                         var el = document.getElementById(id);
                         if (!el) {
                             try { el = document.querySelector('[id="' + CSS.escape(id) + '"]'); } catch(e) {}
                         }
                         if (!el) {
                             try { el = document.querySelector('[name="' + CSS.escape(id) + '"]'); } catch(e) {}
                         }
                         if(el) {
                             var ch = el.closest('.chapter');
                             var cIdx = chapterElements.indexOf(ch);
                             var delay = 0;
                             if (cIdx !== -1 && cIdx !== currentChapterIndex) {
                                 switchChapter(cIdx, "skip");
                                 delay = 150;
                             }
                             var jumpFn = function() {
                                 forceBreakBefore(el);
                                 requestAnimationFrame(function() {
                                     requestAnimationFrame(function() {
                                         if (!el || !wrapper) return;
                                         if (layoutCache.w <= 0) refreshLayoutCache();
                                         var targetL = 0;
                                         if (!el.classList.contains('chapter')) {
                                             var rect = el.getBoundingClientRect();
                                             var wRect = wrapper.getBoundingClientRect();
                                             var relLeft = rect.left - wRect.left;
                                             targetL = Math.round((wrapper.scrollLeft + relLeft) / layoutCache.w) * layoutCache.w;
                                         }
                                         wrapper.scrollLeft = targetL;
                                         setTimeout(function() { findCurrentAnchor(); updateProgress(); }, 150);
                                     });
                                 });
                             };
                             if (delay > 0) setTimeout(jumpFn, delay);
                             else jumpFn();
                         }
                    };
                    
                    window.jumpToMatch = function(id) {
                         var hl = document.querySelectorAll('.active-match');
                         for(var i=0; i<hl.length; i++) { hl[i].classList.remove('active-match'); }
                         var el = document.getElementById(id);
                         if(el) {
                             el.classList.add('active-match');
                             var ch = el.closest('.chapter');
                             var cIdx = chapterElements.indexOf(ch);
                             var delay = 0;
                             if (cIdx !== -1 && cIdx !== currentChapterIndex) {
                                 switchChapter(cIdx, "skip");
                                 delay = 150;
                             }
                             var jumpFn = function() {
                                 forceBreakBefore(el);
                                 requestAnimationFrame(function() {
                                     requestAnimationFrame(function() {
                                        if (!el || !wrapper) return;
                                        if (layoutCache.w <= 0) refreshLayoutCache();
                                        var targetL = 0;
                                        if (!el.classList.contains('chapter')) {
                                            var rect = el.getBoundingClientRect();
                                            var wRect = wrapper.getBoundingClientRect();
                                            var relLeft = rect.left - wRect.left;
                                            targetL = Math.round((wrapper.scrollLeft + relLeft) / layoutCache.w) * layoutCache.w;
                                        }
                                        wrapper.scrollLeft = targetL;
                                        setTimeout(function() { updateProgress(); }, 150);
                                     });
                                 });
                             };
                             if (delay > 0) setTimeout(jumpFn, delay);
                             else jumpFn();
                         }
                    };

                    window.performSearchFromNative = function(query) {
                        try {
                            query = (query || "").trim().toLowerCase();
                            var hl = document.querySelectorAll('.search-highlight');
                            for(var i=0; i<hl.length; i++) {
                                var span = hl[i]; var p = span.parentNode; 
                                if(p) {
                                    var txt = document.createTextNode(span.textContent);
                                    p.replaceChild(txt, span);
                                    var prev = txt.previousSibling;
                                    var next = txt.nextSibling;
                                    if(prev && prev.nodeType === 3) {
                                        prev.nodeValue += txt.nodeValue;
                                        p.removeChild(txt);
                                        txt = prev;
                                    }
                                    if(next && next.nodeType === 3) {
                                        txt.nodeValue += next.nodeValue;
                                        p.removeChild(next);
                                    }
                                }
                            }
                            searchMatches = [];
                            if (!query) { return; }

                            var walker = document.createTreeWalker(textContainer, NodeFilter.SHOW_TEXT, null, false);
                            var node; var textNodes = [];
                            while(node = walker.nextNode()) { 
                                if(node.parentNode.tagName !== 'SCRIPT' && node.parentNode.tagName !== 'STYLE') textNodes.push(node); 
                            }

                            var matchCount = 0;
                            for (var k = 0; k < textNodes.length; k++) {
                                var tNode = textNodes[k];
                                var text = tNode.nodeValue; var textL = text.toLowerCase();
                                var idx = textL.indexOf(query);
                                if (idx !== -1) {
                                    var span = document.createElement('span');
                                    span.className = 'search-highlight'; span.id = 'search-match-' + matchCount;
                                    span.textContent = text.substring(idx, idx + query.length);
                                    
                                    var snipStart = Math.max(0, idx - 40);
                                    var snipEnd = Math.min(text.length, idx + query.length + 40);
                                    var snippet = text.substring(snipStart, idx) + "<b>" + span.textContent + "</b>" + text.substring(idx + query.length, snipEnd);
                                    searchMatches.push({ id: span.id, text: (snipStart > 0 ? "... " : "") + snippet.replace(/\s+/g, ' ') + (snipEnd < text.length ? " ..." : "") });
                                    
                                    var range = document.createRange();
                                    range.setStart(tNode, idx); range.setEnd(tNode, idx + query.length);
                                    range.surroundContents(span);
                                    matchCount++; if (matchCount > 500) break;
                                }
                            }
                            // Do not remove search-active globally based on match count, rely on native dialog state instead
                            if(window.readerBridge && window.readerBridge.sendSearchResults) {
                                var res = "";
                                for(var m=0; m<searchMatches.length; m++) { res += searchMatches[m].id + "|||" + searchMatches[m].text + (m < searchMatches.length - 1 ? "|||" : ""); }
                                window.readerBridge.sendSearchResults(res || "NONE");
                            }
                        } catch(e) { console.error("Search error: ", e); }
                    };

                    window.setDialogActive = function(a) { document.body.classList.toggle('dialog-active', !!a); };
                    window.setSearchActive = function(a) { document.body.classList.toggle('search-active', !!a); };
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    data class EpubLoadResult(val html: String, val toc: List<TocItem>)

    fun loadEpub(file: File, isDarcula: Boolean, fontSize: Int, theme: String? = null, fontFamily: String? = null, bridgeJs: String? = null): EpubLoadResult {
        val epubReader = EpubReader()
        val book = epubReader.readEpub(file.inputStream())
        val imageMap = mutableMapOf<String, String>()

        for (res in book.resources.all) {
            val href = res.href
            val mime = getMimeType(href)
            if (mime != null && mime.startsWith("image/")) {
                val filename = href.substringAfterLast('/')
                try {
                    val b64 = Base64.getEncoder().encodeToString(res.data).replace(Regex("\\s"), "")
                    val dataUri = "data:$mime;base64,$b64"
                    imageMap[href.lowercase(Locale.getDefault())] = dataUri
                    imageMap[filename.lowercase(Locale.getDefault())] = dataUri
                } catch (_: Exception) { }
            }
        }

        val colors = if (isDarcula) ThemeColors("#2b2d30", "#a9b7c6", "rgba(43, 45, 48, 0.9)", "#3c3f41", "#4c5052")
        else ThemeColors("#ffffff", "#333333", "rgba(255, 255, 255, 0.9)", "#e0e0e0", "#e6e6e6")

        val sb = StringBuilder()
        val tocItems = extractTocItems(book)

        for ((chapterIndex, spineRef) in book.spine.spineReferences.withIndex()) {
            val res = spineRef.resource
            val rawHtml = String(res.data, Charset.forName(res.inputEncoding ?: "UTF-8"))
            val doc = Jsoup.parse(rawHtml)
            val allImages = doc.select("img, image")
            for (img in allImages) {
                val isSvgImage = img.tagName().equals("image", ignoreCase = true)
                val attrName = if (isSvgImage) "xlink:href" else "src"
                var src = img.attr(attrName)
                if (src.isEmpty() && isSvgImage) src = img.attr("href")
                val rawKey = src.substringAfterLast('/').lowercase(Locale.getDefault()).replace("%20", " ")
                val fullKey = src.lowercase(Locale.getDefault())
                val finalData = imageMap[rawKey] ?: imageMap[fullKey]
                if (finalData != null) {
                    img.attr(attrName, finalData)
                    if (isSvgImage) img.attr("href", finalData)
                }
            }
            sb.append("<div id='spine-$chapterIndex' class='chapter'><div class='page-content'>${doc.body().html()}</div></div>")
        }
        val mappedToc = mapTocToSpineIds(tocItems, book)
        val html = getAppHtml(sb.toString(), colors, fontSize, theme, fontFamily, bridgeJs)
        return EpubLoadResult(html, mappedToc)
    }

    fun getWelcomeHtml(isDarcula: Boolean, fontSize: Int, theme: String? = null, fontFamily: String? = null, bridgeJs: String? = null): String {
        val colors = if (isDarcula) ThemeColors("#2b2d30", "#aaa", "rgba(43, 45, 48, 0.9)", "#4e5254", "#4c5052")
        else ThemeColors("#fff", "#333", "rgba(255, 255, 255, 0.9)", "#ddd", "#eee")
        val welcomeContent = """
            <div style='display:flex;flex-direction:column;height:100%;justify-content:center;align-items:center;opacity:0.7;text-align:center;padding:40px;gap:25px;' onclick="if(window.readerBridge && typeof window.readerBridge.openFile === 'function') window.readerBridge.openFile();">
                <svg viewBox="0 0 24 24" style="width: 64px; height: 64px; fill: none; stroke: currentColor; stroke-width: 1.2px; cursor: pointer; margin-bottom: 10px;">
                    <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"></path>
                    <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"></path>
                </svg>
                <div style="cursor: pointer;">
                    <div style="font-size: 20px; font-weight: 500; margin-bottom: 15px;">Reader Master v1.5.1 (Stable)</div>
                    
                    <div style="font-size: 14px; line-height: 1.8; margin-bottom: 20px;">
                        <div style="font-weight: bold; color: var(--text);">New in this version:</div>
                        <div>• Optimized default font size for better readability</div>
                        <div>• Fixed jumping to chapters and search results</div>
                    </div>
                    
                    <div style="font-size: 14px; line-height: 1.8; margin-bottom: 20px; opacity: 0.9;">
                        <div style="font-weight: bold; color: var(--text);">新版本特性：</div>
                        <div>• 优化了默认字体大小，提升阅读舒适度</div>
                        <div>• 修复了点击目录章节或搜索结果时跳转位置不准确的问题</div>
                    </div>

                    <div style="font-size: 14px; line-height: 1.8; opacity: 0.8;">
                        <div style="font-weight: bold; color: var(--text);">新機能：</div>
                        <div>• 読みやすさを向上させるためにデフォルトのフォントサイズを最適化しました</div>
                        <div>• チャプターと検索結果へのジャンプを修正しました</div>
                    </div>
                    
                    <div style="font-size: 13px; margin-top: 30px; border: 1px solid var(--border); padding: 8px 16px; border-radius: 20px;">
                        Click anywhere to open an EPUB / 点击此处打开书籍 / クリックして開く
                    </div>
                </div>
            </div>
        """.trimIndent()
        return getAppHtml(welcomeContent, colors, fontSize, theme, fontFamily, bridgeJs)
    }

    private fun getMimeType(href: String): String? {
        val name = href.lowercase(Locale.getDefault())
        return when {
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".gif") -> "image/gif"
            name.endsWith(".svg") -> "image/svg+xml"
            else -> null
        }
    }

    private fun extractTocItems(book: Book): List<TocItem> {
        val items = mutableListOf<TocItem>()
        fun recurse(refs: List<TOCReference>) {
            for (ref in refs) {
                if (!ref.title.isNullOrBlank()) items.add(TocItem(ref.completeHref, ref.title))
                if (ref.children != null) recurse(ref.children)
            }
        }
        recurse(book.tableOfContents.tocReferences)
        return items.distinctBy { it.title }
    }

    private fun mapTocToSpineIds(tocItems: List<TocItem>, book: Book): List<TocItem> {
        val spineRefs = book.spine.spineReferences
        return tocItems.map { item ->
            val href = item.htmlId.substringBefore("#")
            val fragment = item.htmlId.substringAfter("#", "")
            val idx = spineRefs.indexOfFirst { it.resource.href == href }
            if (idx != -1) {
                // If there's a specific fragment, use it as the ID. Otherwise use the spine-X ID.
                if (fragment.isNotEmpty()) TocItem(fragment, item.title)
                else TocItem("spine-$idx", item.title)
            } else item
        }
    }

    data class ThemeColors(val bg: String, val text: String, val sidebarBg: String, val border: String, val hover: String)
    data class TocItem(val htmlId: String, val title: String)
}