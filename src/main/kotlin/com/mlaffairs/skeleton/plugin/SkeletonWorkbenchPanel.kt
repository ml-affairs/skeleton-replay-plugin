package com.mlaffairs.skeleton.plugin

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
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
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import javax.swing.TransferHandler
import javax.swing.event.HyperlinkEvent

class SkeletonWorkbenchPanel(private val project: Project) {
    val component: JComponent = JPanel(BorderLayout())

    private val reportPanel = JPanel(BorderLayout())
    private val tabs = JTabbedPane()
    private val workflowText = richTextPane("Run Skeleton to load workflow.md")
    private val artifactsText = richTextPane("No session.json loaded")
    private val qualityText = richTextPane("Run Skeleton to load architecture_quality.md")
    private val logText = readOnlyTextArea("Skeleton Replay workbench ready for ${project.name}.")
    private val followInIde = JCheckBox("Follow in IDE", SkeletonIdeNavigationService.getInstance(project).isFollowEnabled())
    private var reportBrowser: JBCefBrowser? = null
    private var reportBridge: SkeletonReportBridge? = null

    init {
        reportPanel.add(SkeletonStartupReportPanel.scanning(), BorderLayout.CENTER)
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
        SkeletonIdeNavigationService.getInstance(project).clearTraceIndex()
        workflowText.text = htmlPage("Workflow", "Skeleton is running. Workflow will load from ${command.outputDirectory.resolve("workflow.md")}.")
        artifactsText.text = htmlPage("Artifacts", "Waiting for ${command.sessionPath}.")
        qualityText.text = htmlPage("Quality", "Skeleton is running. Quality report will load from ${command.outputDirectory.resolve("architecture_quality.md")}.")
        showReportPlaceholder("Skeleton is running. Report will load when report.html is available.")
    }

    fun displayLoadedSession(session: SkeletonLoadedSession) {
        SkeletonIdeNavigationService.getInstance(project).setTraceIndex(session.traceIndex)
        workflowText.text = markdownHtml("Workflow", session.workflowText ?: "workflow.md was not found at ${session.manifest.artifacts.workflow}.")
        workflowText.caretPosition = 0
        qualityText.text = markdownHtml("Quality", session.qualityMarkdownText ?: "architecture_quality.md was not found at ${session.manifest.artifacts.quality_markdown}.")
        qualityText.caretPosition = 0
        artifactsText.text = artifactsHtml(session)
        artifactsText.caretPosition = 0
        displayReport(session.reportPath, session.manifest.artifacts.report)
    }

    fun displayStartupScanning() {
        SkeletonIdeNavigationService.getInstance(project).clearTraceIndex()
        resetArtifactTabs()
        reportPanel.removeAll()
        reportPanel.add(SkeletonStartupReportPanel.scanning(), BorderLayout.CENTER)
        reportPanel.revalidate()
        reportPanel.repaint()
    }

    fun displayStartupReports(reports: List<SkeletonDiscoveredReport>) {
        SkeletonIdeNavigationService.getInstance(project).clearTraceIndex()
        resetArtifactTabs()
        reportPanel.removeAll()
        reportPanel.add(
            SkeletonStartupReportPanel.startScreen(
                reports = reports,
                onManualLoad = { chooseAndLoadSession() },
                onRescan = { SkeletonWorkbenchService.getInstance(project).rescanStartupReports() },
                onPyPi = { BrowserUtil.browse("https://pypi.org/project/skeleton-replay/") },
                onLoadSession = { sessionPath -> SkeletonRunnerService.getInstance(project).loadExistingArtifact(sessionPath) },
            ),
            BorderLayout.CENTER,
        )
        reportPanel.revalidate()
        reportPanel.repaint()
    }

    fun displayStandaloneReport(reportPath: Path) {
        SkeletonIdeNavigationService.getInstance(project).clearTraceIndex()
        workflowText.text = htmlPage("Workflow", "No session.json was loaded, so workflow.md is not available for this standalone report.")
        qualityText.text = htmlPage("Quality", "No session.json was loaded, so architecture_quality.md is not available for this standalone report.")
        artifactsText.text = htmlPage(
            "Artifacts",
            """
            <div class="summary"><span class="warn">standalone report</span></div>
            <p>Loaded <a href="${escapeHtml(reportPath.toUri().toString())}">${escapeHtml(reportPath.toString())}</a>.</p>
            <p>Drop or load the matching <code>session.json</code> to populate Workflow, Quality, and artifact metadata.</p>
            """.trimIndent(),
            rawBody = true,
        )
        workflowText.caretPosition = 0
        qualityText.caretPosition = 0
        artifactsText.caretPosition = 0
        displayReport(reportPath, reportPath.toString())
    }

    fun displayStatus(message: String) {
        SkeletonIdeNavigationService.getInstance(project).clearTraceIndex()
        showReportPlaceholder(message)
    }

    fun displayError(message: String) {
        SkeletonIdeNavigationService.getInstance(project).clearTraceIndex()
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
            add(JButton("Load Artifact...").apply {
                addActionListener { chooseAndLoadSession() }
            })
            add(JButton("Unload").apply {
                addActionListener { SkeletonWorkbenchService.getInstance(project).unloadArtifact() }
            })
            add(followInIde.apply {
                addActionListener {
                    SkeletonIdeNavigationService.getInstance(project).setFollowEnabled(isSelected)
                }
            })
            add(JBLabel("Drop session.json, report.html, or an artifact folder here."))
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
        val descriptor = FileChooserDescriptor(true, true, false, false, false, false).apply {
            title = "Load Skeleton Artifact"
            description = "Choose session.json, report.html, or an artifact directory."
        }
        val initialFile = initialChooserPath()?.let { LocalFileSystem.getInstance().refreshAndFindFileByPath(it.toString()) }
        FileChooser.chooseFile(descriptor, project, initialFile) { selectedFile ->
            SkeletonRunnerService.getInstance(project).loadExistingArtifact(Path.of(selectedFile.path))
        }
    }

    private fun resetArtifactTabs() {
        workflowText.text = htmlPage("Workflow", "Load a Skeleton session to view workflow.md.")
        artifactsText.text = htmlPage("Artifacts", "No session.json loaded.")
        qualityText.text = htmlPage("Quality", "Load a Skeleton session to view architecture_quality.md.")
        workflowText.caretPosition = 0
        artifactsText.caretPosition = 0
        qualityText.caretPosition = 0
    }

    private fun initialChooserPath(): Path? {
        val projectRoot = project.basePath?.let { Path.of(it) } ?: return null
        return projectRoot
    }

    private fun installDropTarget(target: JComponent) {
        target.transferHandler = object : TransferHandler() {
            override fun canImport(support: TransferSupport): Boolean =
                support.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
                    support.isDataFlavorSupported(DataFlavor.stringFlavor)

            override fun importData(support: TransferSupport): Boolean {
                if (!canImport(support)) {
                    return false
                }
                val transferable = support.transferable
                if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*> ?: return false
                    val firstFile = files.firstOrNull() as? java.io.File ?: return false
                    SkeletonRunnerService.getInstance(project).loadExistingArtifact(firstFile.toPath())
                    return true
                }
                val text = transferable.getTransferData(DataFlavor.stringFlavor) as? String ?: return false
                val firstPath = text.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.isNotEmpty() }
                    ?: return false
                SkeletonRunnerService.getInstance(project).loadExistingArtifact(firstPath)
                return true
            }
        }
    }

    private fun readOnlyTextArea(text: String): JTextArea =
        JTextArea(text).apply {
            isEditable = false
            lineWrap = false
        }

    private fun richTextPane(text: String): JEditorPane =
        JEditorPane("text/html", htmlPage("Skeleton", text)).apply {
            isEditable = false
            addHyperlinkListener { event ->
                if (event.eventType == HyperlinkEvent.EventType.ACTIVATED && event.url != null) {
                    BrowserUtil.browse(event.url)
                }
            }
        }

    private fun centerLabel(text: String): JComponent =
        JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12)
            add(JBLabel(text), BorderLayout.CENTER)
        }

    private fun artifactsHtml(session: SkeletonLoadedSession): String {
        val manifest = session.manifest
        val statusClass = if (manifest.target_exit_code == 0) "ok" else "warn"
        val rows = listOf(
            "session" to manifest.artifacts.session,
            "trace" to manifest.artifacts.trace,
            "snapshot" to manifest.artifacts.snapshot,
            "workflow" to manifest.artifacts.workflow,
            "quality" to manifest.artifacts.quality,
            "quality markdown" to manifest.artifacts.quality_markdown,
            "report" to (manifest.artifacts.report ?: "not generated"),
        ).joinToString("") { (label, path) ->
            val value = if (path == "not generated") {
                path
            } else {
                """<a href="${escapeHtml(fileUri(path))}">${escapeHtml(path)}</a>"""
            }
            "<tr><th>${escapeHtml(label)}</th><td>$value</td></tr>"
        }
        return htmlPage(
            "Artifacts",
            """
            <p><b>${statusLabel(statusClass)}:</b> target exit ${manifest.target_exit_code} &nbsp; ${manifest.metrics.events} events &nbsp; ${manifest.metrics.nodes} nodes &nbsp; ${manifest.metrics.edges} edges</p>
            <h3>Run</h3>
            <table>
              <tr><th>Skeleton</th><td>${escapeHtml(manifest.skeleton_version)}</td></tr>
              <tr><th>Command</th><td><code>${escapeHtml(manifest.command)}</code></td></tr>
              <tr><th>Project root</th><td>${escapeHtml(manifest.project_root)}</td></tr>
              <tr><th>Target</th><td>${escapeHtml(manifest.target.kind)} ${escapeHtml(manifest.target.path ?: "")}</td></tr>
              <tr><th>Error</th><td>${escapeHtml(manifest.target_error ?: "none")}</td></tr>
            </table>
            <h3>Files</h3>
            <table>$rows</table>
            """.trimIndent(),
            rawBody = true,
        )
    }

    private fun markdownHtml(title: String, markdown: String): String {
        val body = markdown.lines().joinToString("\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("### ") -> "<h3>${escapeHtml(trimmed.removePrefix("### "))}</h3>"
                trimmed.startsWith("## ") -> "<h2>${escapeHtml(trimmed.removePrefix("## "))}</h2>"
                trimmed.startsWith("# ") -> "<h1>${escapeHtml(trimmed.removePrefix("# "))}</h1>"
                trimmed.startsWith("- ") -> "<p>&bull; ${highlightTerms(escapeHtml(trimmed.removePrefix("- ")))}</p>"
                trimmed.startsWith("* ") -> "<p>&bull; ${highlightTerms(escapeHtml(trimmed.removePrefix("* ")))}</p>"
                trimmed.isEmpty() -> "<br/>"
                else -> "<p>${highlightTerms(escapeHtml(trimmed))}</p>"
            }
        }
        return htmlPage(title, body, rawBody = true)
    }

    private fun htmlPage(title: String, body: String, rawBody: Boolean = false): String {
        val safeBody = if (rawBody) body else "<p>${escapeHtml(body)}</p>"
        return """
            <html>
            <body>
              <h2>${escapeHtml(title)}</h2>
              $safeBody
            </body>
            </html>
        """.trimIndent()
    }

    private fun highlightTerms(text: String): String =
        text.replace(Regex("\\b(warning|risk|error|failed|failure|coupling|boundary|entrypoint|service|repository|adapter|module|method|return)\\b", RegexOption.IGNORE_CASE)) {
            "<b>${it.value}</b>"
        }

    private fun statusLabel(statusClass: String): String =
        if (statusClass == "ok") "OK" else "Warning"

    private fun fileUri(path: String): String =
        runCatching { Path.of(path).toUri().toString() }.getOrDefault(path)

    private fun escapeHtml(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
