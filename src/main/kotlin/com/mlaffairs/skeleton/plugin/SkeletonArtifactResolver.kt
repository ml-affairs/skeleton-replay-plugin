package com.mlaffairs.skeleton.plugin

import java.net.URI
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

sealed class SkeletonArtifactResolution {
    data class Session(val sessionPath: Path) : SkeletonArtifactResolution()
    data class StandaloneReport(val reportPath: Path) : SkeletonArtifactResolution()
}

class SkeletonArtifactResolver {
    fun resolve(rawPath: String): SkeletonArtifactResolution? =
        parsePath(rawPath)?.let(::resolve)

    fun resolve(path: Path): SkeletonArtifactResolution? {
        val normalizedPath = path.toAbsolutePath().normalize()
        val sessionPath = sessionPathFor(normalizedPath)
        if (sessionPath != null) {
            return SkeletonArtifactResolution.Session(sessionPath)
        }
        if (isReportFile(normalizedPath) && Files.exists(normalizedPath)) {
            return SkeletonArtifactResolution.StandaloneReport(normalizedPath)
        }
        return null
    }

    private fun sessionPathFor(path: Path): Path? {
        if (Files.isDirectory(path)) {
            return listOf(path.resolve("session.json"), path.parent?.resolve("session.json"))
                .filterNotNull()
                .firstOrNull { Files.exists(it) }
        }
        if (!Files.exists(path)) {
            return null
        }
        if (isSessionFile(path)) {
            return path
        }
        return path.parent?.resolve("session.json")?.takeIf { Files.exists(it) }
    }

    private fun parsePath(rawPath: String): Path? {
        val trimmed = rawPath.trim().trim('"', '\'')
        if (trimmed.isEmpty()) {
            return null
        }
        if (trimmed.startsWith("file://")) {
            return runCatching { Path.of(URI(trimmed)) }.getOrNull()
        }
        val expanded = if (trimmed == "~" || trimmed.startsWith("~/")) {
            System.getProperty("user.home") + trimmed.removePrefix("~")
        } else {
            trimmed
        }
        return try {
            Path.of(expanded)
        } catch (_: InvalidPathException) {
            null
        }
    }

    private fun isSessionFile(path: Path): Boolean = path.fileName?.toString() == "session.json"

    private fun isReportFile(path: Path): Boolean = path.fileName?.toString() == "report.html"
}
