package com.shaoYe.reader

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class TocDialog(project: Project, private val tocItems: List<EpubParser.TocItem>, private val service: ReaderService) : DialogWrapper(project, true) {
    init {
        title = "Table of Contents"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val listModel = DefaultListModel<String>()
        tocItems.forEach { listModel.addElement(it.title) }

        val list = JBList(listModel)
        list.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val idx = list.selectedIndex
                if (idx in tocItems.indices) {
                    service.scrollToId(tocItems[idx].htmlId)
                }
            }
        }

        val scrollPane = JBScrollPane(list)
        scrollPane.preferredSize = Dimension(300, 400)
        return scrollPane
    }

    override fun createActions() = emptyArray<Action>()
}

class ProgressDialog(project: Project) : DialogWrapper(project, false) {
    private val infoLabel = JLabel("Calculating progress...", SwingConstants.CENTER)

    init {
        title = "Reading Progress"
        setResizable(false)
        init()
    }

    fun updateInfo(info: String) {
        SwingUtilities.invokeLater {
            infoLabel.text = info
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(200, 60)
        infoLabel.font = infoLabel.font.deriveFont(16f)
        panel.add(infoLabel, BorderLayout.CENTER)
        return panel
    }

    override fun createActions() = emptyArray<Action>()
}

class SettingsDialog(project: Project, private val service: ReaderService) : DialogWrapper(project, true) {
    init {
        title = "Settings"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        // Theme
        val themePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        themePanel.add(JLabel("Theme:"))
        val btnWhite = JButton("White").apply { addActionListener { service.setTheme("white") } }
        val btnSepia = JButton("Sepia").apply { addActionListener { service.setTheme("sepia") } }
        val btnDark = JButton("Dark").apply { addActionListener { service.setTheme("dark") } }
        themePanel.add(btnWhite)
        themePanel.add(btnSepia)
        themePanel.add(btnDark)

        // Font Family
        val fontPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        fontPanel.add(JLabel("Font:"))
        val btnSans = JButton("Sans-Serif").apply { addActionListener { service.setFontFamily("sans") } }
        val btnSerif = JButton("Serif").apply { addActionListener { service.setFontFamily("serif") } }
        fontPanel.add(btnSans)
        fontPanel.add(btnSerif)

        // Font Size
        val sizePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        sizePanel.add(JLabel("Size:"))
        val btnZoomOut = JButton("A-").apply { addActionListener { service.zoomOut() } }
        val btnZoomIn = JButton("A+").apply { addActionListener { service.zoomIn() } }
        sizePanel.add(btnZoomOut)
        sizePanel.add(btnZoomIn)

        panel.add(themePanel)
        panel.add(fontPanel)
        panel.add(sizePanel)

        return panel
    }

    override fun createActions() = emptyArray<Action>()
}

class SearchDialog(project: Project, private val service: ReaderService) : DialogWrapper(project, false, IdeModalityType.MODELESS) {
    private val listModel = DefaultListModel<String>()
    private val resultIds = mutableListOf<String>()
    private val searchField = JBTextField()



    init {
        title = "Search"
        init()
        setupActions()
    }

    private fun setupActions() {
    }

    fun updateResults(results: List<Pair<String, String>>) {
        SwingUtilities.invokeLater {
            listModel.clear()
            resultIds.clear()
            
            if (results.isEmpty()) {
                listModel.addElement("No results found.")
            } else {
                results.forEach {
                    resultIds.add(it.first)
                    listModel.addElement(it.second.replace(Regex("<.*?>"), ""))
                }
            }
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())

        val topPanel = JPanel(BorderLayout(5, 0))
        topPanel.add(searchField, BorderLayout.CENTER)
        panel.add(topPanel, BorderLayout.NORTH)

        var searchTimer: Timer? = null
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) { scheduleSearch() }
            override fun removeUpdate(e: DocumentEvent?) { scheduleSearch() }
            override fun changedUpdate(e: DocumentEvent?) { scheduleSearch() }

            private fun scheduleSearch() {
                searchTimer?.stop()
                val query = searchField.text
                if (query.isNotBlank()) {
                    listModel.clear()
                    listModel.addElement("Searching...")
                    resultIds.clear()
                }

                searchTimer = Timer(300) {
                    if (searchField.text.isNotBlank()) {
                        service.performSearch(searchField.text)
                    } else {
                        listModel.clear()
                        resultIds.clear()
                        service.performSearch("") 
                    }
                }
                searchTimer?.isRepeats = false
                searchTimer?.start()
            }
        })

        val list = JBList(listModel)
        list.emptyText.text = "Enter keywords to search..."
        list.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val idx = list.selectedIndex
                if (idx in resultIds.indices) {
                    service.jumpToMatch(resultIds[idx])
                }
            }
        }

        val scrollPane = JBScrollPane(list)
        panel.add(scrollPane, BorderLayout.CENTER)

        panel.preferredSize = Dimension(450, 350)
        panel.minimumSize = Dimension(200, 150)

        return panel
    }

    override fun dispose() {
        service.setSearchActive(false)
        super.dispose()
    }

    override fun createActions() = emptyArray<Action>()
}
