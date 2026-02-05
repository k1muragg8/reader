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


