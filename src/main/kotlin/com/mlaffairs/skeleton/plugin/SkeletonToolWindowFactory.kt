package com.mlaffairs.skeleton.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class SkeletonToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val workbench = SkeletonWorkbenchPanel(project)
        val content = ContentFactory.getInstance().createContent(workbench.component, "Workbench", false)
        toolWindow.contentManager.addContent(content)
    }
}
