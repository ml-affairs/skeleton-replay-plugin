package com.mlaffairs.skeleton.plugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class LoadSkeletonArtifactAction : AnAction() {
    override fun update(event: AnActionEvent) {
        val virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
        event.presentation.isEnabledAndVisible = virtualFile != null && (
            virtualFile.isDirectory ||
                virtualFile.name == "session.json" ||
                virtualFile.name == "report.html" ||
                virtualFile.parent?.findChild("session.json") != null
            )
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        SkeletonRunnerService.getInstance(project).loadExistingArtifact(virtualFile.path)
    }
}
