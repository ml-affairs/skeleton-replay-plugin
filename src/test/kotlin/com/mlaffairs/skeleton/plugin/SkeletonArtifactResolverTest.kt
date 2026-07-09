package com.mlaffairs.skeleton.plugin

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SkeletonArtifactResolverTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun resolvesSessionFile() {
        val sessionPath = artifactDir().resolve("session.json").also { it.writeText("{}") }

        val resolved = SkeletonArtifactResolver().resolve(sessionPath)

        assertEquals(sessionPath, assertIs<SkeletonArtifactResolution.Session>(resolved).sessionPath)
    }

    @Test
    fun resolvesReportToSiblingSessionWhenAvailable() {
        val dir = artifactDir()
        val sessionPath = dir.resolve("session.json").also { it.writeText("{}") }
        val reportPath = dir.resolve("report.html").also { it.writeText("<html></html>") }

        val resolved = SkeletonArtifactResolver().resolve(reportPath)

        assertEquals(sessionPath, assertIs<SkeletonArtifactResolution.Session>(resolved).sessionPath)
    }

    @Test
    fun resolvesStandaloneReportWhenNoSessionExists() {
        val reportPath = artifactDir().resolve("report.html").also { it.writeText("<html></html>") }

        val resolved = SkeletonArtifactResolver().resolve(reportPath)

        assertEquals(reportPath, assertIs<SkeletonArtifactResolution.StandaloneReport>(resolved).reportPath)
    }

    @Test
    fun resolvesSiblingArtifactToSession() {
        val dir = artifactDir()
        val sessionPath = dir.resolve("session.json").also { it.writeText("{}") }
        val workflowPath = dir.resolve("workflow.md").also { it.writeText("# Workflow") }

        val resolved = SkeletonArtifactResolver().resolve(workflowPath)

        assertEquals(sessionPath, assertIs<SkeletonArtifactResolution.Session>(resolved).sessionPath)
    }

    @Test
    fun resolvesArtifactDirectoryToSession() {
        val dir = artifactDir()
        val sessionPath = dir.resolve("session.json").also { it.writeText("{}") }

        val resolved = SkeletonArtifactResolver().resolve(dir)

        assertEquals(sessionPath, assertIs<SkeletonArtifactResolution.Session>(resolved).sessionPath)
    }

    @Test
    fun resolvesFileUriText() {
        val sessionPath = artifactDir().resolve("session.json").also { it.writeText("{}") }

        val resolved = SkeletonArtifactResolver().resolve(sessionPath.toUri().toString())

        assertEquals(sessionPath, assertIs<SkeletonArtifactResolution.Session>(resolved).sessionPath)
    }

    @Test
    fun returnsNullForUnknownPath() {
        assertNull(SkeletonArtifactResolver().resolve(tempDir.resolve("missing.txt")))
    }

    private fun artifactDir(): Path = tempDir.resolve(".skeleton").also {
        if (!java.nio.file.Files.exists(it)) {
            it.createDirectory()
        }
    }
}
