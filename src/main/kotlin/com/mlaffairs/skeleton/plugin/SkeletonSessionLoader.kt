package com.mlaffairs.skeleton.plugin

import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

data class SkeletonLoadedSession(
    val manifest: SkeletonSessionManifest,
    val sessionPath: Path,
    val workflowText: String?,
    val qualityMarkdownText: String?,
    val artifactsText: String,
    val reportPath: Path?,
)

class SkeletonSessionLoader {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun load(sessionPath: Path): SkeletonLoadedSession {
        if (!sessionPath.exists()) {
            throw SkeletonSessionLoadException("Skeleton did not write session.json at $sessionPath")
        }
        val manifest = json.decodeFromString<SkeletonSessionManifest>(sessionPath.readText())
        val workflowText = readOptional(manifest.artifacts.workflow)
        val qualityMarkdownText = readOptional(manifest.artifacts.quality_markdown)
        val reportPath = manifest.artifacts.report?.let { Path.of(it) }?.takeIf { Files.exists(it) }
        return SkeletonLoadedSession(
            manifest = manifest,
            sessionPath = sessionPath,
            workflowText = workflowText,
            qualityMarkdownText = qualityMarkdownText,
            artifactsText = artifactsSummary(manifest),
            reportPath = reportPath,
        )
    }

    private fun readOptional(path: String): String? {
        val artifactPath = Path.of(path)
        if (!Files.exists(artifactPath)) {
            return null
        }
        return artifactPath.readText()
    }

    private fun artifactsSummary(manifest: SkeletonSessionManifest): String {
        val lines = mutableListOf<String>()
        lines.add("Skeleton ${manifest.skeleton_version}")
        lines.add("Command: ${manifest.command}")
        lines.add("Invocation: ${manifest.invocation.joinToString(" ")}")
        lines.add("Project root: ${manifest.project_root}")
        lines.add("Target: ${manifest.target.kind}${manifest.target.path?.let { " $it" }.orEmpty()}")
        if (manifest.target.args.isNotEmpty()) {
            lines.add("Target args: ${manifest.target.args.joinToString(" ")}")
        }
        lines.add("")
        lines.add("Metrics")
        lines.add("  Events: ${manifest.metrics.events}")
        lines.add("  Nodes: ${manifest.metrics.nodes}")
        lines.add("  Edges: ${manifest.metrics.edges}")
        lines.add("")
        lines.add("Outcome")
        lines.add("  Target exit code: ${manifest.target_exit_code}")
        lines.add("  Target error: ${manifest.target_error ?: "none"}")
        lines.add("  Report opened by engine: ${manifest.report_opened}")
        lines.add("")
        lines.add("Artifacts")
        lines.add("  session: ${manifest.artifacts.session}")
        lines.add("  trace: ${manifest.artifacts.trace}")
        lines.add("  snapshot: ${manifest.artifacts.snapshot}")
        lines.add("  workflow: ${manifest.artifacts.workflow}")
        lines.add("  quality: ${manifest.artifacts.quality}")
        lines.add("  quality_markdown: ${manifest.artifacts.quality_markdown}")
        lines.add("  report: ${manifest.artifacts.report ?: "not generated"}")
        return lines.joinToString(System.lineSeparator())
    }
}

class SkeletonSessionLoadException(message: String) : RuntimeException(message)
