package com.mlaffairs.skeleton.plugin

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.JTextArea

class SkeletonWorkbenchPanel(private val project: Project) {
    val component: JComponent = JPanel(BorderLayout())

    private val reportPanel = JPanel(BorderLayout())
    private val workflowText = JTextArea("Run Skeleton to load workflow.md").apply { isEditable = false }
    private val artifactsText = JTextArea("No session.json loaded").apply { isEditable = false }
    private val qualityText = JTextArea("Run Skeleton to load architecture_quality.md").apply { isEditable = false }
    private val logText = JTextArea("Skeleton Replay workbench ready for ${project.name}.").apply { isEditable = false }

    init {
        val tabs = JTabbedPane()
        reportPanel.add(JBLabel("Run Skeleton to load report.html"), BorderLayout.CENTER)
        tabs.addTab("Report", reportPanel)
        tabs.addTab("Workflow", JBScrollPane(workflowText))
        tabs.addTab("Artifacts", JBScrollPane(artifactsText))
        tabs.addTab("Quality", JBScrollPane(qualityText))
        tabs.addTab("Log", JBScrollPane(logText))
        component.add(tabs, BorderLayout.CENTER)
    }
}
