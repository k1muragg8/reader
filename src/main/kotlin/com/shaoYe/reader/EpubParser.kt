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

    private fun getAppHtml(contentHtml: String, colors: ThemeColors, fontSize: Int, theme: String?, fontFamily: String?): String {
        val actualTheme = theme ?: "white"
        val actualFontFamily = fontFamily ?: "sans"
        val cssFontFamily = if (actualFontFamily == "serif") "Palatino, \"Palatino Linotype\", \"Book Antiqua\", Georgia, serif" else "-apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif"

        return """
            <!DOCTYPE html>
            <html lang='en' data-theme='$actualTheme'>
            <head>
                <meta charset='UTF-8'>
                <meta http-equiv="Content-Security-Policy" content="default-src 'self' 'unsafe-inline' 'unsafe-eval' data:;">
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
                        scroll-snap-type: none !important;
                    }

                    /* Removing #toolbar-trigger as we use body hover now */

                    #toolbar {
                        position: fixed; top: 0; left: 0; right: 0; height: 36px;
                        background: var(--sidebar-bg); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px);
                        display: grid; grid-template-columns: 1fr auto 1fr;
                        align-items: center; padding: 0 8px; z-index: 1002; user-select: none;
                        transform: translateY(-100%); transition: transform 0.3s cubic-bezier(0.19, 1, 0.22, 1);
                        border-bottom: 0.5px solid var(--border);
                    }
                    /* Show toolbar when body has 'hovering' class or when settings are open */
                    body.hovering #toolbar, body.settings-open #toolbar {
                        /* We remove the hover toolbar entirely since actions are now in the native IDE Title Bar */
                        /* transform: translateY(0); */
                    }
                    #toolbar { display: none !important; }
                    
                    /* Sidebar z-index adjustment if needed, but 9999 is fine */

                    .toolbar-group { display: flex; align-items: center; gap: 4px; }
                    .toolbar-group:nth-child(1) { justify-self: start; }
                    .toolbar-group:nth-child(2) { justify-self: center; }
                    .toolbar-group:nth-child(3) { justify-self: end; }

                    .icon-btn {
                        width: 24px; height: 24px; background: transparent; border: none;
                        padding: 4px; cursor: pointer; display: flex; align-items: center;
                        justify-content: center; border-radius: 4px; transition: all 0.2s ease; opacity: 0.8;
                    }
                    .icon-btn:hover { opacity: 1; background: var(--hover-bg); }
                    .icon-btn:active { transform: scale(0.95); }

                    .feather { width: 16px; height: 16px; fill: none; stroke: var(--icon-stroke); stroke-width: 1.5px; }

                    /* 极致紧贴边框，由外层容器强制给出顶部 4px 和底部 4px 留白 */
                    #content { position: absolute; top: 4px; bottom: 4px; left: 0; right: 0; overflow: hidden; }

                    #reader-wrapper {
                        width: 100%; height: 100%; overflow-x: hidden; overflow-y: hidden;
                        scroll-behavior: smooth; outline: none;
                        user-select: none; /* Prevent accidental text drag when trying to pan */
                    }
                    
                    /* 去掉内层的高度影响，保证文字直接顶满整个内部高度 */
                    #reader-text {
                        /* By setting height to 100% and removing bottom padding, we align with the 4px absolute constraint of the #content wrapper */
                        height: 100%; width: 100%; column-fill: auto;
                        padding-top: 0; padding-bottom: 0; box-sizing: border-box;
                    }
                    
                    .chapter { break-before: column; }
                    .page-content { padding: 0; margin: 0; width: 100%; box-sizing: border-box; }
                    h1, h2, h3, h4, h5, h6 { break-inside: avoid; break-after: avoid; }
                    p { line-height: 1.6; margin-top: 0; margin-bottom: 0; text-indent: 1.5em; text-align: justify; font-size: var(--font-size); letter-spacing: 0.02em; }
                    img { max-width: 100%; max-height: 80vh; height: auto; display: block; margin: 20px auto; border-radius: 8px; break-inside: avoid; }
                    
                    ::-webkit-scrollbar { display: none !important; }
                    .search-highlight { background-color: #ffeb3b; color: #000; border-radius: 2px; box-shadow: 0 0 2px rgba(0,0,0,0.2); }
                    .search-match { font-weight: bold; color: var(--icon-stroke); background: rgba(255, 235, 59, 0.3); }

                </style>
            </head>
            <body>
                <div id="content">
                    <div id="reader-wrapper" tabindex="0">
                        <div id="reader-text">$contentHtml</div>
                    </div>
                </div>
                
                <script>
                    const wrapper = document.getElementById('reader-wrapper');
                    const textContainer = document.getElementById('reader-text');
                    const pageInfo = document.getElementById('page-info');
                    const sidebar = document.getElementById('sidebar');
                    const backdrop = document.getElementById('sidebar-backdrop');
                    
                    // 状态锁
                    let isResizing = false;
                    let resizeTimer = null;
                    let saveTimeout = null;
                    let progressTimeout = null;
                    
                    // 影子追踪器：记录当前视野中“最关键”的那个元素索引
                    let currentAnchorIndex = 0;
                    let currentAnchorAbsoluteLeft = 0;
                    let allElements = [];

                    const settingsPopover = document.getElementById('settings-popover');
                    let wheelTimeout = null;
                    let lastWheelTime = 0;

                    window.onload = () => { 
                         // 缓存所有可能的锚点（包含列表项等）
                         allElements = Array.from(textContainer.querySelectorAll('p, h1, h2, h3, img, li, blockquote'));
                         updateLayout(); 
                         // 修复：初次加载时可能因为容器尺寸未就绪导致变形，延时再排版一次
                         setTimeout(updateLayout, 100);
                         
                         wrapper.focus();
                         // Bind Events
                         document.getElementById('btn-chapters').addEventListener('click', toggleSidebar);
                         document.getElementById('btn-close-sidebar').addEventListener('click', toggleSidebar);
                         backdrop.addEventListener('click', toggleSidebar);
                         document.getElementById('btn-open').addEventListener('click', () => {
                             if(window.readerBridge && typeof window.readerBridge.openFile === 'function') {
                                 window.readerBridge.openFile();
                             } else {
                                 console.log("readerBridge or openFile is not available yet");
                             }
                         });
                         
                         document.getElementById('btn-settings').addEventListener('click', (e) => {
                             e.stopPropagation();
                             if (window.toggleSettings) window.toggleSettings();
                         });

                         document.addEventListener('click', (e) => {
                             if (!settingsPopover.contains(e.target) && !document.getElementById('btn-settings').contains(e.target)) {
                                 settingsPopover.classList.remove('open');
                                 document.body.classList.remove('settings-open');
                             }
                         });

                         // Theme Buttons
                         document.querySelectorAll('.theme-btn').forEach(btn => {
                             btn.addEventListener('click', () => {
                                 const theme = btn.dataset.value;
                                 document.documentElement.setAttribute('data-theme', theme);
                                 if (window.readerBridge) window.readerBridge.saveTheme(theme);
                             });
                         });

                         // Font Family Buttons
                         document.querySelectorAll('.font-btn').forEach(btn => {
                             btn.addEventListener('click', () => {
                                 document.querySelectorAll('.font-btn').forEach(b => b.classList.remove('active'));
                                 btn.classList.add('active');
                                 const family = btn.dataset.value;
                                 if (family === 'serif') {
                                     document.documentElement.style.setProperty('--font-family', 'Palatino, "Palatino Linotype", "Book Antiqua", Georgia, serif');
                                 } else {
                                     document.documentElement.style.setProperty('--font-family', '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif');
                                 }
                                 if (window.readerBridge) window.readerBridge.saveFontFamily(family);
                                 setTimeout(findCurrentAnchor, 100);
                             });
                         });

                         // Zoom Controls from settings
                         document.getElementById('btn-zoom-in').addEventListener('click', window.readerZoomIn);
                         document.getElementById('btn-zoom-out').addEventListener('click', window.readerZoomOut);

                         // Window Hover Logic for Toolbar
                         document.documentElement.addEventListener('mouseenter', () => document.body.classList.add('hovering'));
                         document.documentElement.addEventListener('mouseleave', () => document.body.classList.remove('hovering'));
                    };

                    function updateLayout(width) {
                        const w = width || wrapper.clientWidth;
                        if(w > 0) {
                            textContainer.style.width = 'auto'; 
                            // Set padding inside textContainer
                            textContainer.style.paddingLeft = '4px';
                            textContainer.style.paddingRight = '4px';
                            // The actual usable width for a column is w minus the padding (4*2 = 8)
                            const usableWidth = w - 8;
                            textContainer.style.columnWidth = Math.max(usableWidth, 20) + 'px';
                            textContainer.style.columnGap = '8px';
                        }
                    }

                    // --- 1. 平时：精准记录“谁是主角” ---
                    wrapper.addEventListener('scroll', () => {
                        if (isResizing) return; // 施工期间不记录
                        updateProgress(); 
                        if(saveTimeout) clearTimeout(saveTimeout);
                        saveTimeout = setTimeout(() => {
                            findCurrentAnchor(); // 记录锚点
                            saveToBridge();      // 保存进度
                        }, 200);
                    });

                    function findCurrentAnchor() {
                        let bestIndex = 0;
                        let minDistance = Infinity;
                        for (let i = 0; i < allElements.length; i++) {
                            const el = allElements[i];
                            const rect = el.getBoundingClientRect();
                            if (rect.left >= 0 && rect.left < minDistance) {
                                minDistance = rect.left;
                                bestIndex = i;
                            }
                        }
                        currentAnchorIndex = bestIndex;
                    }

                    // --- 2. 战时：无感冻结重排 ---
                    const observer = new ResizeObserver(entries => {
                        for(let entry of entries) {
                            const width = entry.contentRect.width;
                            if(width <= 0) continue;

                            // A. 刚开始拖动：打麻醉
                            if (!isResizing) {
                                isResizing = true;
                                wrapper.classList.add('resizing'); // 杀掉动画和吸附

                                const anchorEl = allElements[currentAnchorIndex];
                                if (anchorEl) {
                                    const rect = anchorEl.getBoundingClientRect();
                                    currentAnchorAbsoluteLeft = wrapper.scrollLeft + rect.left;
                                }
                            }
                            
                            // B. 拖动中：实时计算但不乱跳
                            if(resizeTimer) clearTimeout(resizeTimer);
                            
                            // 更新布局列宽
                            updateLayout(width);

                            // 强行纠偏：找到原来的主角在新舞台的位置
                            if (allElements[currentAnchorIndex]) {
                                const newWidth = wrapper.clientWidth;
                                const targetLeft = Math.floor(currentAnchorAbsoluteLeft / (newWidth || 1)) * newWidth;
                                wrapper.scrollTo({left: targetLeft, behavior: 'instant'});
                            }

                            // C. 拖动结束：200ms后解除麻醉
                            resizeTimer = setTimeout(() => {
                                isResizing = false;
                                wrapper.classList.remove('resizing'); // 恢复平滑和吸附
                                updateProgress();
                            }, 200);
                        }
                    });
                    observer.observe(wrapper);

                    window.isReadyToSave = false;

                    function saveToBridge() {
                        if(!window.isReadyToSave) return;
                        const w = wrapper.clientWidth;
                        if(w > 0 && window.readerBridge) {
                            const maxScroll = wrapper.scrollWidth - w;
                            const pct = maxScroll > 0 ? (wrapper.scrollLeft / maxScroll) : 0;
                            window.readerBridge.saveProgress(pct.toString());
                        }
                    }

                    // --- Navigation ---
                    function navNext() { 
                        const w = wrapper.clientWidth;
                        const idx = Math.ceil((wrapper.scrollLeft + 1) / w);
                        wrapper.scrollTo({ left: idx * w, behavior: 'smooth' });
                    }
                    function navPrev() { 
                        const w = wrapper.clientWidth;
                        const idx = Math.ceil((wrapper.scrollLeft + 1) / w);
                        wrapper.scrollTo({ left: (idx - 2) * w, behavior: 'smooth' });
                    }

                    function toggleSidebar() { sidebar.classList.toggle('open'); }
                    window.toggleSidebar = toggleSidebar;
                    function scrollToId(id) {
                         const el = document.getElementById(id);
                         if(el) { 
                             el.scrollIntoView(); 
                             if(sidebar.classList.contains('open')) toggleSidebar(); 
                         }
                    }
                    
                    function getProgressString() {
                        const w = wrapper.clientWidth;
                        const scrollW = wrapper.scrollWidth;
                        if(w > 0) {
                             const current = Math.ceil((wrapper.scrollLeft + 1) / w);
                             const total = Math.ceil(scrollW / w) || 1;
                             const maxScroll = scrollW - w;
                             const pct = maxScroll > 0 ? Math.round((wrapper.scrollLeft / maxScroll) * 100) : 0;
                             return current + ' / ' + total + ' (' + pct + '%)';
                        }
                        return "0 / 0 (0%)";
                    }

                    function updateProgress() {
                        if (isResizing) return;
                        if (progressTimeout) clearTimeout(progressTimeout);
                        progressTimeout = setTimeout(() => {
                             const text = getProgressString();
                             if(window.readerBridge && window.readerBridge.sendProgressInfo) {
                                 window.readerBridge.sendProgressInfo(text);
                             }
                        }, 50);
                    }

                    window.requestProgressInfo = function() {
                         const text = getProgressString();
                         if(window.readerBridge && window.readerBridge.sendProgressInfo) {
                             window.readerBridge.sendProgressInfo(text);
                         }
                    };
                    
                    window.readerRestore = function(s) {
                        const pct = parseFloat(s);
                        if(!isNaN(pct)) {
                            setTimeout(() => {
                                const maxScroll = wrapper.scrollWidth - wrapper.clientWidth;
                                wrapper.scrollTo({ left: pct * maxScroll, behavior: 'instant' });
                                setTimeout(findCurrentAnchor, 200);
                            }, 300);
                        }
                    };

                     document.addEventListener('keydown', function(e) {
                          // Prevent A/D/Arrows when typing in inputs
                          if (document.activeElement &&
                             (document.activeElement.tagName === 'INPUT' || document.activeElement.tagName === 'TEXTAREA' || document.activeElement.isContentEditable)) {
                              return; // do not trigger shortcuts if typing
                          }

                          const k = e.key;
                          const lowerK = k.toLowerCase();
                          
                          // Map A/D and Arrow Keys to Pagination
                          if (lowerK === 'a' || k === 'ArrowLeft') {
                              e.preventDefault();
                              e.stopPropagation();
                              navPrev();
                          } else if (lowerK === 'd' || k === 'ArrowRight') {
                              e.preventDefault();
                              e.stopPropagation();
                              navNext();
                          }
                     });
                    
                    // Wheel Scrolling (Strictly translate vertical scroll wheel to page turns)
                    wrapper.addEventListener('wheel', (e) => {
                         // Prevent native scrolling completely for strict pagination control
                         e.preventDefault();

                         const now = Date.now();
                         if (now - lastWheelTime > 300) { // Debounce threshold
                             // Prefer deltaY (mouse wheel), fallback to deltaX (trackpad swipe)
                             if (e.deltaY > 0 || e.deltaX > 0) {
                                 navNext();
                             } else if (e.deltaY < 0 || e.deltaX < 0) {
                                 navPrev();
                             }
                             lastWheelTime = now;
                         }
                    }, { passive: false });

                    window.readerNext = navNext; window.readerPrev = navPrev;

                    window.readerZoomIn = function() {
                        const root = document.documentElement;
                        const currentSize = parseInt(getComputedStyle(root).getPropertyValue('--font-size')) || 16;
                        const newSize = currentSize + 2;
                        root.style.setProperty('--font-size', newSize + 'px');
                        if (window.readerBridge) window.readerBridge.saveFontSize(newSize.toString());
                        setTimeout(() => { updateLayout(); findCurrentAnchor(); }, 100);
                    };

                    window.readerZoomOut = function() {
                        const root = document.documentElement;
                        const currentSize = parseInt(getComputedStyle(root).getPropertyValue('--font-size')) || 16;
                        if(currentSize > 10) {
                            const newSize = currentSize - 2;
                            root.style.setProperty('--font-size', newSize + 'px');
                            if (window.readerBridge) window.readerBridge.saveFontSize(newSize.toString());
                            setTimeout(() => { updateLayout(); findCurrentAnchor(); }, 100);
                        }
                    };
                    // --- Search Logic ---
                    const searchSidebar = document.getElementById('search-sidebar');
                    const searchInput = document.getElementById('search-input');
                    const searchResults = document.getElementById('search-results');
                    let searchMatches = [];

                    function toggleSearchSidebar() {
                         searchSidebar.classList.toggle('open');
                         if(searchSidebar.classList.contains('open')) {
                             setTimeout(() => searchInput.focus(), 100);
                             // If mobile/narrow, close chapter sidebar
                             sidebar.classList.remove('open');
                         }
                    }
                    window.toggleSearchSidebar = toggleSearchSidebar;

                    window.toggleSettings = function() {
                         settingsPopover.classList.toggle('open');
                         if (settingsPopover.classList.contains('open')) {
                             document.body.classList.add('settings-open');
                         } else {
                             document.body.classList.remove('settings-open');
                         }
                    };

                    // Bind Search Events
                    document.getElementById('btn-search').addEventListener('click', toggleSearchSidebar);
                    document.getElementById('btn-close-search').addEventListener('click', toggleSearchSidebar);
                    searchInput.addEventListener('keydown', (e) => { if(e.key === 'Enter') performSearch(); });

                    function clearHighlights() {
                        if (!textContainer) return;
                        const highlights = textContainer.querySelectorAll('.search-highlight');
                        highlights.forEach(span => {
                            const parent = span.parentNode;
                            if (parent) {
                                parent.replaceChild(document.createTextNode(span.textContent), span);
                            }
                        });
                        textContainer.normalize();
                    }
                    window.clearHighlights = clearHighlights;

                    window.performSearchFromNative = function(query) {
                        let finalResult = "NONE";
                        try {
                            query = (query || "").trim();
                            if (!query) {
                                clearHighlights();
                                if (window.readerBridge && window.readerBridge.sendSearchResults) {
                                    window.readerBridge.sendSearchResults("");
                                }
                                return;
                            }

                            clearHighlights();
                            searchMatches = [];
                            
                            setTimeout(() => {
                                try {
                                    const filter = (window.NodeFilter && window.NodeFilter.SHOW_TEXT) || 4;
                                    const walker = document.createTreeWalker(textContainer, filter, null, false);
                                    let node;
                                    const textNodes = [];

                                    while(node = walker.nextNode()) {
                                        const parent = node.parentNode;
                                        if (parent && (parent.tagName === 'SCRIPT' || parent.tagName === 'STYLE')) continue;
                                        if (node.nodeValue && node.nodeValue.trim() !== '') {
                                            textNodes.push(node);
                                        }
                                    }

                                    let matchCount = 0;
                                    const queryLower = query.toLowerCase();
                                    const queryLen = query.length;

                                    textNodes.forEach(textNode => {
                                        const text = textNode.nodeValue;
                                        if (!text) return;
                                        const textLower = text.toLowerCase();
                                        let startIndex = 0;
                                        let index;
                                        const matchesInNode = [];

                                        while ((index = textLower.indexOf(queryLower, startIndex)) !== -1) {
                                            matchesInNode.push(index);
                                            startIndex = index + queryLen;
                                        }

                                        if (matchesInNode.length > 0) {
                                            const parent = textNode.parentNode;
                                            if (!parent) return;
                                            
                                            const frag = document.createDocumentFragment();
                                            let lastIdx = 0;

                                            for (let i = 0; i < matchesInNode.length; i++) {
                                                const idx = matchesInNode[i];
                                                frag.appendChild(document.createTextNode(text.substring(lastIdx, idx)));

                                                const span = document.createElement('span');
                                                span.className = 'search-highlight';
                                                span.id = 'search-match-' + matchCount;
                                                const actualMatchText = text.substring(idx, idx + queryLen);
                                                span.textContent = actualMatchText;
                                                frag.appendChild(span);

                                                const start = Math.max(0, idx - 40);
                                                const end = Math.min(text.length, idx + queryLen + 40);
                                                let snippet = text.substring(start, idx) + "<b>" + actualMatchText + "</b>" + text.substring(idx + queryLen, end);
                                                snippet = snippet.replace(/[\r\n\t]+/g, ' ').trim();

                                                searchMatches.push({
                                                    id: 'search-match-' + matchCount,
                                                    text: (start > 0 ? '... ' : '') + snippet + (end < text.length ? ' ...' : ''),
                                                });

                                                lastIdx = idx + queryLen;
                                                matchCount++;
                                            }

                                            frag.appendChild(document.createTextNode(text.substring(lastIdx)));
                                            parent.replaceChild(frag, textNode);
                                        }
                                    });

                                    finalResult = searchMatches.map(m => m.id + "|||" + m.text).join("|||") || "NONE";
                                } catch (e) {
                                    finalResult = "error|||" + e.toString();
                                } finally {
                                    if (window.readerBridge && window.readerBridge.sendSearchResults) {
                                        window.readerBridge.sendSearchResults(finalResult);
                                    }
                                }
                            }, 10);
                        } catch (e) {
                            if (window.readerBridge && window.readerBridge.sendSearchResults) {
                                window.readerBridge.sendSearchResults("error|||Outer Error: " + e.toString());
                            }
                        }
                    };

                    window.jumpToMatch = function(id) {
                         const el = document.getElementById(id);
                         if(el) {
                             const w = wrapper.clientWidth;
                             const rect = el.getBoundingClientRect();
                             const absoluteLeft = wrapper.scrollLeft + rect.left;
                             const targetScroll = Math.floor((absoluteLeft - 4) / w) * w;
                             wrapper.scrollTo({ left: targetScroll, behavior: 'auto' });
                             
                             const oldBg = el.style.backgroundColor;
                             el.style.transition = 'background-color 0.5s ease';
                             el.style.backgroundColor = '#ff9800';
                             
                             setTimeout(() => {
                                 el.style.backgroundColor = oldBg || ''; 
                             }, 500);
                         }
                    };
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    data class EpubLoadResult(val html: String, val toc: List<TocItem>)

    fun loadEpub(file: File, isDarcula: Boolean, fontSize: Int, theme: String? = null, fontFamily: String? = null): EpubLoadResult {
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
        val html = getAppHtml(sb.toString(), colors, fontSize, theme, fontFamily)
        return EpubLoadResult(html, mappedToc)
    }

    fun getWelcomeHtml(isDarcula: Boolean, fontSize: Int, theme: String? = null, fontFamily: String? = null): String {
        val colors = if (isDarcula) ThemeColors("#2b2d30", "#aaa", "rgba(43, 45, 48, 0.9)", "#4e5254", "#4c5052")
        else ThemeColors("#fff", "#333", "rgba(255, 255, 255, 0.9)", "#ddd", "#eee")
        val welcomeContent = """
            <div style='display:flex;flex-direction:column;height:100%;justify-content:center;align-items:center;opacity:0.6;text-align:center;padding:20px;gap:20px;' onclick="if(window.readerBridge && typeof window.readerBridge.openFile === 'function') window.readerBridge.openFile();">
                <svg viewBox="0 0 24 24" style="width: 48px; height: 48px; fill: none; stroke: currentColor; stroke-width: 1.5px; cursor: pointer;">
                    <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
                </svg>
                <div style="font-size: 16px; line-height: 1.8; cursor: pointer;">
                    <div>Click the folder icon above or here to open an EPUB book</div>
                    <div style="font-size: 14px; margin-top: 8px;">点击顶部的文件夹图标或此处打开 EPUB 书籍</div>
                    <div style="font-size: 14px; margin-top: 8px;">上部のフォルダアイコンまたはここをクリックしてEPUBブックを開きます</div>
                </div>
            </div>
        """.trimIndent()
        return getAppHtml(welcomeContent, colors, fontSize, theme, fontFamily)
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
            val idx = spineRefs.indexOfFirst { it.resource.href == href }
            if (idx != -1) TocItem("spine-$idx", item.title) else item
        }
    }

    data class ThemeColors(val bg: String, val text: String, val sidebarBg: String, val border: String, val hover: String)
    data class TocItem(val htmlId: String, val title: String)
}