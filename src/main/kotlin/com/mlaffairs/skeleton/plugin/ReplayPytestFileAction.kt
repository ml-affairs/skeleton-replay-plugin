package com.mlaffairs.skeleton.plugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class ReplayPytestFileAction : AnAction() {
    override fun update(event: AnActionEvent) {
        val virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
        event.presentation.isEnabledAndVisible = virtualFile?.extension == "py" && isLikelyTestFile(virtualFile.path)
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        SkeletonRunnerService.getInstance(project).runPytestFile(virtualFile)
    }

    private fun isLikelyTestFile(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        val name = normalized.substringAfterLast('/')
        return name.startsWith("test_") || name.endsWith("_test.py") || normalized.contains("/tests/")
    }
}
