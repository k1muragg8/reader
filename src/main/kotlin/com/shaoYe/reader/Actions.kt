package com.shaoYe.reader

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

class NextPageAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { ReaderService.getInstance(it).nextPage() }
    }
}

class PrevPageAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { ReaderService.getInstance(it).prevPage() }
    }
}

class ZoomInAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { ReaderService.getInstance(it).zoomIn() }
    }
}

class ZoomOutAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { ReaderService.getInstance(it).zoomOut() }
    }
}

class OpenFileAction : AnAction("Open Book", "Open an EPUB file", com.intellij.icons.AllIcons.Actions.MenuOpen), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { ReaderService.getInstance(it).openFileChooser() }
    }
}

class ToggleTocAction : AnAction("Chapters", "Toggle chapters sidebar", com.intellij.icons.AllIcons.Actions.ListFiles), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { ReaderService.getInstance(it).toggleToc() }
    }
}

class ToggleSearchAction : AnAction("Search", "Toggle search sidebar", com.intellij.icons.AllIcons.Actions.Find), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { ReaderService.getInstance(it).toggleSearch() }
    }
}

class ToggleSettingsAction : AnAction("Settings", "Toggle settings popover", com.intellij.icons.AllIcons.General.Settings), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { ReaderService.getInstance(it).toggleSettings() }
    }
}
