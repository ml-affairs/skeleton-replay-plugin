package com.mlaffairs.skeleton.plugin

import java.awt.Component
import java.awt.Container
import java.awt.event.MouseEvent
import java.nio.file.Path
import java.time.Instant
import javax.swing.AbstractButton
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkeletonStartupReportPanelTest {
    @Test
    fun rendersDiscoveredSessionsAndClickDelegatesToLoadPath() {
        val sessionPath = Path.of("/project/tests/.skeleton/orders/latest/session.json")
        val clicked = mutableListOf<Path>()
        val component = SkeletonStartupReportPanel.startScreen(
            reports = listOf(
                SkeletonDiscoveredReport(
                    sessionPath = sessionPath,
                    reportPath = Path.of("/project/tests/.skeleton/orders/latest/report.html"),
                    targetLabel = "test_orders.py (pytest)",
                    targetDetailLabel = "target tests/test_orders.py",
                    artifactLabel = "tests/.skeleton/orders/latest",
                    events = 42,
                    targetExitCode = 0,
                    modifiedTime = Instant.parse("2026-07-10T10:15:30Z"),
                    status = SkeletonDiscoveredReportStatus.LOADABLE,
                ),
            ),
            onManualLoad = {},
            onRescan = {},
            onPyPi = {},
            onLoadSession = { clicked.add(it) },
        )

        assertTrue(component.allText().any { it == "test_orders.py (pytest)" })
        assertTrue(component.allText().any { it == "target tests/test_orders.py" })
        assertTrue(component.allText().any { it == "artifact tests/.skeleton/orders/latest" })
        assertTrue(component.allText().any { it.contains("42 events") })

        component.componentContaining("test_orders.py").clickMouse()

        assertEquals(listOf(sessionPath), clicked)
    }

    @Test
    fun noArtifactStateIncludesInstallGuidanceAndManualLoadButton() {
        var manualLoads = 0
        var rescans = 0
        val component = SkeletonStartupReportPanel.startScreen(
            reports = emptyList(),
            onManualLoad = { manualLoads += 1 },
            onRescan = { rescans += 1 },
            onPyPi = {},
            onLoadSession = {},
        )

        val text = component.allText().joinToString("\n")
        assertTrue(text.contains("No .skeleton reports found"))
        assertTrue(text.contains("Install skeleton-replay on PyPI"))
        assertTrue(text.contains("python -m pip install skeleton-replay"))
        assertTrue(text.contains("Ask your LLM CLI to add a scenario test with skeleton-replay"))

        component.buttonContaining("Load Artifact").doClick()
        component.buttonContaining("Scan Reports").doClick()

        assertEquals(1, manualLoads)
        assertEquals(1, rescans)
    }

    private fun Component.allText(): List<String> {
        val result = mutableListOf<String>()
        fun visit(component: Component) {
            when (component) {
                is AbstractButton -> result.add(component.text)
                is JLabel -> result.add(component.text)
            }
            if (component is Container) {
                component.components.forEach(::visit)
            }
        }
        visit(this)
        return result
    }

    private fun Component.buttonContaining(text: String): AbstractButton {
        fun visit(component: Component): AbstractButton? {
            if (component is AbstractButton && component.text.contains(text)) {
                return component
            }
            if (component is Container) {
                component.components.forEach { child ->
                    visit(child)?.let { return it }
                }
            }
            return null
        }
        return requireNotNull(visit(this)) { "No button containing $text" }
    }

    private fun Component.componentContaining(text: String): Component {
        fun visit(component: Component): Component? {
            when (component) {
                is AbstractButton -> if (component.text.contains(text)) return component
                is JLabel -> if (component.text.contains(text)) return component
            }
            if (component is Container) {
                component.components.forEach { child ->
                    visit(child)?.let { return it }
                }
            }
            return null
        }
        return requireNotNull(visit(this)) { "No component containing $text" }
    }

    private fun Component.clickMouse() {
        val event = MouseEvent(this, MouseEvent.MOUSE_CLICKED, 0L, 0, 1, 1, 1, false)
        mouseListeners.forEach { listener -> listener.mouseClicked(event) }
    }
}
