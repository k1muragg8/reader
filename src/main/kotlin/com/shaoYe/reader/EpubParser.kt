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
        val cssFontFamily = if (actualFontFamily == "serif") "Palatino, \"Palatino Linotype\", \"Book Antiqua\", Georgia, serif" else "-apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif"

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
                    }

                    /* Theme: White */
                    :root[data-theme="white"] {
                        --bg: #ffffff; --text: #000000;
                        --sidebar-bg: rgba(255, 255, 255, 0.9);
                        --border: #e0e0e0; --icon-stroke: #000000;
                        --hover-bg: rgba(0,0,0,0.05);
                    }

                    /* Theme: Sepia */
                    :root[data-theme="sepia"] {
                        --bg: #fbf0d9; --text: #5f4b32;
                        --sidebar-bg: rgba(251, 240, 217, 0.95);
                        --border: #e8dcc4; --icon-stroke: #5f4b32;
                        --hover-bg: rgba(95, 75, 50, 0.08);
                    }

                    /* Theme: Dark */
                    :root[data-theme="dark"] {
                        --bg: #1e1e1e; --text: #d4d4d4;
                        --sidebar-bg: rgba(30, 30, 30, 0.9);
                        --border: #333333; --icon-stroke: #d4d4d4;
                        --hover-bg: rgba(255,255,255,0.1);
                    }
                    
                    body { 
                        font-family: var(--font-family);
                        font-size: var(--font-size) !important;
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
                    
                    #content { width: 100%; height: 100%; display: flex; flex-direction: column; overflow: hidden; position: relative; }
                    #reader-wrapper { flex: 1; width: 100%; height: 100%; overflow-x: scroll; overflow-y: hidden; outline: none; transition: opacity 0.2s; will-change: transform; }
                    #reader-wrapper.resizing { cursor: col-resize; opacity: 0.8; }
                    #reader-wrapper.resizing * { transition: none !important; animation: none !important; }
                    #reader-text { height: 100%; padding: 5px 60px; column-fill: auto; position: relative; }
                    
                    .chapter { break-before: column; }
                    .page-content { padding: 0; margin: 0; width: 100%; box-sizing: border-box; }
                    h1, h2, h3, h4, h5, h6 { break-inside: avoid; break-after: avoid; font-weight: bold; line-height: 1.2; }
                    h1, h2, h3, h4, h5, h6 { line-height: 1.2; margin: 0 !important; padding: 0 !important; font-weight: 600; break-after: avoid; }
                    h1 { font-size: 1.1em !important; }
                    h2 { font-size: 1.05em !important; }
                    h3, h4, h5, h6 { font-size: 1.0em !important; }
                    
                    p { margin: 0 !important; padding: 5px 0 !important; line-height: 1.6; text-align: justify; }
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
                            wrapper.scrollTo({ left: target, behavior: 'instant' });
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
                            wrapper.scrollTo({ left: target, behavior: 'instant' });
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
                            if (snapTo === 'end') {
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
                                 textContainer.style.width = 'auto';
                                 textContainer.style.minWidth = '100vw'; 
                                 textContainer.style.columnWidth = w + 'px';
                                 textContainer.style.columnGap = '0px';
                                 textContainer.style.height = h + 'px';
                                 textContainer.style.setProperty('padding', '0px', 'important');
                                 textContainer.style.setProperty('margin', '0px', 'important');
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
                        var low = 0, high = allElements.length - 1;
                        var bestIndex = currentAnchorIndex;

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
                                for (var j = mid - 1; j >= Math.max(0, mid - 20); j--) {
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
                        var now = Date.now();
                        if (now - lastBridgeTime < 100) return;
                        lastBridgeTime = now;

                        if (layoutCache.w <= 0) refreshLayoutCache();
                        var cur = Math.ceil((wrapper.scrollLeft + 1) / layoutCache.w);
                        
                        // Per-chapter percentage
                        var scrollPct = layoutCache.maxScroll > 0 ? (wrapper.scrollLeft / layoutCache.maxScroll) : 0;
                        
                        // Global percentage approximation
                        var globalPct = Math.round(((currentChapterIndex + scrollPct) / chapterElements.length) * 100);
                        
                        window.readerBridge.sendProgressInfo((currentChapterIndex + 1) + '-' + cur + ' / ' + chapterElements.length + ' (' + globalPct + '%)');
                        
                        // Save progress in new format: chapterIndex|scrollPct
                        if (window.isReadyToSave && window.readerBridge.saveProgress) {
                             window.readerBridge.saveProgress(currentChapterIndex + '|' + scrollPct.toFixed(4));
                        }
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
                             for(var k=0; k<rawChapters.length; k++) { chapterElements.push(rawChapters[k]); }
                             
                             var rawNodes = textContainer.querySelectorAll('p, h1, h2, h3, h4, h5, h6, img, li, blockquote, .page-content div, .chapter div');
                             allElements = [];
                             for(var i=0; i<rawNodes.length; i++) { allElements.push(rawNodes[i]); }
                             
                             // Show first chapter by default if no restore happened yet
                             if (!window.isRestoring) switchChapter(0, 0);
                             
                             if (allElements.length === 0) {
                                 var allNodes = textContainer.querySelectorAll('*');
                                 var ignoredTags = {'STYLE':1, 'SCRIPT':1, 'META':1, 'HEAD':1, 'TITLE':1, 'NOSCRIPT':1};
                                 for(var j=0; j<allNodes.length; j++) {
                                     var el = allNodes[j];
                                     if (el.textContent.trim().length > 0 && !ignoredTags[el.tagName]) allElements.push(el);
                                 }
                             }
                             
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
                         if(el) {
                             var ch = el.closest('.chapter');
                             var cIdx = chapterElements.indexOf(ch);
                             if (cIdx !== -1 && cIdx !== currentChapterIndex) {
                                 switchChapter(cIdx, 0);
                             }
                             forceBreakBefore(el);
                             requestAnimationFrame(function() {
                                 requestAnimationFrame(function() {
                                     if (!el || !wrapper) return;
                                     var rect = el.getBoundingClientRect();
                                     var wRect = wrapper.getBoundingClientRect();
                                     if (layoutCache.w <= 0) refreshLayoutCache();
                                     var targetL = Math.round((wrapper.scrollLeft + rect.left - wRect.left) / layoutCache.w) * layoutCache.w;
                                     wrapper.scrollLeft = targetL;
                                     setTimeout(function() { findCurrentAnchor(); updateProgress(); }, 150);
                                 });
                             });
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
                             if (cIdx !== -1 && cIdx !== currentChapterIndex) {
                                 switchChapter(cIdx, 0);
                             }
                             forceBreakBefore(el);
                             requestAnimationFrame(function() {
                                 requestAnimationFrame(function() {
                                    if (!el || !wrapper) return;
                                    var rect = el.getBoundingClientRect();
                                    var wRect = wrapper.getBoundingClientRect();
                                    if (layoutCache.w <= 0) refreshLayoutCache();
                                    var targetL = Math.round((wrapper.scrollLeft + rect.left - wRect.left) / layoutCache.w) * layoutCache.w;
                                    wrapper.scrollLeft = targetL;
                                    setTimeout(function() { updateProgress(); }, 150);
                                 });
                             });
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
                                    if(txt.previousSibling && txt.previousSibling.nodeType === 3) {
                                        txt.previousSibling.nodeValue += txt.nodeValue;
                                        p.removeChild(txt);
                                        txt = txt.previousSibling;
                                    }
                                    if(txt.nextSibling && txt.nextSibling.nodeType === 3) {
                                        txt.nodeValue += txt.nextSibling.nodeValue;
                                        p.removeChild(txt.nextSibling);
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
                        } catch(e) { }
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
                    <div style="font-size: 20px; font-weight: 500; margin-bottom: 15px;">Reader Master v1.4.5 (Stable)</div>
                    
                    <div style="font-size: 14px; line-height: 1.8; margin-bottom: 20px;">
                        <div style="font-weight: bold; color: var(--text);">New in this version:</div>
                        <div>• Fixed jumping to chapters and search results</div>
                        <div>• Fixed font size adjustment setting</div>
                    </div>
                    
                    <div style="font-size: 14px; line-height: 1.8; margin-bottom: 20px; opacity: 0.9;">
                        <div style="font-weight: bold; color: var(--text);">新版本特性：</div>
                        <div>• 修复了点击目录章节或搜索结果时跳转位置不准确的问题</div>
                        <div>• 修复了设置中调整字体大小失效的问题</div>
                    </div>

                    <div style="font-size: 14px; line-height: 1.8; opacity: 0.8;">
                        <div style="font-weight: bold; color: var(--text);">新機能：</div>
                        <div>• チャプターと検索結果へのジャンプを修正しました</div>
                        <div>• フォントサイズ調整の設定を修正しました</div>
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