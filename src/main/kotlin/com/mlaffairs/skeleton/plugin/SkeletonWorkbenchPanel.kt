package com.mlaffairs.skeleton.plugin

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.datatransfer.DataFlavor
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import javax.swing.TransferHandler

class SkeletonWorkbenchPanel(private val project: Project) {
    val component: JComponent = JPanel(BorderLayout())

    private val reportPanel = JPanel(BorderLayout())
    private val tabs = JTabbedPane()
    private val workflowText = readOnlyTextArea("Run Skeleton to load workflow.md")
    private val artifactsText = readOnlyTextArea("No session.json loaded")
    private val qualityText = readOnlyTextArea("Run Skeleton to load architecture_quality.md")
    private val logText = readOnlyTextArea("Skeleton Replay workbench ready for ${project.name}.")
    private var reportBrowser: JBCefBrowser? = null
    private var reportBridge: SkeletonReportBridge? = null

    init {
        reportPanel.add(centerLabel("Run Skeleton to load report.html"), BorderLayout.CENTER)
        tabs.addTab("Report", reportPanel)
        tabs.addTab("Workflow", JBScrollPane(workflowText))
        tabs.addTab("Artifacts", JBScrollPane(artifactsText))
        tabs.addTab("Quality", JBScrollPane(qualityText))
        tabs.addTab("Log", JBScrollPane(logText))
        component.add(toolbar(), BorderLayout.NORTH)
        component.add(tabs, BorderLayout.CENTER)
        installDropTarget(component)
        installDropTarget(tabs)
        installDropTarget(reportPanel)
    }

    fun runStarted(command: SkeletonRunCommand) {
        workflowText.text = "Skeleton is running. Workflow will load from ${command.outputDirectory.resolve("workflow.md")}."
        artifactsText.text = "Waiting for ${command.sessionPath}."
        qualityText.text = "Skeleton is running. Quality report will load from ${command.outputDirectory.resolve("architecture_quality.md")}."
        showReportPlaceholder("Skeleton is running. Report will load when report.html is available.")
    }

    fun displayLoadedSession(session: SkeletonLoadedSession) {
        workflowText.text = session.workflowText ?: "workflow.md was not found at ${session.manifest.artifacts.workflow}."
        workflowText.caretPosition = 0
        qualityText.text = session.qualityMarkdownText ?: "architecture_quality.md was not found at ${session.manifest.artifacts.quality_markdown}."
        qualityText.caretPosition = 0
        artifactsText.text = session.artifactsText
        artifactsText.caretPosition = 0
        displayReport(session.reportPath, session.manifest.artifacts.report)
    }

    fun displayStatus(message: String) {
        showReportPlaceholder(message)
    }

    fun displayError(message: String) {
        showReportPlaceholder(message)
        Messages.showErrorDialog(project, message, "Skeleton Replay")
    }

    fun setLogText(text: String) {
        logText.text = text
        logText.caretPosition = logText.document.length
    }

    private fun displayReport(reportPath: Path?, configuredReportPath: String?) {
        if (reportPath == null) {
            showReportPlaceholder(configuredReportPath?.let { "report.html was not found at $it." } ?: "This Skeleton run did not generate report.html.")
            return
        }
        if (JBCefApp.isSupported()) {
            val browser = reportBrowser ?: JBCefBrowser().also { reportBrowser = it }
            ensureReportBridge(browser)
            browser.loadURL(reportPath.toUri().toString())
            reportPanel.removeAll()
            reportPanel.add(browser.component, BorderLayout.CENTER)
            reportPanel.revalidate()
            reportPanel.repaint()
            return
        }
        showOpenReportFallback(reportPath)
    }

    private fun showReportPlaceholder(message: String) {
        reportPanel.removeAll()
        reportPanel.add(centerLabel(message), BorderLayout.CENTER)
        reportPanel.revalidate()
        reportPanel.repaint()
    }

    private fun showOpenReportFallback(reportPath: Path) {
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12)
            add(JBLabel("JCEF is not available in this IDE runtime."), BorderLayout.NORTH)
            add(JBLabel(reportPath.toString()), BorderLayout.CENTER)
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    add(JButton("Open report.html").apply {
                        addActionListener { BrowserUtil.browse(reportPath.toUri()) }
                    })
                },
                BorderLayout.SOUTH,
            )
        }
        reportPanel.removeAll()
        reportPanel.add(panel, BorderLayout.CENTER)
        reportPanel.revalidate()
        reportPanel.repaint()
    }

    private fun toolbar(): JComponent =
        JPanel(FlowLayout(FlowLayout.LEFT, 8, 6)).apply {
            border = JBUI.Borders.emptyLeft(4)
            add(JButton("Load session.json").apply {
                addActionListener { chooseAndLoadSession() }
            })
            add(followToggle())
            add(JBLabel("Drop session.json, report.html, or an artifact folder here."))
        }

    private fun followToggle(): JCheckBox =
        JCheckBox("Follow in IDE", SkeletonIdeNavigationService.getInstance(project).isFollowEnabled()).apply {
            addActionListener {
                SkeletonIdeNavigationService.getInstance(project).setFollowEnabled(isSelected)
            }
        }

    private fun ensureReportBridge(browser: JBCefBrowser) {
        if (reportBridge != null) {
            return
        }
        reportBridge = SkeletonReportBridge(browser) { payloadJson ->
            SkeletonIdeNavigationService.getInstance(project).consumeBridgePayload(payloadJson)
        }.also { bridge -> bridge.install() }
    }

    private fun chooseAndLoadSession() {
        val chooser = JFileChooser().apply {
            dialogTitle = "Load Skeleton session.json"
            fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
        }
        if (chooser.showOpenDialog(component) == JFileChooser.APPROVE_OPTION) {
            SkeletonRunnerService.getInstance(project).loadExistingArtifact(chooser.selectedFile.toPath())
        }
    }

    private fun installDropTarget(target: JComponent) {
        target.transferHandler = object : TransferHandler() {
            override fun canImport(support: TransferSupport): Boolean =
                support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)

            override fun importData(support: TransferSupport): Boolean {
                if (!canImport(support)) {
                    return false
                }
                val files = support.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*> ?: return false
                val firstFile = files.firstOrNull() as? java.io.File ?: return false
                SkeletonRunnerService.getInstance(project).loadExistingArtifact(firstFile.toPath())
                return true
            }
        }
    }

    private fun readOnlyTextArea(text: String): JTextArea =
        JTextArea(text).apply {
            isEditable = false
            lineWrap = false
        }

    private fun centerLabel(text: String): JComponent =
        JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12)
            add(JBLabel(text), BorderLayout.CENTER)
        }
}
