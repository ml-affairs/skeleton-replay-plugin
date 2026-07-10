package com.mlaffairs.skeleton.plugin

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkeletonArtifactDiscoveryTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun findsSkeletonSessionsUnderTestsDirectories() {
        val sessionPath = writeSession(tempDir.resolve("pkg/tests/.skeleton/example/latest"), "tests/test_orders.py")

        val reports = SkeletonArtifactDiscovery().discover(tempDir)

        assertEquals(listOf(sessionPath), reports.map { it.sessionPath })
        assertEquals("test_orders.py (pytest)", reports.single().targetLabel)
        assertEquals("target tests/test_orders.py", reports.single().targetDetailLabel)
    }

    @Test
    fun findsProjectRootSkeletonSessions() {
        val sessionPath = writeSession(tempDir.resolve(".skeleton/pycharm/latest"), "app.py")

        val reports = SkeletonArtifactDiscovery().discover(tempDir)

        assertEquals(listOf(sessionPath), reports.map { it.sessionPath })
    }

    @Test
    fun displaysAbsoluteTargetPathsRelativeToProjectRoot() {
        val targetPath = tempDir.resolve("tests/test_absolute.py").toString()
        writeSession(tempDir.resolve("tests/.skeleton/absolute/latest"), targetPath)

        val reports = SkeletonArtifactDiscovery().discover(tempDir)

        assertEquals("test_absolute.py (pytest)", reports.single().targetLabel)
        assertEquals("target tests/test_absolute.py", reports.single().targetDetailLabel)
    }

    @Test
    fun ignoresBuildCacheAndVendorDirectories() {
        writeSession(tempDir.resolve("build/tests/.skeleton/run/latest"), "ignored_build.py")
        writeSession(tempDir.resolve("node_modules/pkg/tests/.skeleton/run/latest"), "ignored_vendor.py")
        writeSession(tempDir.resolve(".venv/tests/.skeleton/run/latest"), "ignored_venv.py")

        val reports = SkeletonArtifactDiscovery().discover(tempDir)

        assertTrue(reports.isEmpty())
    }

    @Test
    fun sortsNewestSessionsFirstThenPath() {
        val oldSession = writeSession(tempDir.resolve("a/tests/.skeleton/old/latest"), "old.py")
        val newestB = writeSession(tempDir.resolve("b/tests/.skeleton/new/latest"), "new_b.py")
        val newestA = writeSession(tempDir.resolve("a/tests/.skeleton/new/latest"), "new_a.py")
        Files.setLastModifiedTime(oldSession, FileTime.from(Instant.parse("2026-01-01T00:00:00Z")))
        Files.setLastModifiedTime(newestB, FileTime.from(Instant.parse("2026-01-03T00:00:00Z")))
        Files.setLastModifiedTime(newestA, FileTime.from(Instant.parse("2026-01-03T00:00:00Z")))

        val reports = SkeletonArtifactDiscovery().discover(tempDir)

        assertEquals(listOf(newestA, newestB, oldSession), reports.map { it.sessionPath })
    }

    @Test
    fun listsMalformedSessionOnlyWhenSiblingReportExists() {
        val skipped = tempDir.resolve("skip/tests/.skeleton/run/latest").also { it.createDirectories() }.resolve("session.json")
        skipped.writeText("not json")
        val listedDir = tempDir.resolve("list/tests/.skeleton/run/latest").also { it.createDirectories() }
        val listed = listedDir.resolve("session.json").also { it.writeText("not json") }
        listedDir.resolve("report.html").writeText("<html></html>")

        val reports = SkeletonArtifactDiscovery().discover(tempDir)

        assertEquals(listOf(listed), reports.map { it.sessionPath })
        assertEquals(SkeletonDiscoveredReportStatus.MALFORMED_SESSION, reports.single().status)
    }

    private fun writeSession(directory: Path, targetPath: String): Path {
        directory.createDirectories()
        val reportPath = directory.resolve("report.html")
        reportPath.writeText("<html></html>")
        val sessionPath = directory.resolve("session.json")
        sessionPath.writeText(
            """
            {
              "schema_version": 1,
              "skeleton_version": "0.7.0",
              "command": "pytest",
              "invocation": ["python", "-m", "skeleton_replay", "pytest"],
              "project_root": "${escapeJson(tempDir.toString())}",
              "target": {
                "kind": "pytest",
                "path": "$targetPath",
                "args": []
              },
              "artifacts": {
                "trace": "${escapeJson(directory.resolve("trace.jsonl").toString())}",
                "snapshot": "${escapeJson(directory.resolve("snapshot.json").toString())}",
                "workflow": "${escapeJson(directory.resolve("workflow.md").toString())}",
                "quality": "${escapeJson(directory.resolve("quality.json").toString())}",
                "quality_markdown": "${escapeJson(directory.resolve("architecture_quality.md").toString())}",
                "session": "${escapeJson(sessionPath.toString())}",
                "report": "${escapeJson(reportPath.toString())}"
              },
              "metrics": {
                "events": 17,
                "nodes": 4,
                "edges": 3
              },
              "target_exit_code": 0,
              "target_error": null,
              "report_opened": false
            }
            """.trimIndent(),
        )
        return sessionPath.toAbsolutePath().normalize()
    }

    private fun escapeJson(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
