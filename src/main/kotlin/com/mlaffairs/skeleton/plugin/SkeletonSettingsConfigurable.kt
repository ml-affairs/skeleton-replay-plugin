package com.mlaffairs.skeleton.plugin

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent

class SkeletonSettingsConfigurable(private val project: Project) : Configurable {
    private val settings = SkeletonSettings.getInstance(project)
    private val interpreterCommand = JBTextField()
    private val packageInstallCommand = JBTextField()
    private val outputDirectory = JBTextField()
    private val includePatterns = JBTextField()
    private val excludePatterns = JBTextField()
    private val maxEvents = JBTextField()
    private val openReportInsideIde = JBCheckBox("Open report inside PyCharm when possible")

    override fun getDisplayName(): String = "Skeleton Replay"

    override fun createComponent(): JComponent {
        reset()
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Interpreter command", interpreterCommand)
            .addLabeledComponent("Install command", packageInstallCommand)
            .addLabeledComponent("Output directory", outputDirectory)
            .addLabeledComponent("Include patterns", includePatterns)
            .addLabeledComponent("Exclude patterns", excludePatterns)
            .addLabeledComponent("Max events", maxEvents)
            .addComponent(openReportInsideIde)
            .panel
    }

    override fun isModified(): Boolean {
        val state = settings.state
        return interpreterCommand.text != state.interpreterCommand ||
            packageInstallCommand.text != state.packageInstallCommand ||
            outputDirectory.text != state.outputDirectory ||
            includePatterns.text != state.includePatterns ||
            excludePatterns.text != state.excludePatterns ||
            maxEvents.text != state.maxEvents ||
            openReportInsideIde.isSelected != state.openReportInsideIde
    }

    override fun apply() {
        settings.loadState(
            SkeletonSettingsState(
                interpreterCommand = interpreterCommand.text,
                packageInstallCommand = packageInstallCommand.text,
                outputDirectory = outputDirectory.text,
                includePatterns = includePatterns.text,
                excludePatterns = excludePatterns.text,
                maxEvents = maxEvents.text,
                openReportInsideIde = openReportInsideIde.isSelected,
            )
        )
    }

    override fun reset() {
        val state = settings.state
        interpreterCommand.text = state.interpreterCommand
        packageInstallCommand.text = state.packageInstallCommand
        outputDirectory.text = state.outputDirectory
        includePatterns.text = state.includePatterns
        excludePatterns.text = state.excludePatterns
        maxEvents.text = state.maxEvents
        openReportInsideIde.isSelected = state.openReportInsideIde
    }
}
