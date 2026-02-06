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

    companion object {
        private const val KEY_LAST_PATH = "READER_MASTER_LAST_PATH"
        private const val KEY_LAST_PROGRESS = "READER_MASTER_LAST_PROGRESS"

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

        jbCefBrowser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                injectJsBridge(browser)
                restoreProgress(browser)
            }
        }, jbCefBrowser.cefBrowser)

        autoLoadLastBook()
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
                saveProgress: function(val) { ${saveProgressQuery?.inject("val")} }
            };
        """.trimIndent()
        // 修复：既然 browser 已校验不为空，此处不再使用冗余的 ? 号
        browser.executeJavaScript(js, browser.url, 0)
    }

    private fun restoreProgress(browser: CefBrowser?) {
        if (browser == null) return
        val lastProgress = PropertiesComponent.getInstance(project).getValue(KEY_LAST_PROGRESS)
        if (!lastProgress.isNullOrEmpty()) {
            browser.executeJavaScript("if(window.readerRestore) window.readerRestore('$lastProgress');", browser.url, 0)
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
                    val htmlContent = EpubParser.loadEpub(file, isDarcula)

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