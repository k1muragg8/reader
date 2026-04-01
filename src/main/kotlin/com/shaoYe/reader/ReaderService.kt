package com.shaoYe.reader

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.ui.JBColor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.io.File
import javax.swing.SwingUtilities

@Service(Service.Level.PROJECT)
class ReaderService(private val project: Project) {

    var browser: JBCefBrowser? = null

    // JS Queries
    private var openFileQuery: JBCefJSQuery? = null
    private var saveProgressQuery: JBCefJSQuery? = null
    private var saveThemeQuery: JBCefJSQuery? = null
    private var saveFontFamilyQuery: JBCefJSQuery? = null
    private var saveFontSizeQuery: JBCefJSQuery? = null
    private var searchResultsQuery: JBCefJSQuery? = null

    private var progressInfoQuery: JBCefJSQuery? = null

    // State
    private var currentSearchDialog: SearchDialog? = null
    private var currentTocItems: List<EpubParser.TocItem> = emptyList()
    var progressCallback: ((String) -> Unit)? = null

    companion object {
        private const val KEY_LAST_PATH = "READER_MASTER_LAST_PATH"
        private const val KEY_LAST_PROGRESS = "READER_MASTER_LAST_PROGRESS"
        private const val KEY_LAST_THEME = "READER_MASTER_LAST_THEME"
        private const val KEY_LAST_FONT_FAMILY = "READER_MASTER_LAST_FONT_FAMILY"
        private const val KEY_LAST_FONT_SIZE = "READER_MASTER_LAST_FONT_SIZE"

        fun getInstance(project: Project): ReaderService {
            return project.getService(ReaderService::class.java)
        }
    }

    fun initBrowser(jbCefBrowser: JBCefBrowser) {
        this.browser = jbCefBrowser

        // 核心修复：JBCefJSQuery 弃用警告处理
        openFileQuery = JBCefJSQuery.create(jbCefBrowser as JBCefBrowserBase)
        openFileQuery?.addHandler { _ ->
            SwingUtilities.invokeLater { openFileChooser() }
            JBCefJSQuery.Response("OK")
        }

        saveProgressQuery = JBCefJSQuery.create(jbCefBrowser as JBCefBrowserBase)
        saveProgressQuery?.addHandler { progressStr ->
            try {
                PropertiesComponent.getInstance(project).setValue(KEY_LAST_PROGRESS, progressStr)
            } catch (e: Exception) {}
            JBCefJSQuery.Response("OK")
        }

        saveThemeQuery = JBCefJSQuery.create(jbCefBrowser as JBCefBrowserBase)
        saveThemeQuery?.addHandler { themeStr ->
            try {
                PropertiesComponent.getInstance(project).setValue(KEY_LAST_THEME, themeStr)
            } catch (e: Exception) {}
            JBCefJSQuery.Response("OK")
        }

        saveFontFamilyQuery = JBCefJSQuery.create(jbCefBrowser as JBCefBrowserBase)
        saveFontFamilyQuery?.addHandler { fontStr ->
            try {
                PropertiesComponent.getInstance(project).setValue(KEY_LAST_FONT_FAMILY, fontStr)
            } catch (e: Exception) {}
            JBCefJSQuery.Response("OK")
        }

        saveFontSizeQuery = JBCefJSQuery.create(jbCefBrowser as JBCefBrowserBase)
        saveFontSizeQuery?.addHandler { sizeStr ->
            try {
                PropertiesComponent.getInstance(project).setValue(KEY_LAST_FONT_SIZE, sizeStr)
            } catch (e: Exception) {}
            JBCefJSQuery.Response("OK")
        }

        searchResultsQuery = JBCefJSQuery.create(jbCefBrowser as JBCefBrowserBase)
        searchResultsQuery?.addHandler { resultsStr ->
            if (resultsStr.isNotEmpty() && resultsStr != "NONE") {
                if (resultsStr.startsWith("error|||")) {
                    val errorMsg = resultsStr.removePrefix("error|||")
                    currentSearchDialog?.updateResults(listOf(Pair("error", "Error: $errorMsg")))
                } else if (resultsStr.startsWith("info|||")) {
                    val infoMsg = resultsStr.removePrefix("info|||")
                    currentSearchDialog?.updateResults(listOf(Pair("info", "DIAGNOSTIC: $infoMsg")))
                } else {
                    val items = resultsStr.split("|||")
                    val resultsList = items.chunked(2).mapNotNull {
                        if (it.size == 2) Pair(it[0], it[1]) else null
                    }
                    currentSearchDialog?.updateResults(resultsList)
                }
            } else {
                currentSearchDialog?.updateResults(emptyList())
            }
            JBCefJSQuery.Response("OK")
        }

        progressInfoQuery = JBCefJSQuery.create(jbCefBrowser as JBCefBrowserBase)
        progressInfoQuery?.addHandler { infoStr ->
            progressCallback?.invoke(infoStr)
            JBCefJSQuery.Response("OK")
        }

        jbCefBrowser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain == true) {
                    restoreProgress(browser)
                    
                    val props = PropertiesComponent.getInstance(project)
                    val savedFontSizeStr = props.getValue(KEY_LAST_FONT_SIZE)
                    val scheme = EditorColorsManager.getInstance().globalScheme
                    val fontSize = savedFontSizeStr?.toIntOrNull() ?: scheme.editorFontSize
                    updateFontSize(fontSize)

                    // Mark as ready to save progress after a short delay to ensure DOM is fully laid out
                    browser?.executeJavaScript("setTimeout(function() { window.isReadyToSave = true; }, 500);", browser.url, 0)
                }
            }
        }, jbCefBrowser.cefBrowser)

        // Listen for font size changes
        val connection = project.messageBus.connect()
        connection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener {
             val props = PropertiesComponent.getInstance(project)
             val savedFontSizeStr = props.getValue(KEY_LAST_FONT_SIZE)
             val scheme = EditorColorsManager.getInstance().globalScheme
             val fontSize = savedFontSizeStr?.toIntOrNull() ?: scheme.editorFontSize
             updateFontSize(fontSize)
        })

        autoLoadLastBook()
    }

    private fun updateFontSize(size: Int) {
        // Update CSS variable
        val js = "document.documentElement.style.setProperty('--font-size', '${size}px');"
        browser?.cefBrowser?.executeJavaScript(js, null, 0)
    }

    private fun autoLoadLastBook() {
        val lastPath = PropertiesComponent.getInstance(project).getValue(KEY_LAST_PATH)
        if (!lastPath.isNullOrEmpty()) {
            val file = File(lastPath)
            if (file.exists()) {
                loadEpub(file)
            }
        }
    }

    fun getBridgeJs(): String? {
        if (openFileQuery == null) return null
        return """
            window.readerBridge = {
                openFile: function() { ${openFileQuery?.inject("''")} },
                saveProgress: function(val) { ${saveProgressQuery?.inject("val")} },
                saveTheme: function(val) { ${saveThemeQuery?.inject("val")} },
                saveFontFamily: function(val) { ${saveFontFamilyQuery?.inject("val")} },
                saveFontSize: function(val) { ${saveFontSizeQuery?.inject("val")} },
                sendSearchResults: function(val) { ${searchResultsQuery?.inject("val")} },
                sendProgressInfo: function(val) { ${progressInfoQuery?.inject("val")} },
                setSearchActive: function(active) { if(active) document.body.classList.add('search-active'); else document.body.classList.remove('search-active'); }
            };
        """.trimIndent()
    }

    private fun restoreProgress(browser: CefBrowser?) {
        if (browser == null) return
        val props = PropertiesComponent.getInstance(project)
        val lastProgress = props.getValue(KEY_LAST_PROGRESS)

        if (!lastProgress.isNullOrEmpty()) {
            browser.executeJavaScript("if(window.readerRestore) window.readerRestore('$lastProgress');\n", browser.url, 0)
        }
    }

    fun openFileChooser() {
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withTitle("Open Book")
            .withFileFilter { it.extension?.equals("epub", ignoreCase = true) == true }

        val virtualFile = FileChooser.chooseFile(descriptor, project, null)
        if (virtualFile != null) {
            val file = File(virtualFile.path)
            PropertiesComponent.getInstance(project).setValue(KEY_LAST_PATH, file.absolutePath)
            loadEpub(file)
        }
    }

    fun loadEpub(file: File) {
        if (browser == null) return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Loading Book...", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Parsing EPUB..."
                    // 使用现代 API 处理主题判定警告
                    val isDarcula = !JBColor.isBright()
                    val scheme = EditorColorsManager.getInstance().globalScheme

                    val props = PropertiesComponent.getInstance(project)
                    val savedTheme = props.getValue(KEY_LAST_THEME)
                    val savedFontFamily = props.getValue(KEY_LAST_FONT_FAMILY)
                    val savedFontSizeStr = props.getValue(KEY_LAST_FONT_SIZE)
                    // The default font size should be the same as the IDE editor default.
                    val fontSize = savedFontSizeStr?.toIntOrNull() ?: scheme.editorFontSize

                    val loadResult = EpubParser.loadEpub(file, isDarcula, fontSize, savedTheme, savedFontFamily, getBridgeJs())
                    currentTocItems = loadResult.toc

                    ApplicationManager.getApplication().invokeLater {
                        browser?.loadHTML(loadResult.html, "http://readermaster/")
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        browser?.loadHTML("<html><body><h1 style='color:red;'>Error: ${e.message}</h1></body></html>")
                    }
                }
            }
        })
    }

    // --- 恢复丢失的功能组件 (Actions.kt 所需) ---
    fun nextPage() = browser?.cefBrowser?.executeJavaScript("window.readerNext()", null, 0)
    fun prevPage() = browser?.cefBrowser?.executeJavaScript("window.readerPrev()", null, 0)
    fun zoomIn() = browser?.cefBrowser?.executeJavaScript("window.readerZoomIn()", null, 0)
    fun zoomOut() = browser?.cefBrowser?.executeJavaScript("window.readerZoomOut()", null, 0)

    // --- Kotlin Dialog Handlers ---
    fun toggleToc() {
        setDialogActive(true)
        TocDialog(project, currentTocItems, this).show()
        // Removed immediate false to prevent content hiding
    }

    fun toggleSearch() {
        if (currentSearchDialog == null || !currentSearchDialog!!.isShowing) {
            setSearchActive(true)
            currentSearchDialog = SearchDialog(project, this)
            currentSearchDialog?.show()
            // We need a way to detect when dialog closes to set search active to false
            // DialogWrapper has a callback or we can check later.
            // For now, let's just keep it active while the dialog reference exists and is showing.
        }
    }

    fun setSearchActive(active: Boolean) {
        browser?.cefBrowser?.executeJavaScript("if(window.setSearchActive) window.setSearchActive($active);", null, 0)
    }

    fun toggleSettings() {
        setDialogActive(true)
        SettingsDialog(project, this).show()
        // Removed immediate false
    }

    fun setDialogActive(active: Boolean) {
        browser?.cefBrowser?.executeJavaScript("if(window.setDialogActive) window.setDialogActive($active);", null, 0)
    }


    // --- Invoked from Native Dialogs ---
    fun setTheme(theme: String) {
        PropertiesComponent.getInstance(project).setValue(KEY_LAST_THEME, theme)
        browser?.cefBrowser?.executeJavaScript("document.documentElement.setAttribute('data-theme', '$theme');", null, 0)
    }

    fun setFontFamily(family: String) {
        PropertiesComponent.getInstance(project).setValue(KEY_LAST_FONT_FAMILY, family)
        val cssVal = if (family == "serif") "Palatino, 'Palatino Linotype', 'Book Antiqua', Georgia, serif" else "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
        browser?.cefBrowser?.executeJavaScript("document.documentElement.style.setProperty('--font-family', \"$cssVal\");", null, 0)
    }

    fun scrollToId(id: String) {
        val escapedId = id.replace("\\", "\\\\").replace("'", "\\'")
        browser?.cefBrowser?.executeJavaScript("if(window.scrollToId) window.scrollToId('$escapedId');", null, 0)
    }

    fun performSearch(query: String) {
        val escapedQuery = query.replace("\\", "\\\\").replace("'", "\\'")
        browser?.cefBrowser?.executeJavaScript("if(window.performSearchFromNative) window.performSearchFromNative('$escapedQuery');", browser?.cefBrowser?.url, 0)
    }

    fun jumpToMatch(id: String) {
        val escapedId = id.replace("\\", "\\\\").replace("'", "\\'")
        browser?.cefBrowser?.executeJavaScript("if(window.jumpToMatch) window.jumpToMatch('$escapedId');", browser?.cefBrowser?.url, 0)
    }

    fun checkBridgeHealth() {
        val js = """
            (function() {
                let status = [];
                status.push("Bridge: " + (typeof window.readerBridge));
                status.push("Search: " + (typeof window.performSearchFromNative));
                status.push("Jump: " + (typeof window.jumpToMatch));
                status.push("Container: " + (!!document.getElementById('reader-text')));
                status.push("URL: " + window.location.href);
                
                if (window.readerBridge && window.readerBridge.sendSearchResults) {
                    window.readerBridge.sendSearchResults("info|||" + status.join(" | "));
                } else {
                    // Fallback if bridge is totally broken
                    console.error("CRITICAL: Bridge sendSearchResults is missing!");
                }
            })();
        """.trimIndent()
        browser?.cefBrowser?.executeJavaScript(js, browser?.cefBrowser?.url, 0)
    }
}