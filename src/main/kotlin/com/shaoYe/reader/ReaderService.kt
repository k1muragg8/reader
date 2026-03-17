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

    companion object {
        private const val KEY_LAST_PATH = "READER_MASTER_LAST_PATH"
        private const val KEY_LAST_PROGRESS = "READER_MASTER_LAST_PROGRESS"
        private const val KEY_LAST_THEME = "READER_MASTER_LAST_THEME"
        private const val KEY_LAST_FONT_FAMILY = "READER_MASTER_LAST_FONT_FAMILY"

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

        jbCefBrowser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                injectJsBridge(browser)
                restoreProgress(browser)
                
                // Inject initial font size validation
                val scheme = EditorColorsManager.getInstance().globalScheme
                updateFontSize(scheme.editorFontSize)

                // Mark as ready to save progress after a short delay to ensure DOM is fully laid out
                browser?.executeJavaScript("setTimeout(() => { window.isReadyToSave = true; }, 500);", browser.url, 0)
            }
        }, jbCefBrowser.cefBrowser)

        // Listen for font size changes
        val connection = project.messageBus.connect()
        connection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener {
             val scheme = EditorColorsManager.getInstance().globalScheme
             updateFontSize(scheme.editorFontSize)
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

    private fun injectJsBridge(browser: CefBrowser?) {
        if (browser == null) return // 增加空校验，消除后续冗余安全调用警告
        val js = """
            window.readerBridge = {
                openFile: function() { ${openFileQuery?.inject("''")} },
                saveProgress: function(val) { ${saveProgressQuery?.inject("val")} },
                saveTheme: function(val) { ${saveThemeQuery?.inject("val")} },
                saveFontFamily: function(val) { ${saveFontFamilyQuery?.inject("val")} }
            };
        """.trimIndent()
        // 修复：既然 browser 已校验不为空，此处不再使用冗余的 ? 号
        browser.executeJavaScript(js, browser.url, 0)
    }

    private fun restoreProgress(browser: CefBrowser?) {
        if (browser == null) return
        val props = PropertiesComponent.getInstance(project)
        val lastProgress = props.getValue(KEY_LAST_PROGRESS)
        val lastTheme = props.getValue(KEY_LAST_THEME)
        val lastFont = props.getValue(KEY_LAST_FONT_FAMILY)

        val jsBuilder = StringBuilder()
        if (!lastProgress.isNullOrEmpty()) {
            jsBuilder.append("if(window.readerRestore) window.readerRestore('$lastProgress');\n")
        }
        if (!lastTheme.isNullOrEmpty()) {
            jsBuilder.append("if(window.readerRestoreTheme) window.readerRestoreTheme('$lastTheme');\n")
        }
        if (!lastFont.isNullOrEmpty()) {
            jsBuilder.append("if(window.readerRestoreFontFamily) window.readerRestoreFontFamily('$lastFont');\n")
        }

        if (jsBuilder.isNotEmpty()) {
            browser.executeJavaScript(jsBuilder.toString(), browser.url, 0)
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
                    val htmlContent = EpubParser.loadEpub(file, isDarcula, scheme.editorFontSize)

                    ApplicationManager.getApplication().invokeLater {
                        browser?.loadHTML(htmlContent, "http://readermaster/")
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
}