package com.mlaffairs.skeleton.plugin

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Files
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class SkeletonRunnerService(private val project: Project) {
    private val commandBuilder = SkeletonCommandBuilder()
    private val sessionLoader = SkeletonSessionLoader()

    fun runCurrentFile(targetFile: VirtualFile) {
        val settings = SkeletonSettings.getInstance(project).state
        val command = commandBuilder.buildRunCommand(project, targetFile, settings)
        runCommand(command, settings)
    }

    fun runPytestFile(targetFile: VirtualFile) {
        val settings = SkeletonSettings.getInstance(project).state
        val command = commandBuilder.buildPytestCommand(project, targetFile, settings)
        runCommand(command, settings)
    }

    fun loadExistingArtifact(path: Path) {
        val workbench = SkeletonWorkbenchService.getInstance(project)
        workbench.activateToolWindow()
        val sessionPath = resolveSessionPath(path)
        if (sessionPath == null) {
            workbench.runFailed("Could not find session.json from $path")
            return
        }
        workbench.appendLogLine("Loading existing Skeleton session: $sessionPath")
        loadSession(SkeletonRunCommand(emptyList(), sessionPath.parent, sessionPath.parent, sessionPath), 0, workbench)
    }

    private fun runCommand(command: SkeletonRunCommand, settings: SkeletonSettingsState) {
        val workbench = SkeletonWorkbenchService.getInstance(project)
        workbench.activateToolWindow()
        workbench.runStarted(command)

        val commandLine = GeneralCommandLine(command.commandLine)
            .withWorkDirectory(command.projectRoot.toFile())
            .withCharset(Charsets.UTF_8)
        try {
            val handler = OSProcessHandler(commandLine)
            handler.addProcessListener(object : ProcessAdapter() {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    val prefix = if (outputType == ProcessOutputTypes.STDERR) "stderr: " else ""
                    workbench.appendLog(prefix + event.text)
                }

                override fun processTerminated(event: ProcessEvent) {
                    workbench.appendLogLine("Skeleton process exited with ${event.exitCode}.")
                    loadSession(command, event.exitCode, workbench)
                }
            })
            handler.startNotify()
        } catch (exception: ExecutionException) {
            val message = "Could not start Skeleton: ${exception.message ?: exception.javaClass.simpleName}"
            workbench.runFailed(message)
            notify(message, settings.packageInstallCommand, NotificationType.ERROR)
        }
    }

    private fun resolveSessionPath(path: Path): Path? {
        val normalizedPath = path.toAbsolutePath().normalize()
        return when {
            Files.isDirectory(normalizedPath) && Files.exists(normalizedPath.resolve("session.json")) -> normalizedPath.resolve("session.json")
            normalizedPath.fileName.toString() == "session.json" && Files.exists(normalizedPath) -> normalizedPath
            normalizedPath.fileName.toString() == "report.html" && Files.exists(normalizedPath.parent.resolve("session.json")) -> normalizedPath.parent.resolve("session.json")
            else -> null
        }
    }

    private fun loadSession(command: SkeletonRunCommand, exitCode: Int, workbench: SkeletonWorkbenchService) {
        try {
            val session = sessionLoader.load(command.sessionPath)
            workbench.sessionLoaded(session)
            val notificationType = if (session.manifest.target_exit_code == 0 && exitCode == 0) {
                NotificationType.INFORMATION
            } else {
                NotificationType.WARNING
            }
            notify("Skeleton artifacts loaded", session.sessionPath.toString(), notificationType)
        } catch (exception: RuntimeException) {
            val message = exception.message ?: "Could not load Skeleton session.json."
            workbench.runFailed(message)
            notify(message, "Check the Skeleton Replay Log tab for process output.", NotificationType.ERROR)
        }
    }

    private fun notify(title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Skeleton Replay")
            .createNotification(title, content, type)
            .notify(project)
    }

    companion object {
        fun getInstance(project: Project): SkeletonRunnerService = project.getService(SkeletonRunnerService::class.java)
    }
}
