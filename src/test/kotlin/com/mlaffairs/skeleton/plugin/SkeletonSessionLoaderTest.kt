package com.mlaffairs.skeleton.plugin

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SkeletonSessionLoaderTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun loadsManifestAndLinkedArtifacts() {
        val tracePath = tempDir.resolve("trace.jsonl")
        val snapshotPath = tempDir.resolve("snapshot.json")
        val workflowPath = tempDir.resolve("workflow.md")
        val qualityPath = tempDir.resolve("quality.json")
        val qualityMarkdownPath = tempDir.resolve("architecture_quality.md")
        val reportPath = tempDir.resolve("report.html")
        val sessionPath = tempDir.resolve("session.json")
        tracePath.writeText(
            """
            {"event_type":"call","order":0,"depth":0,"call_id":0,"callee":{"module":"orders","function":"main","qualified_name":"orders.main","file":"${escapeJson(tempDir.resolve("orders.py").toString())}","line":1,"node_id":"function:orders.main","endpoint_type":"function","callable_kind":"module_function"},"args":{"order_id":{"type":"str","value":"A-1"}}}
            {"event_type":"return","order":1,"depth":0,"call_id":0,"callee":{"module":"orders","function":"main","qualified_name":"orders.main","file":"${escapeJson(tempDir.resolve("orders.py").toString())}","line":1,"node_id":"function:orders.main","endpoint_type":"function","callable_kind":"module_function"},"return_value":{"type":"bool","value":true}}
            """.trimIndent(),
        )
        snapshotPath.writeText("{}")
        workflowPath.writeText("# Workflow")
        qualityPath.writeText("{}")
        qualityMarkdownPath.writeText("# Quality")
        reportPath.writeText("<html></html>")
        sessionPath.writeText(
            """
            {
              "schema_version": 1,
              "skeleton_version": "0.7.0",
              "command": "run",
              "invocation": ["skeleton", "run", "app.py"],
              "project_root": "${escapeJson(tempDir.toString())}",
              "target": {
                "kind": "script",
                "path": "${escapeJson(tempDir.resolve("app.py").toString())}",
                "args": []
              },
              "artifacts": {
                "trace": "${escapeJson(tracePath.toString())}",
                "snapshot": "${escapeJson(snapshotPath.toString())}",
                "workflow": "${escapeJson(workflowPath.toString())}",
                "quality": "${escapeJson(qualityPath.toString())}",
                "quality_markdown": "${escapeJson(qualityMarkdownPath.toString())}",
                "session": "${escapeJson(sessionPath.toString())}",
                "report": "${escapeJson(reportPath.toString())}"
              },
              "metrics": {
                "events": 2,
                "nodes": 3,
                "edges": 1
              },
              "target_exit_code": 0,
              "target_error": null,
              "report_opened": false
            }
            """.trimIndent(),
        )

        val loadedSession = SkeletonSessionLoader().load(sessionPath)

        assertEquals("0.7.0", loadedSession.manifest.skeleton_version)
        assertEquals("# Workflow", loadedSession.workflowText)
        assertEquals("# Quality", loadedSession.qualityMarkdownText)
        assertEquals(reportPath, loadedSession.reportPath)
        assertNotNull(loadedSession.artifactsText)
        assertNotNull(
            loadedSession.traceIndex.pairedCallFor(
                SkeletonReplaySelectionPayload(
                    schema_version = 1,
                    event_index = 1,
                    event_order = 1,
                    event_type = "return",
                ),
            ),
        )
    }

    private fun escapeJson(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
