package com.mlaffairs.skeleton.plugin

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.UIManager
import javax.swing.border.EmptyBorder

object SkeletonStartupReportPanel {
    private val modifiedFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    fun scanning(): JComponent =
        centerMessage("Scanning for Skeleton reports...")

    fun startScreen(
        reports: List<SkeletonDiscoveredReport>,
        onManualLoad: () -> Unit,
        onRescan: () -> Unit,
        onPyPi: () -> Unit,
        onLoadSession: (Path) -> Unit,
    ): JComponent =
        JPanel(BorderLayout()).apply {
            border = EmptyBorder(12, 12, 12, 12)
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                    add(JButton("Load Artifact...").apply {
                        addActionListener { onManualLoad() }
                    })
                    add(JButton("Scan Reports").apply {
                        addActionListener { onRescan() }
                    })
                    if (reports.isEmpty()) {
                        add(JButton("skeleton-replay on PyPI").apply {
                            addActionListener { onPyPi() }
                        })
                    }
                },
                BorderLayout.NORTH,
            )
            if (reports.isEmpty()) {
                add(emptyState(), BorderLayout.CENTER)
            } else {
                add(reportList(reports, onLoadSession), BorderLayout.CENTER)
            }
        }

    private fun reportList(reports: List<SkeletonDiscoveredReport>, onLoadSession: (Path) -> Unit): JComponent =
        JScrollPane(
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                border = EmptyBorder(12, 0, 0, 0)
                reports.forEachIndexed { index, report ->
                    val row = reportRow(report, onLoadSession)
                    row.alignmentX = 0.0f
                    row.maximumSize = Dimension(Int.MAX_VALUE, row.preferredSize.height)
                    add(row)
                    if (index != reports.lastIndex) {
                        add(Box.createVerticalStrut(8))
                    }
                }
            },
        )

    private fun reportRow(report: SkeletonDiscoveredReport, onLoadSession: (Path) -> Unit): JComponent =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor") ?: Color(0x4b5563)),
                EmptyBorder(10, 12, 10, 12),
            )
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            val titleLabel = JLabel(displayTarget(report.targetLabel)).apply {
                alignmentX = 0.0f
                font = font.deriveFont(font.style or Font.BOLD)
            }
            val targetLabel = report.targetDetailLabel?.let { secondaryLabel(it) }
            val artifactLabel = secondaryLabel("artifact ${report.artifactLabel}")
            val summaryLabel = secondaryLabel(reportSummary(report))
            add(titleLabel)
            targetLabel?.let { add(it) }
            add(artifactLabel)
            add(summaryLabel)
            installClickHandler(this) { onLoadSession(report.sessionPath) }
            installClickHandler(titleLabel) { onLoadSession(report.sessionPath) }
            targetLabel?.let { installClickHandler(it) { onLoadSession(report.sessionPath) } }
            installClickHandler(artifactLabel) { onLoadSession(report.sessionPath) }
            installClickHandler(summaryLabel) { onLoadSession(report.sessionPath) }
        }

    private fun emptyState(): JComponent =
        JPanel(GridLayout(0, 1, 0, 6)).apply {
            border = EmptyBorder(16, 0, 0, 0)
            add(JLabel("No .skeleton reports found in this project."))
            add(JLabel("Install skeleton-replay on PyPI: python -m pip install skeleton-replay"))
            add(JLabel("Ask your LLM CLI to add a scenario test with skeleton-replay."))
            add(JLabel("After it creates a .skeleton report, click Scan Reports."))
        }

    private fun centerMessage(message: String): JComponent =
        JPanel(BorderLayout()).apply {
            border = EmptyBorder(12, 12, 12, 12)
            add(JLabel(message), BorderLayout.CENTER)
        }

    private fun reportSummary(report: SkeletonDiscoveredReport): String {
        val metrics = listOfNotNull(
            report.events?.let { "$it events" },
            report.targetExitCode?.let { "exit $it" },
            report.status.label(),
            "modified ${modifiedFormatter.format(report.modifiedTime)}",
        )
        return metrics.joinToString(" | ")
    }

    private fun SkeletonDiscoveredReportStatus.label(): String =
        when (this) {
            SkeletonDiscoveredReportStatus.LOADABLE -> "report ready"
            SkeletonDiscoveredReportStatus.REPORT_MISSING -> "report missing"
            SkeletonDiscoveredReportStatus.MALFORMED_SESSION -> "session unreadable"
        }

    private fun displayTarget(targetLabel: String): String =
        targetLabel.ifBlank { "Skeleton report" }

    private fun secondaryLabel(text: String): JLabel =
        JLabel(text).apply {
            alignmentX = 0.0f
            foreground = UIManager.getColor("Label.disabledForeground") ?: foreground
        }

    private fun installClickHandler(component: JComponent, onClick: () -> Unit) {
        component.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    onClick()
                }
            },
        )
    }
}
