package com.mlaffairs.skeleton.plugin

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class ReplayCurrentFileAction : AnAction() {
    override fun update(event: AnActionEvent) {
        val virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
        event.presentation.isEnabledAndVisible = virtualFile?.extension == "py"
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val settings = SkeletonSettings.getInstance(project).state
        val command = listOf(
            settings.interpreterCommand,
            "-m",
            "skeleton_replay",
            "run",
            "--project-root",
            project.basePath.orEmpty(),
            "--out-dir",
            settings.outputDirectory,
            "--no-open",
            virtualFile.path,
        )

        NotificationGroupManager.getInstance()
            .getNotificationGroup("Skeleton Replay")
            .createNotification(
                "Skeleton command prepared",
                command.joinToString(" "),
                NotificationType.INFORMATION,
            )
            .notify(project)
    }
}
