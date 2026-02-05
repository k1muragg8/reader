package com.shaoYe.reader

import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.domain.TOCReference
import nl.siegmann.epublib.epub.EpubReader
import org.jsoup.Jsoup
import java.io.File
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.Base64
import java.util.Locale

object EpubParser {

    private fun getAppHtml(tocItems: List<TocItem>, contentHtml: String, colors: ThemeColors): String {
        val tocListHtml = if (tocItems.isEmpty()) {
            "<div style='padding:10px;color:#888;'>No chapters</div>"
        } else {
            tocItems.joinToString("") {
                "<div class='toc-item' onclick=\"scrollToId('${it.htmlId}')\">${it.title}</div>"
            }
        }
        
        return """
            <!DOCTYPE html>
            <html lang='en'>
            <head>
                <meta charset='UTF-8'>
                <meta http-equiv="Content-Security-Policy" content="default-src 'self' 'unsafe-inline' 'unsafe-eval' data:;">
                <style>
                    /* --- READER MASTER v18 UNIVERSAL --- */
                    :root {
                        --bg: ${colors.bg}; --text: ${colors.text};
                        --sidebar: ${colors.sidebarBg}; --border: ${colors.border};
                        --hover: ${colors.hover};
                        --footer-text: #888;
                    }
                    
                    html, body {
                        margin: 0; padding: 0; width: 100vw; height: 100vh; overflow: hidden;
                        background: var(--bg); color: var(--text);
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif;
                    }
                    * { box-sizing: border-box; }
                    
                    #app-container {
                        position: relative; width: 100%; height: 100%; overflow: hidden;
                    }
                    
                    /* READER WRAPPER */
                    #reader-wrapper {
                        position: absolute; top: 32px; bottom: 24px; left: 0; right: 0;
                        overflow-x: scroll; overflow-y: hidden;
                        outline: none;
                        
                        /* SCROLL SNAP */
                        scroll-snap-type: x mandatory;
                        scroll-behavior: smooth;
                    }
                    
                    #reader-text {
                        height: 100%; width: 100%;
                        column-width: 100vw; column-gap: 0; column-fill: auto;
                    }
                    
                    /* HIDE SCROLLBARS */
                    ::-webkit-scrollbar { display: none !important; }

                    /* FOOTER */
                    #footer {
                        position: absolute; bottom: 0; left: 0; right: 0; height: 24px;
                        background: var(--bg); border-top: 1px solid var(--border);
                        display: flex; align-items: center; justify-content: space-between;
                        padding: 0 12px; font-size: 11px; color: var(--footer-text);
                        z-index: 600; user-select: none;
                        font-variant-numeric: tabular-nums;
                    }

                    /* TOC OVERLAY */
                    #sidebar {
                        position: absolute; top: 0; left: 0; bottom: 0; width: 260px;
                        background: var(--sidebar); border-right: 1px solid var(--border);
                        transform: translateX(-100%); transition: transform 0.2s cubic-bezier(0.25, 1, 0.5, 1);
                        z-index: 1000; display: flex; flex-direction: column;
                        box-shadow: 4px 0 16px rgba(0,0,0,0.25);
                    }
                    #sidebar.open { transform: translateX(0); }
                    
                    .sidebar-header {
                        padding: 0 12px; height: 32px; border-bottom: 1px solid var(--border);
                        font-weight: 600; font-size: 11px; display: flex; align-items: center; justify-content: space-between;
                    }
                    .toc-list { flex: 1; overflow-y: auto; }
                    .toc-item {
                        padding: 8px 12px; border-bottom: 1px solid var(--border);
                        font-size: 12px; cursor: pointer; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
                        transition: background 0.1s;
                    }
                    .toc-item:hover { background: var(--hover); }

                    #sidebar-backdrop {
                         position: absolute; top: 0; left: 0; right: 0; bottom: 0;
                         background: rgba(0,0,0,0.4); z-index: 900; display: none;
                    }
                    #sidebar.open + #sidebar-backdrop { display: block; }

                    /* HEADER */
                    #header {
                        position: absolute; top: 0; left: 0; right: 0; height: 32px;
                        background: var(--bg); border-bottom: 1px solid var(--border);
                        display: flex; align-items: center; padding: 0 8px; gap: 6px; z-index: 500;
                    }
                    .icon-btn {
                        width: 20px; height: 20px; background: transparent; border: none;
                        border-radius: 3px; color: var(--text); cursor: pointer;
                        display: flex; align-items: center; justify-content: center;
                        font-family: inherit;
                    }
                    .icon-btn:hover { background: var(--hover); }
                    
                    #jump-input {
                        width: 32px; height: 18px; font-size: 11px; text-align: center;
                        background: transparent; color: var(--text); border: 1px solid var(--border);
                        border-radius: 2px;
                    }
                    #jump-input:focus { border-color: #555; outline: none; }

                    /* EPUB CONTENT */
                    .chapter { break-before: column; }
                    .page-content { padding: 40px 8%; max-width: 800px; margin: 0 auto; box-sizing: border-box; }
                    p { line-height: 1.6; margin-bottom: 0.8em; text-align: justify; font-size: 16px; }
                    img { max-width: 100%; height: auto; display: block; margin: 15px auto; }
                </style>
            </head>
            <body>
                <div id="app-container">
                    <!-- TOC -->
                    <div id="sidebar">
                        <div class="sidebar-header">CONTENTS <span class="icon-btn" onclick="toggleSidebar()">✕</span></div>
                        <div class="toc-list">$tocListHtml</div>
                    </div>
                    <div id="sidebar-backdrop" onclick="toggleSidebar()"></div>
                    
                    <!-- HEADER -->
                    <div id="header">
                        <button class="icon-btn" onclick="toggleSidebar()">☰</button>
                        <span style="width:1px;height:12px;background:var(--border);"></span>
                        <button class="icon-btn" onclick="window.readerBridge.openFile()">📂</button>
                        <span style="width:1px;height:12px;background:var(--border);"></span>
                        <button class="icon-btn" onclick="navPrev()">◀</button>
                        <input type="number" id="jump-input" placeholder="#" onkeydown="checkJump(event)">
                        <button class="icon-btn" onclick="navNext()">▶</button>
                        <span style="flex:1;"></span>
                        <button class="icon-btn" onclick="zoomIn()">A+</button>
                        <button class="icon-btn" onclick="zoomOut()">A-</button>
                    </div>
                    
                    <!-- READER -->
                    <div id="reader-wrapper" tabindex="0">
                        <div id="reader-text">$contentHtml</div>
                    </div>
                    
                    <!-- FOOTER -->
                    <div id="footer">
                        <span id="footer-prog">Page 1</span>
                        <span id="footer-pct">0%</span>
                    </div>
                </div>

                <script>
                    const wrapper = document.getElementById('reader-wrapper');
                    const textContainer = document.getElementById('reader-text');
                    const jumpInput = document.getElementById('jump-input');
                    const footerProg = document.getElementById('footer-prog');
                    const footerPct = document.getElementById('footer-pct');
                    
                    let currentFontSize = 16;
                    let saveTimeout = null;

                    // --- LAYOUT & RESIZE ---
                    function updateLayout(width) {
                        const w = width || window.innerWidth;
                        if(w > 0) {
                            textContainer.style.width = 'auto'; // Reset
                            textContainer.style.columnWidth = w + 'px';
                        }
                        updateDashboard();
                    }

                    const observer = new ResizeObserver(entries => {
                         for(let entry of entries) {
                             const newWidth = entry.contentRect.width;
                             if(newWidth > 0) {
                                 // Preserve Page Index
                                 const idx = Math.round(wrapper.scrollLeft / (wrapper.clientWidth || 1));
                                 
                                 updateLayout(newWidth);
                                 
                                 // Instant Snap
                                 wrapper.scrollTo({ left: idx * newWidth, behavior: 'instant' });
                             }
                         }
                    });
                    observer.observe(wrapper);
                    window.onload = () => { updateLayout(); wrapper.focus(); };

                    // --- JUMP UX (FIXED) ---
                    function checkJump(e) {
                         // Fix: "Input Clear Logic"
                         if (e.key === 'Enter') {
                             const val = parseInt(jumpInput.value);
                             if (val > 0) {
                                 const w = wrapper.clientWidth;
                                 wrapper.scrollTo({ left: (val-1) * w, behavior: 'auto' });
                             }
                             jumpInput.value = ''; // FORCE CLEAR
                             jumpInput.blur();     // REMOVE FOCUS
                         }
                    }

                    // --- DASHBOARD & PERSISTENCE ---
                    wrapper.addEventListener('scroll', () => {
                        updateDashboard();
                        
                        // Save Progress (Debounce)
                        if(saveTimeout) clearTimeout(saveTimeout);
                        saveTimeout = setTimeout(() => {
                            const w = wrapper.clientWidth;
                            if(w > 0) {
                                const idx = Math.round(wrapper.scrollLeft / w);
                                if(window.readerBridge) {
                                    window.readerBridge.saveProgress(idx.toString());
                                }
                            }
                        }, 500);
                    });
                    
                    function updateDashboard() {
                        const w = wrapper.clientWidth;
                        if(w <= 0) return;
                        
                        const current = Math.round(wrapper.scrollLeft / w) + 1;
                        const total = Math.ceil(wrapper.scrollWidth / w) || 1;
                        
                        footerProg.textContent = `Page ` + current + ` / ` + total;
                        footerPct.textContent = Math.round((current/total)*100) + `%`;
                    }
                    
                    // RESTORE
                    window.readerRestore = function(savedIdxStr) {
                        if(!savedIdxStr) return;
                        const idx = parseInt(savedIdxStr);
                        if(idx >= 0) {
                             setTimeout(() => {
                                 const w = wrapper.clientWidth;
                                 wrapper.scrollTo({ left: idx * w, behavior: 'instant' });
                                 updateDashboard();
                             }, 150);
                        }
                    };

                    // --- NAV ---
                    function navNext() { 
                         const w = wrapper.clientWidth;
                         const idx = Math.round(wrapper.scrollLeft / w);
                         wrapper.scrollTo({ left: (idx + 1) * w, behavior: 'smooth' });
                    }
                    function navPrev() { 
                         const w = wrapper.clientWidth;
                         const idx = Math.round(wrapper.scrollLeft / w);
                         wrapper.scrollTo({ left: (idx - 1) * w, behavior: 'smooth' });
                    }
                    
                    // --- ACTIONS ---
                    function toggleSidebar() { document.getElementById('sidebar').classList.toggle('open'); }
                    function zoomIn() { currentFontSize++; applyFont(); }
                    function zoomOut() { if(currentFontSize>10)currentFontSize--; applyFont(); }
                    function applyFont() { document.body.style.fontSize = currentFontSize+'px'; }
                    
                    function scrollToId(id) {
                         const el = document.getElementById(id);
                         if(el) {
                             el.scrollIntoView();
                             toggleSidebar();
                         }
                    }
                    
                    // HOTKEYS
                    document.addEventListener('keydown', (e) => {
                         if(document.activeElement === jumpInput) return;
                         if(e.key === 'ArrowRight') navNext();
                         else if(e.key === 'ArrowLeft') navPrev();
                    });
                    
                    window.readerNext = navNext;
                    window.readerPrev = navPrev;
                    window.readerZoomIn = zoomIn;
                    window.readerZoomOut = zoomOut;
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    fun loadEpub(file: File, isDarcula: Boolean): String {
        val epubReader = EpubReader()
        val book = epubReader.readEpub(file.inputStream())
        
        // --- STEP 1: INVENTORY (LOG EVERYTHING) ---
        val imageMap = mutableMapOf<String, String>()
        println("📚 STARTING EPUB IMAGE INVENTORY 📚")
        
        for (res in book.resources.all) {
            val href = res.href
            val mime = getMimeType(href)
            
            if (mime != null && mime.startsWith("image/")) {
                val filename = href.substringAfterLast('/')
                println("📚 EPUB Resource found: [${res.href}] -> Filename: [$filename]")
                
                try {
                    val b64 = Base64.getEncoder().encodeToString(res.data).replace(Regex("\\s"), "")
                    val dataUri = "data:$mime;base64,$b64"
                    imageMap[filename.lowercase(Locale.getDefault())] = dataUri
                } catch (e: Exception) {
                    println("❌ Failed to encode ${res.href}: ${e.message}")
                }
            }
        }

        // --- THEME ---
        val colors = if (isDarcula) ThemeColors("#2b2d30", "#a9b7c6", "#3c3f41", "#4e5254", "#4c5052") 
                     else ThemeColors("#ffffff", "#333333", "#f2f2f2", "#d0d0d0", "#e6e6e6")

        // --- BUILD CONTENT ---
        val sb = StringBuilder()
        var chapterIndex = 0
        val tocItems = extractTocItems(book)
        
        for (spineRef in book.spine.spineReferences) {
            val res = spineRef.resource
            val rawHtml = String(res.data, Charset.forName(res.inputEncoding ?: "UTF-8"))
            val doc = Jsoup.parse(rawHtml)
            
            // --- STEP 2: THE "DESPERATE" MATCHER ---
            for (img in doc.select("img")) {
                val src = img.attr("src")
                println("🖼️ HTML requesting src: [$src]")
                
                // Strategy: Clean the src
                val filename = src.substringAfterLast('/').lowercase(Locale.getDefault()).replace("%20", " ")
                val found = imageMap.containsKey(filename)
                
                println("   👉 Trying to match key: [$filename] -> Result: ${if (found) "SUCCESS" else "FAILED"}")
                
                if (found) {
                    img.attr("src", imageMap[filename])
                }
            }
            
            val id = "spine-$chapterIndex"
            sb.append("<div id='$id' class='chapter'><div class='page-content'>")
            sb.append(doc.body().html())
            sb.append("</div></div>")
            
            chapterIndex++
        }
        
        val mappedToc = mapTocToSpineIds(tocItems, book)
        return getAppHtml(mappedToc, sb.toString(), colors)
    }

    fun getWelcomeHtml(isDarcula: Boolean): String {
         val colors = if (isDarcula) ThemeColors("#2b2d30", "#aaa", "#3c3f41", "#4e5254", "#4c5052") 
                     else ThemeColors("#fff", "#333", "#f2f2f2", "#ddd", "#eee")
         return getAppHtml(emptyList(), "<div style='display:flex;height:100%;justify-content:center;align-items:center;opacity:0.5;'><h2>Click 📂 to Open</h2></div>", colors)
    }
    

    
    // STRICT MIME DETECTION
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
