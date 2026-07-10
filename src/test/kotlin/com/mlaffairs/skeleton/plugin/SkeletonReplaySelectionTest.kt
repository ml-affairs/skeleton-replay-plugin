package com.mlaffairs.skeleton.plugin

import kotlinx.serialization.json.Json
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
              "call_id": 3,
              "endpoint": {
                "module": "checkout",
                "function": "reserve",
                "qualified_name": "checkout.CheckoutService.reserve",
                "file": "/project/checkout.py",
                "line": 24,
                "node_id": "function:checkout.CheckoutService.reserve",
                "class_name": "CheckoutService",
                "endpoint_type": "function",
                "callable_kind": "instance_method"
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
        assertEquals(3L, payload.call_id)
        assertEquals("/project/checkout.py", payload.focusedEndpoint?.file)
        assertEquals(24, payload.focusedEndpoint?.line)
        assertEquals("checkout.CheckoutService.reserve", payload.focusedEndpoint?.qualified_name)
        assertEquals("instance_method", payload.focusedEndpoint?.callable_kind)
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
    fun returnEventsStayOnFocusedEndpointWhenControlReturnsToDifferentSourceContext() {
        val focused = endpoint("/project/checkout.py", module = "checkout", function = "reserve")
        val caller = endpoint("/project/orders.py", module = "orders", function = "main")
        val resolver = SkeletonReplayEndpointResolver { endpoint -> endpoint.file?.startsWith("/project/") == true }

        val resolved = resolver.resolve(selection(focusedEndpoint = focused, caller = caller, eventType = "return"))

        assertSame(focused, resolved)
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

    @Test
    fun indexesTraceEventsByOrderAndPairsReturnsByCallId() {
        val call = traceEvent(
            """
            {"event_type":"call","order":4,"depth":0,"call_id":4,"callee":{"module":"orders","function":"main","qualified_name":"orders.main","file":"/project/orders.py","line":12,"node_id":"function:orders.main","endpoint_type":"function","callable_kind":"module_function"},"args":{"order_id":{"type":"str","value":"A-1"}}}
            """.trimIndent(),
        )
        val returned = traceEvent(
            """
            {"event_type":"return","order":5,"depth":0,"call_id":4,"callee":{"module":"orders","function":"main","qualified_name":"orders.main","file":"/project/orders.py","line":12,"node_id":"function:orders.main","endpoint_type":"function","callable_kind":"module_function"},"return_value":{"type":"bool","value":true}}
            """.trimIndent(),
        )
        val index = SkeletonTraceIndex.fromEvents(listOf(call, returned))

        assertSame(returned, index.eventFor(selection(eventOrder = 5, eventType = "return")))
        assertSame(call, index.pairedCallFor(returned))
        assertEquals("# order_id = \"A-1\"; return -> true", SkeletonTraceInlayFormatter.textForSelection(selection(eventOrder = 5, eventType = "return"), index))
    }

    @Test
    fun pairsOldTraceReturnsWithoutCallIdsByNearestMatchingCallee() {
        val firstCall = traceEvent(
            """
            {"event_type":"call","order":0,"depth":0,"callee":{"module":"orders","function":"outer","qualified_name":"orders.outer","file":"/project/orders.py","line":12,"node_id":"function:orders.outer","endpoint_type":"function"},"args":{"name":{"type":"str","value":"outer"}}}
            """.trimIndent(),
        )
        val nestedCall = traceEvent(
            """
            {"event_type":"call","order":1,"depth":1,"callee":{"module":"orders","function":"inner","qualified_name":"orders.inner","file":"/project/orders.py","line":20,"node_id":"function:orders.inner","endpoint_type":"function"},"args":{"name":{"type":"str","value":"inner"}}}
            """.trimIndent(),
        )
        val nestedReturn = traceEvent(
            """
            {"event_type":"return","order":2,"depth":1,"callee":{"module":"orders","function":"inner","qualified_name":"orders.inner","file":"/project/orders.py","line":20,"node_id":"function:orders.inner","endpoint_type":"function"},"return_value":{"type":"str","value":"done"}}
            """.trimIndent(),
        )
        val firstReturn = traceEvent(
            """
            {"event_type":"return","order":3,"depth":0,"callee":{"module":"orders","function":"outer","qualified_name":"orders.outer","file":"/project/orders.py","line":12,"node_id":"function:orders.outer","endpoint_type":"function"},"return_value":{"type":"str","value":"done"}}
            """.trimIndent(),
        )
        val index = SkeletonTraceIndex.fromEvents(listOf(firstCall, nestedCall, nestedReturn, firstReturn))

        assertSame(nestedCall, index.pairedCallFor(nestedReturn))
        assertSame(firstCall, index.pairedCallFor(firstReturn))
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
            callable_kind = "module_function",
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

    private fun traceEvent(json: String): SkeletonTraceEvent =
        Json { ignoreUnknownKeys = true }.decodeFromString(json)
}
