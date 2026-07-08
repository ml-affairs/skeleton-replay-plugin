package com.mlaffairs.skeleton.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.execution.ParametersListUtil
import java.nio.file.Path
import java.nio.file.Paths

data class SkeletonRunCommand(
    val commandLine: List<String>,
    val projectRoot: Path,
    val outputDirectory: Path,
    val sessionPath: Path,
)

class SkeletonCommandBuilder {
    fun buildRunCommand(project: Project, targetFile: VirtualFile, settings: SkeletonSettingsState): SkeletonRunCommand {
        return buildRunCommand(project.basePath, targetFile.path, targetFile.parent?.path, settings)
    }

    fun buildPytestCommand(project: Project, targetFile: VirtualFile, settings: SkeletonSettingsState): SkeletonRunCommand {
        return buildPytestCommand(project.basePath, targetFile.path, targetFile.parent?.path, settings)
    }

    fun buildRunCommand(projectBasePath: String?, targetFilePath: String, targetParentPath: String?, settings: SkeletonSettingsState): SkeletonRunCommand {
        val projectRoot = Paths.get(projectBasePath ?: targetParentPath ?: targetFilePath).toAbsolutePath().normalize()
        val outputDirectory = resolveOutputDirectory(projectRoot, settings.outputDirectory)
        val commandLine = mutableListOf<String>()
        commandLine.addAll(parseCommand(settings.interpreterCommand, "python"))
        commandLine.addAll(
            listOf(
                "-m",
                "skeleton_replay",
                "run",
                "--project-root",
                projectRoot.toString(),
                "--out-dir",
                outputDirectory.toString(),
            )
        )
        splitPatterns(settings.includePatterns).forEach {
            commandLine.add("--include")
            commandLine.add(it)
        }
        splitPatterns(settings.excludePatterns).forEach {
            commandLine.add("--exclude")
            commandLine.add(it)
        }
        settings.maxEvents.trim().takeIf { it.isNotEmpty() }?.let {
            commandLine.add("--max-events")
            commandLine.add(it)
        }
        commandLine.add("--no-open")
        commandLine.add(targetFilePath)
        return SkeletonRunCommand(
            commandLine = commandLine,
            projectRoot = projectRoot,
            outputDirectory = outputDirectory,
            sessionPath = outputDirectory.resolve("session.json"),
        )
    }

    fun buildPytestCommand(projectBasePath: String?, targetFilePath: String, targetParentPath: String?, settings: SkeletonSettingsState): SkeletonRunCommand {
        val projectRoot = Paths.get(projectBasePath ?: targetParentPath ?: targetFilePath).toAbsolutePath().normalize()
        val outputDirectory = resolveOutputDirectory(projectRoot, settings.outputDirectory)
        val commandLine = mutableListOf<String>()
        commandLine.addAll(parseCommand(settings.interpreterCommand, "python"))
        commandLine.addAll(
            listOf(
                "-m",
                "skeleton_replay",
                "pytest",
                "--project-root",
                projectRoot.toString(),
                "--out-dir",
                outputDirectory.toString(),
            )
        )
        splitPatterns(settings.includePatterns).forEach {
            commandLine.add("--include")
            commandLine.add(it)
        }
        splitPatterns(settings.excludePatterns).forEach {
            commandLine.add("--exclude")
            commandLine.add(it)
        }
        settings.maxEvents.trim().takeIf { it.isNotEmpty() }?.let {
            commandLine.add("--max-events")
            commandLine.add(it)
        }
        commandLine.add("--no-open")
        commandLine.add("--")
        commandLine.add(targetFilePath)
        return SkeletonRunCommand(
            commandLine = commandLine,
            projectRoot = projectRoot,
            outputDirectory = outputDirectory,
            sessionPath = outputDirectory.resolve("session.json"),
        )
    }

    private fun resolveOutputDirectory(projectRoot: Path, rawOutputDirectory: String): Path {
        val configuredPath = Paths.get(rawOutputDirectory.trim().ifEmpty { ".skeleton/pycharm/latest" })
        val resolvedPath = if (configuredPath.isAbsolute) configuredPath else projectRoot.resolve(configuredPath)
        return resolvedPath.toAbsolutePath().normalize()
    }

    private fun parseCommand(rawCommand: String, fallback: String): List<String> {
        val command = rawCommand.trim().ifEmpty { fallback }
        return ParametersListUtil.parse(command)
    }

    private fun splitPatterns(rawPatterns: String): List<String> =
        rawPatterns.split(',', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
