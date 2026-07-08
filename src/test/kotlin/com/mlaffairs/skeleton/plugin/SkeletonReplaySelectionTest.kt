package com.mlaffairs.skeleton.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SkeletonReplaySelectionTest {
    @Test
    fun decodesReportSelectionPayload() {
        val payload = SkeletonReplaySelectionDecoder().decode(
            """
            {
              "schema_version": 1,
              "event_index": 2,
              "event_order": 7,
              "event_type": "return",
              "endpoint": {
                "module": "checkout",
                "function": "reserve",
                "qualified_name": "checkout.CheckoutService.reserve",
                "file": "/project/checkout.py",
                "line": 24,
                "node_id": "function:checkout.CheckoutService.reserve",
                "class_name": "CheckoutService",
                "endpoint_type": "function"
              },
              "caller": {
                "module": "orders",
                "function": "main",
                "qualified_name": "orders.main",
                "file": "/project/orders.py",
                "line": 12,
                "node_id": "function:orders.main",
                "class_name": null,
                "endpoint_type": "function"
              }
            }
            """.trimIndent(),
        )

        assertEquals(1, payload.schema_version)
        assertEquals(2, payload.event_index)
        assertEquals(7, payload.event_order)
        assertEquals("return", payload.event_type)
        assertEquals("/project/checkout.py", payload.focusedEndpoint?.file)
        assertEquals(24, payload.focusedEndpoint?.line)
        assertEquals("checkout.CheckoutService.reserve", payload.focusedEndpoint?.qualified_name)
        assertEquals("orders.main", payload.caller?.qualified_name)
    }

    @Test
    fun resolvesFocusedEndpointBeforeCallerWhenProjectLocal() {
        val focused = endpoint("/project/checkout.py")
        val caller = endpoint("/project/orders.py")
        val resolver = SkeletonReplayEndpointResolver { endpoint -> endpoint.file?.startsWith("/project/") == true }

        val resolved = resolver.resolve(selection(focused, caller))

        assertSame(focused, resolved)
    }

    @Test
    fun fallsBackToCallerWhenFocusedEndpointIsNotProjectLocal() {
        val focused = endpoint("/external/site-packages/vendor.py")
        val caller = endpoint("/project/orders.py")
        val resolver = SkeletonReplayEndpointResolver { endpoint -> endpoint.file?.startsWith("/project/") == true }

        val resolved = resolver.resolve(selection(focused, caller))

        assertSame(caller, resolved)
    }

    @Test
    fun returnEventsResolveCallerWhenControlReturnsToDifferentSourceContext() {
        val focused = endpoint("/project/checkout.py", module = "checkout", function = "reserve")
        val caller = endpoint("/project/orders.py", module = "orders", function = "main")
        val resolver = SkeletonReplayEndpointResolver { endpoint -> endpoint.file?.startsWith("/project/") == true }

        val resolved = resolver.resolve(selection(focusedEndpoint = focused, caller = caller, eventType = "return"))

        assertSame(caller, resolved)
    }

    @Test
    fun returnEventsStayOnFocusedEndpointWhenCallerIsSameSourceContext() {
        val focused = endpoint("/project/orders.py", module = "orders", function = "main")
        val caller = endpoint("/project/orders.py", module = "orders", function = "main")
        val resolver = SkeletonReplayEndpointResolver { endpoint -> endpoint.file?.startsWith("/project/") == true }

        val resolved = resolver.resolve(selection(focusedEndpoint = focused, caller = caller, eventType = "return"))

        assertSame(focused, resolved)
    }

    @Test
    fun returnsNullWhenNoEndpointHasProjectSource() {
        val resolver = SkeletonReplayEndpointResolver { false }

        val resolved = resolver.resolve(selection(endpoint("/external/vendor.py"), endpoint("/other/orders.py")))

        assertNull(resolved)
    }

    @Test
    fun ignoresDuplicateEventOrderAfterApply() {
        val debouncer = SkeletonReplaySelectionDebouncer()
        val first = selection(eventOrder = 3)
        val duplicate = selection(eventOrder = 3)

        assertTrue(debouncer.submit(first))
        assertSame(first, debouncer.drainLatest())
        assertFalse(debouncer.submit(duplicate))
        assertNull(debouncer.drainLatest())
    }

    @Test
    fun drainsOnlyLatestPendingSelection() {
        val debouncer = SkeletonReplaySelectionDebouncer()
        val first = selection(eventOrder = 3)
        val latest = selection(eventOrder = 4)

        assertTrue(debouncer.submit(first))
        assertTrue(debouncer.submit(latest))

        assertSame(latest, debouncer.drainLatest())
        assertNull(debouncer.drainLatest())
    }

    @Test
    fun resetAllowsPreviouslyAppliedEventOrderAgain() {
        val debouncer = SkeletonReplaySelectionDebouncer()
        val first = selection(eventOrder = 3)

        assertTrue(debouncer.submit(first))
        assertSame(first, debouncer.drainLatest())
        debouncer.reset()
        assertTrue(debouncer.submit(first))
    }

    private fun endpoint(file: String, module: String = "orders", function: String = "main"): SkeletonReplayEndpoint =
        SkeletonReplayEndpoint(
            module = module,
            function = function,
            qualified_name = "$module.$function",
            file = file,
            line = 12,
            node_id = "function:$module.$function",
            endpoint_type = "function",
        )

    private fun selection(
        focusedEndpoint: SkeletonReplayEndpoint? = endpoint("/project/checkout.py"),
        caller: SkeletonReplayEndpoint? = null,
        eventOrder: Long = 1,
        eventType: String = "call",
    ): SkeletonReplaySelectionPayload =
        SkeletonReplaySelectionPayload(
            schema_version = 1,
            event_index = eventOrder.toInt(),
            event_order = eventOrder,
            event_type = eventType,
            focusedEndpoint = focusedEndpoint,
            caller = caller,
        )
}
