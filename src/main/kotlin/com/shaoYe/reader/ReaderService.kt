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
import com.intellij.ui.jcef.JBCefJSQuery
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
        
        fun getInstance(project: Project): ReaderService = project.getService(ReaderService::class.java)
    }

    fun initBrowser(jbCefBrowser: JBCefBrowser) {
        this.browser = jbCefBrowser
        
        // 1. OPEN FILE QUERY
        openFileQuery = JBCefJSQuery.create(jbCefBrowser)
        openFileQuery?.addHandler { _ ->
            SwingUtilities.invokeLater { openFileChooser() }
            JBCefJSQuery.Response("OK")
        }
        
        // 2. SAVE PROGRESS QUERY
        saveProgressQuery = JBCefJSQuery.create(jbCefBrowser)
        saveProgressQuery?.addHandler { progressStr ->
            // Save progress (0.0 to 1.0, or page index)
            // User requested "Restore Page". Let's assume the string is the scrollLeft value or percentage.
            // Actually, best to save the raw value passed from JS.
            try {
                PropertiesComponent.getInstance(project).setValue(KEY_LAST_PROGRESS, progressStr)
            } catch (e: Exception) {}
            JBCefJSQuery.Response("OK")
        }
        
        jbCefBrowser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                // Reinject on reload
                injectJsBridge(browser)
                
                // RESTORE PROGRESS
                restoreProgress(browser)
            }
        }, jbCefBrowser.cefBrowser)
        
        // AUTO-LOAD LAST BOOK
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
        val js = """
            window.readerBridge = {
                openFile: function() { ${openFileQuery?.inject("''")} },
                saveProgress: function(val) { ${saveProgressQuery?.inject("val")} }
            };
        """.trimIndent()
        browser?.executeJavaScript(js, browser?.url, 0)
    }
    
    private fun restoreProgress(browser: CefBrowser?) {
        val lastProgress = PropertiesComponent.getInstance(project).getValue(KEY_LAST_PROGRESS)
        if (!lastProgress.isNullOrEmpty()) {
             // Execute JS to restore.
             // We wait a tiny bit for layout? Or just execute. 
             // "window.readerRestore(val)" must be defined in EpubParser HTML.
             browser?.executeJavaScript("if(window.readerRestore) window.readerRestore('$lastProgress');", browser?.url, 0)
        }
    }

    fun openFileChooser() {
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withTitle("打开书籍 (Open Book)")
            .withFileFilter { it.extension?.equals("epub", ignoreCase = true) == true }

        val virtualFile = FileChooser.chooseFile(descriptor, project, null)
        if (virtualFile != null) {
            val file = File(virtualFile.path)
            // SAVE PATH
            PropertiesComponent.getInstance(project).setValue(KEY_LAST_PATH, file.absolutePath)
            loadEpub(file)
        }
    }

    fun loadEpub(file: File) {
        if (browser == null) return
        
        // BACKGROUND TASK (Fix "SlowOperations on EDT" & "Write-unsafe context")
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Loading Book...", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Parsing EPUB..."
                    val isDarcula = com.intellij.util.ui.StartupUiUtil.isUnderDarcula
                    val htmlContent = EpubParser.loadEpub(file, isDarcula)
                    
                    // UPDATE UI ON EDT
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
    
    // Helper wrappers
    fun nextPage() = browser?.cefBrowser?.executeJavaScript("window.readerNext()", null, 0)
    fun prevPage() = browser?.cefBrowser?.executeJavaScript("window.readerPrev()", null, 0)
    fun zoomIn() = browser?.cefBrowser?.executeJavaScript("window.readerZoomIn()", null, 0)
    fun zoomOut() = browser?.cefBrowser?.executeJavaScript("window.readerZoomOut()", null, 0)
}
