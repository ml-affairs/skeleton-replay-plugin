package com.mlaffairs.skeleton.plugin

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

data class SkeletonSettingsState(
    var interpreterCommand: String = "python",
    var packageInstallCommand: String = "python -m pip install skeleton-replay",
    var outputDirectory: String = ".skeleton/pycharm/latest",
    var includePatterns: String = "",
    var excludePatterns: String = "",
    var maxEvents: String = "",
    var openReportInsideIde: Boolean = true,
)

@Service(Service.Level.PROJECT)
@State(name = "SkeletonReplaySettings", storages = [Storage("skeleton-replay.xml")])
class SkeletonSettings : PersistentStateComponent<SkeletonSettingsState> {
    private var state = SkeletonSettingsState()

    override fun getState(): SkeletonSettingsState = state

    override fun loadState(state: SkeletonSettingsState) {
        this.state = state
    }

    companion object {
        fun getInstance(project: Project): SkeletonSettings = project.getService(SkeletonSettings::class.java)
    }
}
