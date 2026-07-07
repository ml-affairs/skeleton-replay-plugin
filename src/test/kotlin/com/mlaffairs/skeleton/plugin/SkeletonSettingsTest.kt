package com.mlaffairs.skeleton.plugin

import kotlin.test.Test
import kotlin.test.assertEquals

class SkeletonSettingsTest {
    @Test
    fun defaultsUseProjectInterpreterCommandAndStableArtifactDirectory() {
        val state = SkeletonSettingsState()

        assertEquals("python", state.interpreterCommand)
        assertEquals("python -m pip install skeleton-replay", state.packageInstallCommand)
        assertEquals(".skeleton/pycharm/latest", state.outputDirectory)
    }
}
