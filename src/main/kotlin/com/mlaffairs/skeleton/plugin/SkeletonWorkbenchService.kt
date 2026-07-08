package com.mlaffairs.skeleton.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

@Service(Service.Level.PROJECT)
class SkeletonWorkbenchService(private val project: Project) {
    private var panel: SkeletonWorkbenchPanel? = null
    private val logBuffer = StringBuilder()
    private var loadedSession: SkeletonLoadedSession? = null
    private var activeCommand: SkeletonRunCommand? = null
    private var statusText: String = "Skeleton Replay workbench ready for ${project.name}."

    fun createPanel(): SkeletonWorkbenchPanel {
        val newPanel = SkeletonWorkbenchPanel(project)
        panel = newPanel
        replayState(newPanel)
        return newPanel
    }

    fun activateToolWindow() {
        ApplicationManager.getApplication().invokeLater {
            ToolWindowManager.getInstance(project).getToolWindow("Skeleton")?.activate(null)
        }
    }

    fun runStarted(command: SkeletonRunCommand) {
        logBuffer.clear()
        loadedSession = null
        activeCommand = command
        statusText = "Running Skeleton..."
        appendLogLine("Running: ${command.commandLine.joinToString(" ")}")
        appendLogLine("Working directory: ${command.projectRoot}")
        invokePanel {
            it.runStarted(command)
        }
    }

    fun appendLog(text: String) {
        logBuffer.append(text)
        invokePanel {
            it.setLogText(logBuffer.toString())
        }
    }

    fun appendLogLine(text: String) {
        appendLog(text + System.lineSeparator())
    }

    fun sessionLoaded(session: SkeletonLoadedSession) {
        loadedSession = session
        activeCommand = null
        statusText = if (session.manifest.target_exit_code == 0) {
            "Skeleton artifacts loaded."
        } else {
            "Skeleton artifacts loaded; target exited with ${session.manifest.target_exit_code}."
        }
        invokePanel {
            it.displayLoadedSession(session)
        }
    }

    fun runFailed(message: String) {
        activeCommand = null
        statusText = message
        appendLogLine(message)
        invokePanel {
            it.displayError(message)
        }
    }

    private fun replayState(targetPanel: SkeletonWorkbenchPanel) {
        targetPanel.setLogText(logBuffer.toString().ifBlank { statusText })
        when {
            loadedSession != null -> targetPanel.displayLoadedSession(loadedSession!!)
            activeCommand != null -> targetPanel.runStarted(activeCommand!!)
            else -> targetPanel.displayStatus(statusText)
        }
    }

    private fun invokePanel(action: (SkeletonWorkbenchPanel) -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            panel?.let(action)
        }
    }

    companion object {
        fun getInstance(project: Project): SkeletonWorkbenchService = project.getService(SkeletonWorkbenchService::class.java)
    }
}
