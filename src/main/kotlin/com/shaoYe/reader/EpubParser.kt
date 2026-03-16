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

    private fun getAppHtml(tocItems: List<TocItem>, contentHtml: String, colors: ThemeColors, fontSize: Int): String {
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
                    :root {
                        /* Base System Colors */
                        --bg: ${colors.bg}; --text: ${colors.text};
                        --sidebar-bg: ${colors.sidebarBg};
                        --border: ${colors.border};
                        --icon-stroke: ${colors.text};
                        --hover-bg: ${if (colors.bg.startsWith("#2")) "rgba(255,255,255,0.08)" else "rgba(0,0,0,0.04)"};
                        --font-size: ${fontSize}px;
                        --font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
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
                        position: fixed; top: 0; left: 0; right: 0; height: 60px;
                        background: var(--sidebar-bg); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px);
                        display: grid; grid-template-columns: 1fr auto 1fr;
                        align-items: center; padding: 0 16px; z-index: 1002; user-select: none;
                        transform: translateY(-100%); transition: transform 0.3s cubic-bezier(0.19, 1, 0.22, 1);
                        border-bottom: 0.5px solid var(--border);
                    }
                    /* Show toolbar when body has 'hovering' class or when settings are open */
                    body.hovering #toolbar, body.settings-open #toolbar {
                        transform: translateY(0);
                    }
                    
                    /* Sidebar z-index adjustment if needed, but 9999 is fine */

                    .toolbar-group { display: flex; align-items: center; gap: 8px; }
                    .toolbar-group:nth-child(1) { justify-self: start; }
                    .toolbar-group:nth-child(2) { justify-self: center; }
                    .toolbar-group:nth-child(3) { justify-self: end; }

                    .icon-btn {
                        width: 32px; height: 32px; background: transparent; border: none;
                        padding: 6px; cursor: pointer; display: flex; align-items: center;
                        justify-content: center; border-radius: 6px; transition: all 0.2s ease; opacity: 0.8;
                    }
                    .icon-btn:hover { opacity: 1; background: var(--hover-bg); }
                    .icon-btn:active { transform: scale(0.95); }

                    .feather { width: 20px; height: 20px; fill: none; stroke: var(--icon-stroke); stroke-width: 1.5px; }

                    #page-info {
                        font-size: 11px; color: var(--text); opacity: 0.6; margin: 0 8px; 
                        font-weight: 500; font-family: -apple-system, sans-serif; cursor: default; white-space: nowrap;
                    }
                    
                    /* 极致紧贴边框 */
                    #content { position: absolute; top: 0; bottom: 0; left: 0; right: 0; overflow: hidden; }

                    #reader-wrapper {
                        width: 100%; height: 100%; overflow-x: hidden; overflow-y: hidden;
                        scroll-behavior: smooth; outline: none;
                        user-select: none; /* Prevent accidental text drag when trying to pan */
                    }
                    
                    /* 四周保持极其紧凑的边距 (4px) 几乎挨着边框 */
                    #reader-text {
                        height: 100%; width: 100%; column-fill: auto;
                        padding-top: 4px; padding-bottom: 4px; box-sizing: border-box;
                    }
                    
                    #jump-input {
                        width: 40px; height: 22px; background: transparent; color: var(--text);
                        border: 1px solid rgba(128,128,128, 0.4); border-radius: 6px;
                        text-align: center; font-size: 11px; font-weight: 500; opacity: 0.7;
                    }
                    #jump-input:focus { opacity: 1; outline: none; border-color: var(--text); }
                    
                    .chapter { break-before: column; }
                    .page-content { padding: 0; margin: 0; width: 100%; box-sizing: border-box; }
                    h1, h2, h3, h4, h5, h6 { break-inside: avoid; break-after: avoid; }
                    p { line-height: 1.6; margin-top: 0; margin-bottom: 0; text-indent: 1.5em; text-align: justify; font-size: var(--font-size); letter-spacing: 0.02em; }
                    img { max-width: 100%; max-height: 80vh; height: auto; display: block; margin: 20px auto; border-radius: 8px; break-inside: avoid; }
                    
                    ::-webkit-scrollbar { display: none !important; }

                    #sidebar {
                        position: fixed; top: 0; left: 0; bottom: 0; width: 280px;
                        background: var(--sidebar-bg); backdrop-filter: blur(20px);
                        border-right: 0.5px solid var(--border); transform: translateX(-100%); 
                        transition: transform 0.3s cubic-bezier(0.19, 1, 0.22, 1);
                        z-index: 9999; display: flex; flex-direction: column; padding-top: 0;
                    }
                    #sidebar.open { transform: translateX(0); }
                    .sidebar-header { position: relative; height: 50px; padding: 0 40px 0 20px; border-bottom: 0.5px solid var(--border); display: flex; align-items: center; }
                    .sidebar-title { font-weight: 600; font-size: 16px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; flex: 1; }
                    #btn-close-sidebar { position: absolute; right: 15px; width: 40px; height: 50px; display: flex; align-items: center; justify-content: center; background: transparent; border: none; cursor: pointer; }
                    #btn-close-sidebar svg { width: 18px; height: 18px; stroke: var(--text); stroke-width: 2px; fill: none; opacity: 0.7; }
                    
                    .toc-list { flex: 1; overflow-y: auto; padding: 10px 0; }
                    .toc-item { padding: 10px 20px; font-size: 13px; cursor: pointer; color: var(--text); opacity: 0.85; border-left: 2px solid transparent; transition: all 0.2s; }
                    .toc-item:hover { background: var(--hover-bg); opacity: 1; border-left-color: var(--icon-stroke); }
                    #sidebar-backdrop { position: absolute; inset: 0; background: rgba(0,0,0,0.3); z-index: 9000; backdrop-filter: blur(2px); display: none; }
                    #sidebar.open + #sidebar-backdrop { display: block; }
                    
                    /* --- Search Sidebar --- */
                    #search-sidebar {
                        position: fixed; top: 0; right: 0; bottom: 0; width: 320px;
                        background: var(--sidebar-bg); backdrop-filter: blur(20px);
                        border-left: 0.5px solid var(--border); transform: translateX(100%); 
                        transition: transform 0.3s cubic-bezier(0.19, 1, 0.22, 1);
                        z-index: 9999; display: flex; flex-direction: column; padding-top: 0;
                    }
                    #search-sidebar.open { transform: translateX(0); }
                    
                    .search-header { 
                        padding: 15px; border-bottom: 0.5px solid var(--border); 
                        display: flex; gap: 8px; align-items: center; 
                    }
                    #search-input {
                        flex: 1; height: 32px; border-radius: 6px; border: 1px solid var(--border);
                        background: rgba(128,128,128, 0.1); color: var(--text); padding: 0 10px;
                        font-size: 13px; outline: none; transition: all 0.2s;
                    }
                    #search-input:focus { background: var(--bg); border-color: var(--icon-stroke); }
                    
                    #search-results { flex: 1; overflow-y: auto; padding: 10px 0; }
                    .search-result-item {
                        padding: 12px 20px; cursor: pointer; border-bottom: 0.5px solid rgba(128,128,128, 0.1);
                        transition: background 0.2s;
                    }
                    .search-result-item:hover { background: var(--hover-bg); }
                    .search-result-title { font-size: 14px; font-weight: 600; margin-bottom: 4px; color: var(--text); }
                    .search-result-snippet { font-size: 12px; color: var(--text); opacity: 0.7; line-height: 1.4; }
                    .search-highlight { background-color: #ffeb3b; color: #000; border-radius: 2px; box-shadow: 0 0 2px rgba(0,0,0,0.2); }
                    .search-match { font-weight: bold; color: var(--icon-stroke); background: rgba(255, 235, 59, 0.3); }


                    /* --- Settings Popover --- */
                    #settings-popover {
                        position: fixed; top: 65px; right: 20px; width: 260px;
                        background: var(--sidebar-bg); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px);
                        border: 0.5px solid var(--border); border-radius: 12px;
                        box-shadow: 0 10px 30px rgba(0,0,0,0.1); z-index: 1003;
                        transform: translateY(-10px); opacity: 0; pointer-events: none;
                        transition: all 0.2s cubic-bezier(0.19, 1, 0.22, 1);
                        padding: 16px; display: flex; flex-direction: column; gap: 16px;
                    }
                    #settings-popover.open {
                        transform: translateY(0); opacity: 1; pointer-events: auto;
                    }
                    .settings-row { display: flex; flex-direction: column; gap: 8px; }
                    .settings-label { font-size: 12px; font-weight: 600; color: var(--text); opacity: 0.6; text-transform: uppercase; letter-spacing: 0.5px; }
                    .theme-picker { display: flex; gap: 10px; }
                    .theme-btn {
                        flex: 1; height: 36px; border-radius: 18px; border: 1px solid var(--border); cursor: pointer;
                        display: flex; align-items: center; justify-content: center; font-size: 13px;
                    }
                    .theme-btn[data-value="white"] { background: #ffffff; color: #000; }
                    .theme-btn[data-value="sepia"] { background: #fbf0d9; color: #5f4b32; }
                    .theme-btn[data-value="dark"] { background: #1e1e1e; color: #d4d4d4; }
                    .font-picker { display: flex; background: var(--hover-bg); border-radius: 8px; padding: 2px; }
                    .font-btn {
                        flex: 1; height: 32px; border: none; background: transparent; color: var(--text);
                        border-radius: 6px; cursor: pointer; font-size: 13px; transition: background 0.2s;
                    }
                    .font-btn.active { background: var(--bg); box-shadow: 0 2px 5px rgba(0,0,0,0.05); font-weight: 500; }

                </style>
            </head>
            <body>
                <div id="toolbar">
                    <div class="toolbar-group">
                        <button id="btn-chapters" class="icon-btn" title="目录"><svg class="feather" viewBox="0 0 24 24"><line x1="3" y1="12" x2="21" y2="12"></line><line x1="3" y1="6" x2="21" y2="6"></line><line x1="3" y1="18" x2="21" y2="18"></line></svg></button>
                        <button id="btn-open" class="icon-btn" title="打开文件"><svg class="feather" viewBox="0 0 24 24"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path></svg></button>
                    </div>
                    <div class="toolbar-group">
                        <input type="number" id="jump-input" placeholder="#">
                        <span id="page-info">-- / --</span>
                    </div>
                    <div class="toolbar-group">
                        <button id="btn-search" class="icon-btn" title="搜索"><svg class="feather" viewBox="0 0 24 24"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg></button>
                        <button id="btn-settings" class="icon-btn" title="设置"><svg class="feather" viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"></circle><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path></svg></button>
                    </div>
                </div>

                <div id="settings-popover">
                    <div class="settings-row">
                        <div class="settings-label">Theme</div>
                        <div class="theme-picker">
                            <button class="theme-btn" data-value="white">White</button>
                            <button class="theme-btn" data-value="sepia">Sepia</button>
                            <button class="theme-btn" data-value="dark">Dark</button>
                        </div>
                    </div>
                    <div class="settings-row">
                        <div class="settings-label">Font Family</div>
                        <div class="font-picker">
                            <button class="font-btn active" data-value="sans">Sans-Serif</button>
                            <button class="font-btn" data-value="serif">Serif</button>
                        </div>
                    </div>
                    <div class="settings-row">
                        <div class="settings-label">Font Size</div>
                        <div style="display: flex; gap: 10px;">
                            <button id="btn-zoom-out" class="theme-btn" style="background: var(--hover-bg); border: none; color: var(--text);">A-</button>
                            <button id="btn-zoom-in" class="theme-btn" style="background: var(--hover-bg); border: none; color: var(--text);">A+</button>
                        </div>
                    </div>
                </div>
                
                <div id="sidebar">
                    <div class="sidebar-header"><span class="sidebar-title">目录</span><button id="btn-close-sidebar"><svg viewBox="0 0 24 24"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg></button></div>
                    <div class="toc-list">$tocListHtml</div>
                </div>
                <div id="sidebar-backdrop"></div>

                <div id="search-sidebar">
                    <div class="search-header">
                        <input type="text" id="search-input" placeholder="全文搜索...">
                        <button id="btn-close-search" class="icon-btn"><svg class="feather" viewBox="0 0 24 24"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg></button>
                    </div>
                    <div id="search-results"></div>
                </div>

                <div id="content">
                    <div id="reader-wrapper" tabindex="0">
                        <div id="reader-text">$contentHtml</div>
                    </div>
                </div>
                
                <script>
                    const wrapper = document.getElementById('reader-wrapper');
                    const textContainer = document.getElementById('reader-text');
                    const pageInfo = document.getElementById('page-info');
                    const jumpInput = document.getElementById('jump-input');
                    const sidebar = document.getElementById('sidebar');
                    const backdrop = document.getElementById('sidebar-backdrop');
                    
                    // 状态锁
                    let isResizing = false;
                    let resizeTimer = null;
                    let saveTimeout = null;
                    
                    // 影子追踪器：记录当前视野中“最关键”的那个元素索引
                    let currentAnchorIndex = 0;
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
                         document.getElementById('btn-open').addEventListener('click', () => { if(window.readerBridge) window.readerBridge.openFile(); });
                         jumpInput.addEventListener('keydown', (e) => { if(e.key === 'Enter') manualJump(); });
                         
                         document.getElementById('btn-settings').addEventListener('click', (e) => {
                             e.stopPropagation();
                             settingsPopover.classList.toggle('open');
                             if (settingsPopover.classList.contains('open')) {
                                 document.body.classList.add('settings-open');
                             } else {
                                 document.body.classList.remove('settings-open');
                             }
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
                        const viewWidth = wrapper.clientWidth;
                        // 策略：使用 getBoundingClientRect() 准确获取在屏幕中的位置
                        for (let i = 0; i < allElements.length; i++) {
                            const el = allElements[i];
                            const rect = el.getBoundingClientRect();
                            // 如果元素的右边缘大于 0（在屏幕可见区域内或更靠右）
                            if (rect.right > 0) {
                                currentAnchorIndex = i;
                                break;
                            }
                        }
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
                            }
                            
                            // B. 拖动中：实时计算但不乱跳
                            if(resizeTimer) clearTimeout(resizeTimer);
                            
                            // 更新布局列宽
                            updateLayout(width);

                            // 强行纠偏：找到原来的主角在新舞台的位置
                            const anchorEl = allElements[currentAnchorIndex];
                            if (anchorEl) {
                                const newWidth = wrapper.clientWidth;
                                // 使用 getBoundingClientRect 计算元素当前偏离视口的距离
                                const rect = anchorEl.getBoundingClientRect();
                                // 当前的 scrollLeft 加上元素相对于视口的左偏移，得到元素在整个滚动区域的绝对左坐标
                                const absoluteLeft = wrapper.scrollLeft + rect.left;
                                // 它应该在哪一页？
                                const targetLeft = Math.floor(absoluteLeft / (newWidth || 1)) * newWidth;
                                // 瞬移过去
                                wrapper.scrollTo(targetLeft, 0);
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

                    function saveToBridge() {
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
                        const idx = Math.round(wrapper.scrollLeft / w);
                        wrapper.scrollTo({ left: (idx + 1) * w, behavior: 'smooth' });
                    }
                    function navPrev() { 
                        const w = wrapper.clientWidth;
                        const idx = Math.round(wrapper.scrollLeft / w);
                        wrapper.scrollTo({ left: (idx - 1) * w, behavior: 'smooth' });
                    }
                    
                    function manualJump() {
                        const val = parseInt(jumpInput.value);
                        if (val > 0) {
                            const w = wrapper.clientWidth;
                            wrapper.scrollTo({ left: (val-1) * w, behavior: 'auto' });
                            jumpInput.value = ''; jumpInput.blur();
                        }
                    }

                    function toggleSidebar() { sidebar.classList.toggle('open'); }
                    function scrollToId(id) {
                         const el = document.getElementById(id);
                         if(el) { 
                             el.scrollIntoView(); 
                             if(sidebar.classList.contains('open')) toggleSidebar(); 
                         }
                    }
                    
                    function updateProgress() {
                        if (isResizing) return;
                        const w = wrapper.clientWidth;
                        const scrollW = wrapper.scrollWidth;
                        if(w > 0) {
                             const current = Math.round(wrapper.scrollLeft / w) + 1;
                             const total = Math.ceil(scrollW / w) || 1;
                             const maxScroll = scrollW - w;
                             const pct = maxScroll > 0 ? Math.round((wrapper.scrollLeft / maxScroll) * 100) : 0;
                             const text = current + ' / ' + total + ' (' + pct + '%)';
                             if(pageInfo) pageInfo.textContent = text;
                        }
                    }
                    
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
                             (document.activeElement.tagName === 'INPUT' || document.activeElement.tagName === 'TEXTAREA')) {
                              if (e.key === 'Enter' && document.activeElement === jumpInput) {
                                  // Handled elsewhere
                              }
                              return;
                          }

                          const k = e.key;
                          const lowerK = k.toLowerCase();
                          
                          // Map A/D and Arrow Keys to Pagination
                          if (lowerK === 'a' || k === 'ArrowLeft') {
                              e.preventDefault();
                              navPrev();
                          } else if (lowerK === 'd' || k === 'ArrowRight') {
                              e.preventDefault();
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
                        root.style.setProperty('--font-size', (currentSize + 2) + 'px');
                        setTimeout(() => { updateLayout(); findCurrentAnchor(); }, 100);
                    };

                    window.readerZoomOut = function() {
                        const root = document.documentElement;
                        const currentSize = parseInt(getComputedStyle(root).getPropertyValue('--font-size')) || 16;
                        if(currentSize > 10) {
                            root.style.setProperty('--font-size', (currentSize - 2) + 'px');
                            setTimeout(() => { updateLayout(); findCurrentAnchor(); }, 100);
                        }
                    };

                    window.readerRestoreTheme = function(t) {
                        if (t) document.documentElement.setAttribute('data-theme', t);
                    };

                    window.readerRestoreFontFamily = function(f) {
                        if (f) {
                            const btn = document.querySelector(`.font-btn[data-value="${'$'}{f}"]`);
                            if (btn) btn.click();
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

                    // Bind Search Events
                    document.getElementById('btn-search').addEventListener('click', toggleSearchSidebar);
                    document.getElementById('btn-close-search').addEventListener('click', toggleSearchSidebar);
                    searchInput.addEventListener('keydown', (e) => { if(e.key === 'Enter') performSearch(); });

                    function performSearch() {
                        const query = searchInput.value.trim();
                        if (!query) return;

                        // 1. Clear previous
                        clearHighlights();
                        searchResults.innerHTML = '<div style="padding:20px;text-align:center;opacity:0.5">搜索中...</div>';
                        searchMatches = [];

                        // 2. Find matches (Text Node Traversal)
                        // delayed to allow UI update
                        setTimeout(() => {
                            const regex = new RegExp(escapeRegex(query), 'gi');
                            const walker = document.createTreeWalker(textContainer, NodeFilter.SHOW_TEXT, null, false);
                            let node;
                            const nodesToReplace = [];

                            while(node = walker.nextNode()) {
                                if (node.parentNode.tagName === 'SCRIPT' || node.parentNode.tagName === 'STYLE') continue;
                                if (regex.test(node.nodeValue)) {
                                    nodesToReplace.push(node);
                                }
                                regex.lastIndex = 0; // reset
                            }

                            // 3. Highlight and Collect
                            let matchCount = 0;
                            nodesToReplace.forEach(textNode => {
                                const text = textNode.nodeValue;
                                const parent = textNode.parentNode;
                                const frag = document.createDocumentFragment();
                                let lastIdx = 0;
                                let match;
                                regex.lastIndex = 0;

                                while ((match = regex.exec(text)) !== null) {
                                    // Append text before match
                                    frag.appendChild(document.createTextNode(text.substring(lastIdx, match.index)));
                                    
                                    // Create highlight span
                                    const span = document.createElement('span');
                                    span.className = 'search-highlight';
                                    span.id = 'search-match-' + matchCount;
                                    span.textContent = match[0];
                                    frag.appendChild(span);
                                    
                                    // Collect result for sidebar
                                    // Get snippet: 20 chars before and after
                                    const start = Math.max(0, match.index - 20);
                                    const end = Math.min(text.length, match.index + match[0].length + 20);
                                    const snippet = text.substring(start, end).replace(match[0], `<span class="search-match">${'$'}{match[0]}</span>`);
                                    
                                    searchMatches.push({
                                        id: 'search-match-' + matchCount,
                                        text: '... ' + snippet + ' ...',
                                        // Try to find a chapter title? simple for now
                                    });

                                    lastIdx = match.index + match[0].length;
                                    matchCount++;
                                }
                                // Append remaining text
                                frag.appendChild(document.createTextNode(text.substring(lastIdx)));
                                parent.replaceChild(frag, textNode);
                            });

                            renderSearchResults(matchCount);
                        }, 50);
                    }

                    function clearHighlights() {
                        // Crucial: Restore original text nodes to avoid DOM explosion on repeated searches
                        // Simple cleanup: remove spans, keep text. 
                        // Note: normalize() joins adjacent text nodes back together.
                        const highlights = textContainer.querySelectorAll('.search-highlight');
                        highlights.forEach(span => {
                            const parent = span.parentNode;
                            parent.replaceChild(document.createTextNode(span.textContent), span);
                            parent.normalize();
                        });
                        searchResults.innerHTML = '';
                    }

                    function renderSearchResults(count) {
                        if (count === 0) {
                            searchResults.innerHTML = '<div style="padding:20px;text-align:center;opacity:0.5">未找到结果</div>';
                            return;
                        }
                        
                        let html = `<div style="padding:10px 20px;font-size:12px;opacity:0.6">找到 ${'$'}{count} 个结果</div>`;
                        searchMatches.forEach((m, i) => {
                            html += `
                                <div class="search-result-item" onclick="jumpToMatch('${'$'}{m.id}')">
                                    <div class="search-result-title">结果 ${'$'}{i+1}</div>
                                    <div class="search-result-snippet">${'$'}{m.text}</div>
                                </div>
                            `;
                        });
                        searchResults.innerHTML = html;
                    }

                    window.jumpToMatch = function(id) {
                         const el = document.getElementById(id);
                         if(el) {
                             const w = wrapper.clientWidth;
                             const rect = el.getBoundingClientRect();
                             const absoluteLeft = wrapper.scrollLeft + rect.left;
                             // Calculate the start of the column/page
                             const targetScroll = Math.floor(absoluteLeft / w) * w;
                             wrapper.scrollTo({ left: targetScroll, behavior: 'auto' });
                             
                             // Flash effect (background color)
                             const oldBg = el.style.backgroundColor;
                             el.style.transition = 'background-color 0.5s ease';
                             el.style.backgroundColor = '#ff9800'; // Orange flash
                             
                             setTimeout(() => {
                                 el.style.backgroundColor = oldBg || ''; 
                             }, 500);
                             
                             // Close sidebar on mobile/if preferred, but keeping open is usually better for "Next/Prev" feeling
                             if (window.innerWidth < 600) toggleSearchSidebar();
                         }
                    };

                    function escapeRegex(string) {
                        return string.replace(/[.*+?^%${'$'}{}()|[\]\\]/g, '\\${'$'}&'); // ${'$'}& means the whole matched string
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    fun loadEpub(file: File, isDarcula: Boolean, fontSize: Int): String {
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
        return getAppHtml(mapTocToSpineIds(tocItems, book), sb.toString(), colors, fontSize)
    }

    fun getWelcomeHtml(isDarcula: Boolean, fontSize: Int): String {
        val colors = if (isDarcula) ThemeColors("#2b2d30", "#aaa", "rgba(43, 45, 48, 0.9)", "#4e5254", "#4c5052")
        else ThemeColors("#fff", "#333", "rgba(255, 255, 255, 0.9)", "#ddd", "#eee")
        return getAppHtml(emptyList(), "<div style='display:flex;height:100%;justify-content:center;align-items:center;opacity:0.5;'><h2>Click ? to Open</h2></div>", colors, fontSize)
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