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

        // MANDATORY: CSP INJECTION FOR DATA URI SUPPORT
        return """
            <!DOCTYPE html>
            <html lang='en'>
            <head>
                <meta charset='UTF-8'>
                <meta http-equiv="Content-Security-Policy" content="default-src 'self' 'unsafe-inline' 'unsafe-eval' data:;">
                <style>
                    /* --- READER MASTER v1.0.0 (INSTA-MINIMAL) --- */
                    :root {
                        --bg: ${colors.bg}; --text: ${colors.text};
                        --border: ${colors.border};
                        --icon-stroke: ${colors.text}; /* Adaptive stroke color */
                        --hover-bg: ${if (colors.bg.startsWith("#2")) "rgba(255,255,255,0.08)" else "rgba(0,0,0,0.04)"};
                    }
                    
                    body { 
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        margin: 0 !important; 
                        padding: 0 !important; 
                        width: 100% !important; 
                        max-width: none !important; 
                        box-sizing: border-box !important;
                        background-color: var(--bg); 
                        color: var(--text); 
                        overflow: hidden; 
                    }
                    * { box-sizing: border-box; }
                    
                    /* TOOLBAR (Pure Minimalist) */
                    #toolbar {
                        position: fixed; top: 0; left: 0; right: 0; height: 50px; 
                        background: var(--bg); 
                        /* No border, just clean space */
                        /* border-bottom: 1px solid var(--border); */ 
                        display: flex; align-items: center; justify-content: space-between;
                        padding: 0 16px; z-index: 1000;
                        user-select: none;
                    }
                    
                    .toolbar-group {
                        display: flex; align-items: center; gap: 20px; /* Air gap */
                    }

                    /* ICON BUTTONS (SVG WRAPPERS) */
                    .icon-btn {
                        width: 32px; height: 32px;
                        background: transparent; border: none; 
                        padding: 4px;
                        cursor: pointer;
                        display: flex; align-items: center; justify-content: center;
                        border-radius: 50%; /* Rounded touch targets */
                        transition: transform 0.1s ease, background 0.2s;
                        opacity: 0.8;
                    }
                    .icon-btn:hover { 
                        opacity: 1;
                        background: var(--hover-bg);
                    }
                    .icon-btn:active { transform: scale(0.92); }

                    /* SVG STYLES */
                    .feather {
                        width: 20px; height: 20px;
                        fill: none;
                        stroke: var(--icon-stroke);
                        stroke-width: 2px;
                        stroke-linecap: round;
                        stroke-linejoin: round;
                    }

                    /* MINIMAL INPUT */
                    #jump-container {
                        position: relative;
                        display: flex; align-items: center; justify-content: center;
                    }
                    #jump-input {
                        width: 42px; height: 26px; 
                        background: transparent; color: var(--text); 
                        border: 1px solid var(--icon-stroke); border-radius: 6px;
                        text-align: center; font-size: 13px; font-weight: 500;
                        opacity: 0.6;
                        transition: opacity 0.2s, width 0.2s;
                    }
                    #jump-input:focus { opacity: 1; outline: none; width: 50px; }
                    /* Hide arrows */
                    input::-webkit-outer-spin-button, input::-webkit-inner-spin-button { -webkit-appearance: none; margin: 0; }
                    
                    /* CONTENT CONTAINER */
                    #content {
                        position: absolute; top: 50px; bottom: 0; left: 0; right: 0;
                        overflow: hidden;
                    }

                    /* READER WRAPPER */
                    #reader-wrapper {
                        width: 100%; height: 100%;
                        overflow-x: scroll; overflow-y: hidden;
                        scroll-snap-type: x mandatory;
                        scroll-behavior: smooth;
                        outline: none;
                    }
                    
                    #reader-text {
                        height: 100%; width: 100%;
                        column-width: 100vw; column-gap: 0; column-fill: auto;
                    }
                    
                    /* EPUB STYLES */
                    .chapter { break-before: column; }
                    .page-content { padding: 20px 48px; margin: 0; width: 100%; box-sizing: border-box; max-width: 800px; margin: 0 auto; }
                    p { line-height: 1.8; margin-bottom: 1.2em; text-align: justify; font-size: 16px; letter-spacing: 0.01em; }
                    img { max-width: 100%; height: auto; display: block; margin: 20px auto; border-radius: 8px; }
                    
                    ::-webkit-scrollbar { display: none !important; }

                    /* SIDEBAR (TOC) */
                    #sidebar {
                        position: absolute; top: 0; left: 0; bottom: 0; width: 280px;
                        background: var(--bg); border-right: 1px solid var(--border);
                        transform: translateX(-100%); transition: transform 0.25s cubic-bezier(0.165, 0.84, 0.44, 1);
                        z-index: 2000; display: flex; flex-direction: column;
                        box-shadow: none !important;
                        padding-top: 20px;
                    }
                    #sidebar.open { transform: translateX(0); }
                    .sidebar-header {
                         padding: 0 20px 15px; font-weight: 700; font-size: 18px; letter-spacing: -0.5px;
                         border-bottom: 1px solid var(--border); display: flex; justify-content: space-between; align-items: center;
                    }
                    .toc-list { flex: 1; overflow-y: auto; padding: 10px 0; }
                    .toc-item { 
                        padding: 12px 20px; font-size: 14px; cursor: pointer; color: var(--text); opacity: 0.8; 
                        border-left: 2px solid transparent; transition: all 0.2s;
                    }
                    .toc-item:hover { background: var(--hover-bg); opacity: 1; border-left-color: var(--icon-stroke); }
                    #sidebar-backdrop { position: absolute; inset: 0; background: rgba(0,0,0,0.3); z-index: 1500; backdrop-filter: blur(2px); display: none; }
                    #sidebar.open + #sidebar-backdrop { display: block; }
                </style>
            </head>
            <body>
                <div id="toolbar">
                    <!-- Left: Menu & Open -->
                    <div class="toolbar-group">
                        <button class="icon-btn" title="Chapters" onclick="toggleSidebar()">
                            <svg class="feather" viewBox="0 0 24 24"><line x1="3" y1="12" x2="21" y2="12"></line><line x1="3" y1="6" x2="21" y2="6"></line><line x1="3" y1="18" x2="21" y2="18"></line></svg>
                        </button>
                        <button class="icon-btn" title="Open File" onclick="window.readerBridge.openFile()">
                            <svg class="feather" viewBox="0 0 24 24"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path></svg>
                        </button>
                    </div>

                    <!-- Center: Navigation -->
                    <div class="toolbar-group">
                        <button class="icon-btn" title="Previous" onclick="navPrev()">
                            <svg class="feather" viewBox="0 0 24 24"><polyline points="15 18 9 12 15 6"></polyline></svg>
                        </button>
                        
                        <div id="jump-container">
                             <input type="number" id="jump-input" placeholder="#" onkeydown="checkJump(event)">
                        </div>

                        <button class="icon-btn" title="Next" onclick="navNext()">
                            <svg class="feather" viewBox="0 0 24 24"><polyline points="9 18 15 12 9 6"></polyline></svg>
                        </button>
                    </div>

                    <!-- Right: Zoom -->
                    <div class="toolbar-group">
                        <button class="icon-btn" title="Zoom Out" onclick="zoomOut()">
                            <svg class="feather" viewBox="0 0 24 24"><line x1="5" y1="12" x2="19" y2="12"></line></svg>
                        </button>
                        <button class="icon-btn" title="Zoom In" onclick="zoomIn()">
                             <svg class="feather" viewBox="0 0 24 24"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
                        </button>
                    </div>
                </div>
                
                <!-- TOC Sidebar -->
                <div id="sidebar">
                    <div class="sidebar-header">Chapters <span style="font-size: 24px; cursor: pointer;" onclick="toggleSidebar()">×</span></div>
                    <div class="toc-list">$tocListHtml</div>
                </div>
                <div id="sidebar-backdrop" onclick="toggleSidebar()"></div>

                <div id="content">
                    <div id="reader-wrapper" tabindex="0">
                        <div id="reader-text">$contentHtml</div>
                    </div>
                </div>
                
                <!-- FOOTER (Minimal) -->
                <div style="position:fixed; bottom:12px; left:0; right:0; text-align:center; font-size:10px; opacity:0.3; pointer-events:none;">
                    <span id="footer-prog"></span>
                </div>

                <script>
                    const wrapper = document.getElementById('reader-wrapper');
                    const textContainer = document.getElementById('reader-text');
                    const jumpInput = document.getElementById('jump-input');
                    const footerProg = document.getElementById('footer-prog');
                    
                    let currentFontSize = 16;
                    let saveTimeout = null;

                    // --- LAYOUT ---
                    function updateLayout(width) {
                        const w = width || window.innerWidth;
                        if(w > 0) {
                            textContainer.style.width = 'auto'; 
                            textContainer.style.columnWidth = w + 'px';
                        }
                        updateProgress();
                    }
                    const observer = new ResizeObserver(entries => {
                         for(let entry of entries) {
                             if(entry.contentRect.width > 0) {
                                 const idx = Math.round(wrapper.scrollLeft / (wrapper.clientWidth || 1));
                                 updateLayout(entry.contentRect.width);
                                 wrapper.scrollTo({ left: idx * entry.contentRect.width, behavior: 'instant' });
                             }
                         }
                    });
                    observer.observe(wrapper);
                    window.onload = () => { updateLayout(); wrapper.focus(); };

                    // --- NAVIGATION ---
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
                    
                    // --- JUMP ---
                    function manualJump() {
                         const val = parseInt(jumpInput.value);
                         if (val > 0) {
                             const w = wrapper.clientWidth;
                             wrapper.scrollTo({ left: (val-1) * w, behavior: 'auto' });
                             jumpInput.value = ''; jumpInput.blur();
                         }
                    }
                    function checkJump(e) { if (e.key === 'Enter') manualJump(); }

                    // --- ZOOM ---
                    function zoomIn() { currentFontSize++; applyFont(); }
                    function zoomOut() { if(currentFontSize>10)currentFontSize--; applyFont(); }
                    function applyFont() { document.body.style.fontSize = currentFontSize+'px'; }

                    // --- TOC ---
                    function toggleSidebar() { document.getElementById('sidebar').classList.toggle('open'); }
                    function scrollToId(id) {
                         const el = document.getElementById(id);
                         if(el) { el.scrollIntoView(); toggleSidebar(); }
                    }

                    // --- PERSISTENCE & PROGRESS ---
                    wrapper.addEventListener('scroll', () => {
                        updateProgress();
                        if(saveTimeout) clearTimeout(saveTimeout);
                        saveTimeout = setTimeout(() => {
                            const w = wrapper.clientWidth;
                            if(w > 0 && window.readerBridge) {
                                const idx = Math.round(wrapper.scrollLeft / w);
                                window.readerBridge.saveProgress(idx.toString());
                            }
                        }, 500);
                    });
                    
                    function updateProgress() {
                        const w = wrapper.clientWidth;
                        if(w > 0) {
                             const current = Math.round(wrapper.scrollLeft / w) + 1;
                             const total = Math.ceil(wrapper.scrollWidth / w) || 1;
                             footerProg.textContent = current + ' / ' + total;
                        }
                    }
                    
                    window.readerRestore = function(s) {
                        const i = parseInt(s);
                        if(i >= 0) setTimeout(() => wrapper.scrollTo({ left: i * wrapper.clientWidth, behavior: 'instant' }), 100);
                    };

                    // --- HOTKEYS ---
                    document.addEventListener('keydown', function(e) {
                         const k = e.key.toLowerCase();
                         if (document.activeElement === jumpInput && k !== 'enter') return;
                         if (k === 'arrowright' || k === 'd') navNext();
                         else if (k === 'arrowleft' || k === 'a') navPrev();
                    });
                    
                    // EXPOSE
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
        // Requirement 1: Robust Image Map (Key 1: Full Path, Key 2: Filename)
        val imageMap = mutableMapOf<String, String>()
        println("📚 STARTING EPUB IMAGE INVENTORY 📚")
        
        for (res in book.resources.all) {
            val href = res.href
            val mime = getMimeType(href)
            
            if (mime != null && mime.startsWith("image/")) {
                val filename = href.substringAfterLast('/')
                println("📚 EPUB Resource found: [${res.href}] -> Filename: [$filename]")
                
                try {
                    // Requirement 2: Strict Base64 Sanitization (No newlines)
                    val b64 = Base64.getEncoder().encodeToString(res.data).replace(Regex("\\s"), "")
                    val dataUri = "data:$mime;base64,$b64"
                    
                    // Store BOTH full href and simple filename for maximum matching success
                    imageMap[href.lowercase(Locale.getDefault())] = dataUri
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
            // Requirement 4: Support both <img> and <image> (SVG)
            val allImages = doc.select("img, image")
            
            for (img in allImages) {
                // Determine attribute: src for img, xlink:href for image (SVG)
                val isSvgImage = img.tagName().equals("image", ignoreCase = true)
                val attrName = if (isSvgImage) "xlink:href" else "src"
                
                var src = img.attr(attrName)
                if (src.isEmpty() && isSvgImage) src = img.attr("href") // Fallback for simple href
                
                println("🖼️ HTML requesting src: [$src]")
                
                // Strategy: Clean the src
                val rawKey = src.substringAfterLast('/').lowercase(Locale.getDefault()).replace("%20", " ")
                // Also try full path matching (heuristic: if src contains /, allow it)
                val fullKey = src.lowercase(Locale.getDefault())
                
                var finalData: String? = null
                if (imageMap.containsKey(rawKey)) finalData = imageMap[rawKey]
                else if (imageMap.containsKey(fullKey)) finalData = imageMap[fullKey]
                
                println("   👉 Trying to match key: [$rawKey] -> Result: ${if (finalData != null) "SUCCESS" else "FAILED"}")
                
                if (finalData != null) {
                    img.attr(attrName, finalData)
                    if (isSvgImage) img.attr("href", finalData) // Redundancy for safety
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
    
    // Requirement 3: Strict MIME Type Detection
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
