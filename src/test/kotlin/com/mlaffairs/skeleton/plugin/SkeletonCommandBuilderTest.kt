package com.mlaffairs.skeleton.plugin

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class SkeletonCommandBuilderTest {
    @Test
    fun buildsRunCommandWithConfiguredOutputAndFilters() {
        val command = SkeletonCommandBuilder().buildRunCommand(
            projectBasePath = "/project",
            targetFilePath = "/project/app.py",
            targetParentPath = "/project",
            settings = SkeletonSettingsState(
                interpreterCommand = "uv run python",
                outputDirectory = ".skeleton/pycharm/latest",
                includePatterns = "app.*, services/*\nrepositories/*",
                excludePatterns = "tests/*",
                maxEvents = "500",
            ),
        )

        assertEquals(Path("/project").toAbsolutePath().normalize(), command.projectRoot)
        assertEquals(Path("/project/.skeleton/pycharm/latest").toAbsolutePath().normalize(), command.outputDirectory)
        assertEquals(command.outputDirectory.resolve("session.json"), command.sessionPath)
        assertEquals(
            listOf(
                "uv",
                "run",
                "python",
                "-m",
                "skeleton_replay",
                "run",
                "--project-root",
                command.projectRoot.toString(),
                "--out-dir",
                command.outputDirectory.toString(),
                "--include",
                "app.*",
                "--include",
                "services/*",
                "--include",
                "repositories/*",
                "--exclude",
                "tests/*",
                "--max-events",
                "500",
                "--no-open",
                "/project/app.py",
            ),
            command.commandLine,
        )
    }

    @Test
    fun usesStableDefaults() {
        val command = SkeletonCommandBuilder().buildRunCommand(
            projectBasePath = "/project",
            targetFilePath = "/project/app.py",
            targetParentPath = "/project",
            settings = SkeletonSettingsState(),
        )

        assertEquals(
            listOf(
                "python",
                "-m",
                "skeleton_replay",
                "run",
                "--project-root",
                command.projectRoot.toString(),
                "--out-dir",
                command.outputDirectory.toString(),
                "--no-open",
                "/project/app.py",
            ),
            command.commandLine,
        )
    }

    @Test
    fun buildsPytestCommandForTestFiles() {
        val command = SkeletonCommandBuilder().buildPytestCommand(
            projectBasePath = "/project",
            targetFilePath = "/project/tests/test_checkout.py",
            targetParentPath = "/project/tests",
            settings = SkeletonSettingsState(),
        )

        assertEquals(
            listOf(
                "python",
                "-m",
                "skeleton_replay",
                "pytest",
                "--project-root",
                command.projectRoot.toString(),
                "--out-dir",
                command.outputDirectory.toString(),
                "--no-open",
                "--",
                "/project/tests/test_checkout.py",
            ),
            command.commandLine,
        )
    }
}
