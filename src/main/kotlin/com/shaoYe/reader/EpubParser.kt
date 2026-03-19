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

                    body, html { margin: 0; padding: 0; width: 100vw; height: 100vh; overflow: hidden; background: var(--bg); color: var(--text); font-family: var(--font-family); }
                    
                    #content { width: 100vw; height: 100vh; display: flex; flex-direction: column; overflow: hidden; position: relative; }
                    #reader-wrapper { flex: 1; width: 100%; height: 100%; overflow-x: scroll; overflow-y: hidden; scroll-snap-type: x mandatory; outline: none; transition: opacity 0.2s; }
                    #reader-wrapper.resizing { scroll-snap-type: none; cursor: col-resize; opacity: 0.8; }
                    #reader-text { height: 100%; padding: 5px 60px; column-fill: auto; position: relative; }
                    
                    .chapter { break-before: column; }
                    .page-content { padding: 0; margin: 0; width: 100%; box-sizing: border-box; }
                    h1, h2, h3, h4, h5, h6 { break-inside: avoid; break-after: avoid; }
                    p { line-height: 1.6; margin: 0; text-indent: 1.5em; text-align: justify; font-size: var(--font-size); letter-spacing: 0.02em; }
                    img { max-width: 100%; max-height: 80vh; height: auto; display: block; margin: 20px auto; border-radius: 8px; break-inside: avoid; }
                    
                    ::-webkit-scrollbar { display: none !important; }
                    .search-highlight { background-color: #ffeb3b; color: #000; border-radius: 2px; }
                    .active-match { background-color: #ff9800 !important; color: #fff !important; box-shadow: 0 0 4px rgba(0,0,0,0.4); }

                    /* --- 隐私与隐匿功能：焦点失焦时隐藏内容 --- */
                    #reader-text { transition: opacity 0.15s ease-in-out; }
                    body.focus-lost #reader-text { opacity: 0 !important; }
                </style>
            </head>
            <body>
                <div id="content">
                    <div id="reader-wrapper" tabindex="0">
                        <div id="reader-text">$contentHtml</div>
                    </div>
                </div>
                
                <script>
                    $bridgeScript

                    /* --- 焦点监听：用于隐匿内容 --- */
                    window.addEventListener('blur', () => { document.body.classList.add('focus-lost'); });
                    window.addEventListener('focus', () => { document.body.classList.remove('focus-lost'); });
                    if (!document.hasFocus()) { document.body.classList.add('focus-lost'); }
                    
                    window.onerror = function(msg, url, line, col, error) {
                        if (window.readerBridge && window.readerBridge.sendSearchResults) {
                            window.readerBridge.sendSearchResults("error|||JS Crash: " + msg + " at " + line + ":" + col);
                        }
                        return false;
                    };

                    const wrapper = document.getElementById('reader-wrapper');
                    const textContainer = document.getElementById('reader-text');
                    let allElements = [];

                    let isResizing = false;
                    let resizeTimer = null;
                    let currentAnchorIndex = 0;
                    let currentAnchorAbsoluteLeft = 0;
                    let lastWheelTime = 0;
                    let saveTimeout = null;
                    let progressTimeout = null;

                    window.onload = () => { 
                         if (!textContainer || !wrapper) return;
                         allElements = Array.from(textContainer.querySelectorAll('p, h1, h2, h3, img, li, blockquote'));
                         updateLayout(); 
                         setTimeout(updateLayout, 100);
                         wrapper.focus();
                    };

                    function updateLayout(width) {
                        if (!wrapper || !textContainer) return;
                        const w = width || wrapper.clientWidth;
                        if(w > 0) {
                            textContainer.style.width = 'auto'; 
                            textContainer.style.paddingLeft = '4px';
                            textContainer.style.paddingRight = '4px';
                            const usableWidth = w - 8;
                            textContainer.style.columnWidth = Math.max(usableWidth, 20) + 'px';
                            textContainer.style.columnGap = '8px';
                        }
                    }

                    wrapper.addEventListener('scroll', () => {
                        if (isResizing) return;
                        updateProgress(); 
                        if(saveTimeout) clearTimeout(saveTimeout);
                        saveTimeout = setTimeout(() => {
                            findCurrentAnchor();
                            saveToBridge();
                        }, 200);
                    });

                    function findCurrentAnchor() {
                        let bestIndex = 0;
                        let minDistance = Infinity;
                        for (let i = 0; i < allElements.length; i++) {
                            const rect = allElements[i].getBoundingClientRect();
                            if (rect.left >= 0 && rect.left < minDistance) {
                                minDistance = rect.left;
                                bestIndex = i;
                            }
                        }
                        currentAnchorIndex = bestIndex;
                    }

                    const observer = new ResizeObserver(entries => {
                        for(let entry of entries) {
                            const width = entry.contentRect.width;
                            if(width <= 0) continue;
                            if (!isResizing) {
                                isResizing = true;
                                wrapper.classList.add('resizing');
                                const anchorEl = allElements[currentAnchorIndex];
                                if (anchorEl) {
                                    currentAnchorAbsoluteLeft = wrapper.scrollLeft + anchorEl.getBoundingClientRect().left;
                                }
                            }
                            if(resizeTimer) clearTimeout(resizeTimer);
                            updateLayout(width);
                            if (allElements[currentAnchorIndex]) {
                                const newWidth = wrapper.clientWidth;
                                const targetLeft = Math.floor(currentAnchorAbsoluteLeft / (newWidth || 1)) * newWidth;
                                wrapper.scrollTo({left: targetLeft, behavior: 'instant'});
                            }
                            resizeTimer = setTimeout(() => {
                                isResizing = false;
                                wrapper.classList.remove('resizing');
                                updateProgress();
                            }, 200);
                        }
                    });
                    observer.observe(wrapper);

                    function saveToBridge() {
                        if(!window.readerBridge) return;
                        const w = wrapper.clientWidth;
                        const maxScroll = wrapper.scrollWidth - w;
                        const pct = maxScroll > 0 ? (wrapper.scrollLeft / maxScroll) : 0;
                        window.readerBridge.saveProgress(pct.toString());
                    }

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
                         if(window.readerBridge) window.readerBridge.sendProgressInfo(getProgressString());
                    };
                    
                    window.readerRestore = function(s) {
                        const pct = parseFloat(s);
                        if(!isNaN(pct)) {
                            setTimeout(() => {
                                wrapper.scrollTo({ left: pct * (wrapper.scrollWidth - wrapper.clientWidth), behavior: 'instant' });
                                setTimeout(findCurrentAnchor, 200);
                            }, 300);
                        }
                    };

                    document.addEventListener('keydown', function(e) {
                        if (document.activeElement && (document.activeElement.tagName === 'INPUT' || document.activeElement.tagName === 'TEXTAREA')) return;
                        if (e.key === 'ArrowLeft' || e.key.toLowerCase() === 'a') { e.preventDefault(); navPrev(); }
                        else if (e.key === 'ArrowRight' || e.key.toLowerCase() === 'd') { e.preventDefault(); navNext(); }
                    });
                    
                    wrapper.addEventListener('wheel', (e) => {
                         e.preventDefault();
                         const now = Date.now();
                         if (now - lastWheelTime > 300) {
                             if (e.deltaY > 0 || e.deltaX > 0) navNext();
                             else if (e.deltaY < 0 || e.deltaX < 0) navPrev();
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

                    function clearHighlights() {
                        const highlights = document.querySelectorAll('.search-highlight');
                        highlights.forEach(span => {
                            const parent = span.parentNode;
                            if (parent) {
                                parent.replaceChild(document.createTextNode(span.textContent), span);
                                parent.normalize();
                            }
                        });
                        searchMatches = [];
                    }

                    let searchMatches = [];
                    window.performSearchFromNative = function(query) {
                        let finalResult = "NONE";
                        try {
                            query = (query || "").trim();
                            if (!query) {
                                clearHighlights();
                                if (window.readerBridge) window.readerBridge.sendSearchResults("");
                                return;
                            }
                            clearHighlights();
                            searchMatches = [];
                            const container = document.getElementById('reader-text');
                            if (!container) return;

                            const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, null, false);
                            let node;
                            const textNodes = [];
                            while(node = walker.nextNode()) {
                                const p = node.parentNode;
                                if (p && (p.tagName === 'SCRIPT' || p.tagName === 'STYLE')) continue;
                                textNodes.push(node);
                            }

                            let matchCount = 0;
                            const maxMatches = 500;
                            const queryLower = query.toLowerCase();
                            const queryLen = query.length;

                            for (let textNode of textNodes) {
                                if (matchCount >= maxMatches) break;
                                const text = textNode.nodeValue;
                                const textLower = text.toLowerCase();
                                let startIndex = 0, index;
                                const matchesInNode = [];
                                while ((index = textLower.indexOf(queryLower, startIndex)) !== -1) {
                                    matchesInNode.push(index);
                                    startIndex = index + queryLen;
                                    if (matchCount + matchesInNode.length >= maxMatches) break;
                                }

                                if (matchesInNode.length > 0) {
                                    const parent = textNode.parentNode;
                                    const frag = document.createDocumentFragment();
                                    let lastIdx = 0;
                                    for (let idx of matchesInNode) {
                                        frag.appendChild(document.createTextNode(text.substring(lastIdx, idx)));
                                        const span = document.createElement('span');
                                        span.className = 'search-highlight';
                                        span.id = 'search-match-' + matchCount;
                                        span.textContent = text.substring(idx, idx + queryLen);
                                        frag.appendChild(span);

                                        const start = Math.max(0, idx - 40), end = Math.min(text.length, idx + queryLen + 40);
                                        let snippet = text.substring(start, idx) + "<b>" + span.textContent + "</b>" + text.substring(idx + queryLen, end);
                                        searchMatches.push({ id: span.id, text: (start > 0 ? '... ' : '') + snippet.replace(/\s+/g, ' ') + (end < text.length ? ' ...' : '') });
                                        lastIdx = idx + queryLen;
                                        matchCount++;
                                    }
                                    frag.appendChild(document.createTextNode(text.substring(lastIdx)));
                                    parent.replaceChild(frag, textNode);
                                }
                            }
                            finalResult = searchMatches.map(m => m.id + "|||" + m.text).join("|||") || "NONE";
                        } catch (e) { finalResult = "error|||" + e.toString(); }
                        finally { if (window.readerBridge) window.readerBridge.sendSearchResults(finalResult); }
                    };

                    window.jumpToMatch = function(id) {
                         document.querySelectorAll('.active-match').forEach(el => el.classList.remove('active-match'));
                         const el = document.getElementById(id);
                         if(el) {
                             el.classList.add('active-match');
                             const container = document.getElementById('reader-wrapper');
                             const w = container.clientWidth;
                             const rect = el.getBoundingClientRect();
                             const absoluteLeft = container.scrollLeft + rect.left;
                             container.scrollTo({ left: Math.floor((absoluteLeft - 10) / (w || 1)) * w, behavior: 'auto' });
                         }
                    };
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
                    <div style="font-size: 20px; font-weight: 500; margin-bottom: 15px;">Reader Master v1.4.0</div>
                    
                    <div style="font-size: 14px; line-height: 1.8; margin-bottom: 20px;">
                        <div style="font-weight: bold; color: var(--text);">New in this version:</div>
                        <div>• Instant Search with Snippets & Direct Jump</div>
                        <div>• Ultra-Narrow 5px Margins Layout</div>
                    </div>
                    
                    <div style="font-size: 14px; line-height: 1.8; margin-bottom: 20px; opacity: 0.9;">
                        <div style="font-weight: bold; color: var(--text);">新版本特性：</div>
                        <div>• 实时搜索、片段预览与点击跳转</div>
                        <div>• 极致的 5px 边缘贴合排版</div>
                    </div>

                    <div style="font-size: 14px; line-height: 1.8; opacity: 0.8;">
                        <div style="font-weight: bold; color: var(--text);">新機能：</div>
                        <div>• インスタント検索、スニペット表示とジャンプ</div>
                        <div>• 極限まで広げた 5px ベゼルレイアウト</div>
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
            val idx = spineRefs.indexOfFirst { it.resource.href == href }
            if (idx != -1) TocItem("spine-$idx", item.title) else item
        }
    }

    data class ThemeColors(val bg: String, val text: String, val sidebarBg: String, val border: String, val hover: String)
    data class TocItem(val htmlId: String, val title: String)
}