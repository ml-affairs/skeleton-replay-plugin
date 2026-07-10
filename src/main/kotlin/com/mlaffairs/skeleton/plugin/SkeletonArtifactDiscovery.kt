package com.mlaffairs.skeleton.plugin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText

data class SkeletonDiscoveredReport(
    val sessionPath: Path,
    val reportPath: Path?,
    val targetLabel: String,
    val targetDetailLabel: String?,
    val artifactLabel: String,
    val events: Int?,
    val targetExitCode: Int?,
    val modifiedTime: Instant,
    val status: SkeletonDiscoveredReportStatus,
)

enum class SkeletonDiscoveredReportStatus {
    LOADABLE,
    REPORT_MISSING,
    MALFORMED_SESSION,
}

class SkeletonArtifactDiscovery {
    private val json = Json { ignoreUnknownKeys = true }

    fun discover(projectRoot: Path): List<SkeletonDiscoveredReport> {
        val normalizedRoot = projectRoot.toAbsolutePath().normalize()
        if (!normalizedRoot.exists() || !Files.isDirectory(normalizedRoot)) {
            return emptyList()
        }
        val sessionPaths = linkedSetOf<Path>()
        findTestsDirectories(normalizedRoot).forEach { testsDirectory ->
            sessionPaths.addAll(findSkeletonSessionsUnder(testsDirectory))
        }
        sessionPaths.addAll(findProjectSkeletonSessions(normalizedRoot))
        return sessionPaths
            .mapNotNull { toDiscoveredReport(it, normalizedRoot) }
            .sortedWith(
                compareByDescending<SkeletonDiscoveredReport> { it.modifiedTime }
                    .thenBy { it.sessionPath.toString() },
            )
    }

    private fun findTestsDirectories(projectRoot: Path): List<Path> {
        val testsDirectories = mutableListOf<Path>()
        Files.walkFileTree(
            projectRoot,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (dir != projectRoot && shouldIgnore(dir.name)) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    if (dir.name == "tests") {
                        testsDirectories.add(dir)
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    return FileVisitResult.CONTINUE
                }
            },
        )
        return testsDirectories
    }

    private fun findSkeletonSessionsUnder(root: Path): List<Path> {
        if (!root.exists() || !Files.isDirectory(root)) {
            return emptyList()
        }
        val sessionPaths = mutableListOf<Path>()
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (dir != root && shouldIgnore(dir.name)) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (file.name == "session.json" && file.hasPathElement(".skeleton")) {
                        sessionPaths.add(file.toAbsolutePath().normalize())
                    }
                    return FileVisitResult.CONTINUE
                }
            },
        )
        return sessionPaths
    }

    private fun findProjectSkeletonSessions(projectRoot: Path): List<Path> =
        findSkeletonSessionsUnder(projectRoot.resolve(".skeleton"))

    private fun toDiscoveredReport(sessionPath: Path, projectRoot: Path): SkeletonDiscoveredReport? {
        val modifiedTime = runCatching { Files.getLastModifiedTime(sessionPath).toInstant() }
            .getOrDefault(Instant.EPOCH)
        val siblingReport = sessionPath.parent?.resolve("report.html")?.takeIf { it.exists() }
        val parsed = runCatching { json.parseToJsonElement(sessionPath.readText()).jsonObject }.getOrNull()
        if (parsed == null) {
            return siblingReport?.let {
                SkeletonDiscoveredReport(
                    sessionPath = sessionPath,
                    reportPath = it,
                    targetLabel = "Unreadable session.json",
                    targetDetailLabel = null,
                    artifactLabel = concisePath(sessionPath.parent ?: sessionPath, projectRoot),
                    events = null,
                    targetExitCode = null,
                    modifiedTime = modifiedTime,
                    status = SkeletonDiscoveredReportStatus.MALFORMED_SESSION,
                )
            }
        }

        val reportPath = reportPath(parsed)?.takeIf { it.exists() } ?: siblingReport
        val status = if (reportPath == null) {
            SkeletonDiscoveredReportStatus.REPORT_MISSING
        } else {
            SkeletonDiscoveredReportStatus.LOADABLE
        }
        val targetDisplay = targetDisplay(parsed, projectRoot, sessionPath)
        return SkeletonDiscoveredReport(
            sessionPath = sessionPath,
            reportPath = reportPath,
            targetLabel = targetDisplay.title,
            targetDetailLabel = targetDisplay.detail,
            artifactLabel = concisePath(sessionPath.parent ?: sessionPath, projectRoot),
            events = parsed.objectAt("metrics")?.intAt("events"),
            targetExitCode = parsed.intAt("target_exit_code"),
            modifiedTime = modifiedTime,
            status = status,
        )
    }

    private fun targetDisplay(manifest: JsonObject, projectRoot: Path, sessionPath: Path): TargetDisplay {
        val target = manifest.objectAt("target")
        val kind = target?.stringAt("kind") ?: manifest.stringAt("command") ?: "report"
        val path = target?.stringAt("path")
        val displayPath = path?.let { concisePath(it, projectRoot) }
            ?: concisePath(sessionPath.parent ?: sessionPath, projectRoot)
        val shortPath = path?.let { runCatching { Path.of(it).fileName?.toString() }.getOrNull() }
            ?: Path.of(displayPath).fileName?.toString()
            ?: displayPath
        return TargetDisplay(
            title = "$shortPath (${kind.replace("_", " ")})",
            detail = "target $displayPath".takeIf { displayPath != shortPath },
        )
    }

    private fun reportPath(manifest: JsonObject): Path? =
        manifest.objectAt("artifacts")
            ?.stringAt("report")
            ?.let { runCatching { Path.of(it).toAbsolutePath().normalize() }.getOrNull() }

    private fun shouldIgnore(name: String): Boolean =
        name in ignoredDirectoryNames

    private fun concisePath(rawPath: String, projectRoot: Path): String =
        runCatching {
            val path = Path.of(rawPath)
            val normalizedPath = if (path.isAbsolute) {
                path.normalize()
            } else {
                projectRoot.resolve(path).normalize()
            }
            if (normalizedPath.startsWith(projectRoot)) {
                projectRoot.relativize(normalizedPath).toString()
            } else {
                path.fileName?.toString() ?: rawPath
            }
        }.getOrDefault(rawPath)

    private fun concisePath(path: Path, projectRoot: Path): String =
        runCatching {
            val normalizedPath = path.toAbsolutePath().normalize()
            if (normalizedPath.startsWith(projectRoot)) {
                projectRoot.relativize(normalizedPath).toString()
            } else {
                path.fileName?.toString() ?: path.toString()
            }
        }
            .getOrDefault(path.toString())

    private fun Path.hasPathElement(name: String): Boolean =
        any { it.toString() == name }

    private fun JsonObject.objectAt(name: String): JsonObject? =
        get(name)?.let { runCatching { it.jsonObject }.getOrNull() }

    private fun JsonObject.stringAt(name: String): String? =
        get(name)?.jsonPrimitive?.contentOrNull

    private fun JsonObject.intAt(name: String): Int? =
        get(name)?.jsonPrimitive?.intOrNull

    companion object {
        private val ignoredDirectoryNames = setOf(
            ".git",
            ".venv",
            "venv",
            "build",
            "dist",
            "node_modules",
            ".idea",
            ".gradle",
            "__pycache__",
        )
    }

    private data class TargetDisplay(val title: String, val detail: String?)
}
