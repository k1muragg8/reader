package com.shaoYe.reader

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.JBColor
import java.awt.BorderLayout
import javax.swing.JPanel

class EpubToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        println("Plugin Reader Master successfully loaded!")

        val readerService = ReaderService.getInstance(project)

        // 1. Initialize Browser
        val browser = JBCefBrowser()

        // 2. Setup Service & Bridge
        readerService.initBrowser(browser)

        // 3. Create Main Panel (No Toolbar!)
        val panel = JPanel(BorderLayout())
        panel.add(browser.component, BorderLayout.CENTER)

        // --- 核心修复：判定深色模式 ---
        // 使用 !JBColor.isBright() 替代 UIUtil.isUnderDarcula()
        val isDarcula = !JBColor.isBright()
        val scheme = com.intellij.openapi.editor.colors.EditorColorsManager.getInstance().globalScheme

        val props = com.intellij.ide.util.PropertiesComponent.getInstance(project)
        val savedTheme = props.getValue("READER_MASTER_LAST_THEME")
        val savedFontFamily = props.getValue("READER_MASTER_LAST_FONT_FAMILY")
        val savedFontSizeStr = props.getValue("READER_MASTER_LAST_FONT_SIZE")
        val fontSize = savedFontSizeStr?.toIntOrNull() ?: (scheme.editorFontSize - 2)

        browser.loadHTML(EpubParser.getWelcomeHtml(isDarcula, fontSize, savedTheme, savedFontFamily))

        toolWindow.setTitleActions(
            listOf(
                ToggleTocAction(),
                OpenFileAction(),
                ToggleSearchAction(),
                ToggleSettingsAction(),
                ShowProgressAction()
            )
        )

        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}