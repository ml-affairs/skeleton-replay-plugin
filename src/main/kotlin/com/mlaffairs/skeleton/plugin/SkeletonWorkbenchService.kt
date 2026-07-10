package com.mlaffairs.skeleton.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class SkeletonWorkbenchService(private val project: Project) {
    private val discovery = SkeletonArtifactDiscovery()
    private var panel: SkeletonWorkbenchPanel? = null
    private val logBuffer = StringBuilder()
    private var loadedSession: SkeletonLoadedSession? = null
    private var standaloneReportPath: Path? = null
    private var activeCommand: SkeletonRunCommand? = null
    private var statusText: String = "Skeleton Replay workbench ready for ${project.name}."
    private var startupDiscoveryResults: List<SkeletonDiscoveredReport>? = null
    private var startupDiscoveryInProgress = false
    private var startupDiscoveryGeneration = 0

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
        invalidateStartupDiscovery()
        logBuffer.clear()
        loadedSession = null
        standaloneReportPath = null
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
        invalidateStartupDiscovery()
        loadedSession = session
        standaloneReportPath = null
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

    fun standaloneReportLoaded(reportPath: Path) {
        invalidateStartupDiscovery()
        loadedSession = null
        standaloneReportPath = reportPath
        activeCommand = null
        statusText = "Standalone Skeleton report loaded."
        invokePanel {
            it.displayStandaloneReport(reportPath)
        }
    }

    fun runFailed(message: String) {
        invalidateStartupDiscovery()
        activeCommand = null
        statusText = message
        appendLogLine(message)
        invokePanel {
            it.displayError(message)
        }
    }

    fun rescanStartupReports() {
        if (!shouldShowStartupDiscovery()) {
            return
        }
        startupDiscoveryResults = null
        startupDiscoveryInProgress = false
        invokePanel { it.displayStartupScanning() }
        startStartupDiscovery()
    }

    fun unloadArtifact() {
        loadedSession = null
        standaloneReportPath = null
        activeCommand = null
        statusText = "Skeleton Replay workbench ready for ${project.name}."
        invokePanel { replayState(it) }
    }

    private fun replayState(targetPanel: SkeletonWorkbenchPanel) {
        targetPanel.setLogText(logBuffer.toString().ifBlank { statusText })
        when {
            loadedSession != null -> targetPanel.displayLoadedSession(loadedSession!!)
            standaloneReportPath != null -> targetPanel.displayStandaloneReport(standaloneReportPath!!)
            activeCommand != null -> targetPanel.runStarted(activeCommand!!)
            shouldShowStartupDiscovery() -> {
                val results = startupDiscoveryResults
                if (results == null) {
                    targetPanel.displayStartupScanning()
                    startStartupDiscovery()
                } else {
                    targetPanel.displayStartupReports(results)
                }
            }
            else -> targetPanel.displayStatus(statusText)
        }
    }

    private fun startStartupDiscovery() {
        if (startupDiscoveryInProgress) {
            return
        }
        val projectRoot = project.basePath?.let { Path.of(it) }
        if (projectRoot == null) {
            startupDiscoveryResults = emptyList()
            invokePanel { it.displayStartupReports(emptyList()) }
            return
        }
        startupDiscoveryInProgress = true
        val generation = ++startupDiscoveryGeneration
        ApplicationManager.getApplication().executeOnPooledThread {
            val reports = runCatching { discovery.discover(projectRoot) }.getOrDefault(emptyList())
            ApplicationManager.getApplication().invokeLater {
                if (generation != startupDiscoveryGeneration || !shouldShowStartupDiscovery()) {
                    return@invokeLater
                }
                startupDiscoveryInProgress = false
                startupDiscoveryResults = reports
                panel?.displayStartupReports(reports)
            }
        }
    }

    private fun shouldShowStartupDiscovery(): Boolean =
        loadedSession == null &&
            standaloneReportPath == null &&
            activeCommand == null &&
            statusText.startsWith("Skeleton Replay workbench ready")

    private fun invalidateStartupDiscovery() {
        startupDiscoveryGeneration += 1
        startupDiscoveryInProgress = false
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
