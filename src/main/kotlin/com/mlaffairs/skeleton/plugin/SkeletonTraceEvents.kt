package com.mlaffairs.skeleton.plugin

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class SkeletonTraceEvent(
    val event_type: String,
    val order: Long,
    val depth: Int = 0,
    val callee: SkeletonReplayEndpoint,
    val caller: SkeletonReplayEndpoint? = null,
    val call_id: Long? = null,
    val args: JsonObject? = null,
    val return_value: JsonObject? = null,
)

class SkeletonTraceIndex private constructor(
    private val eventsByOrder: Map<Long, SkeletonTraceEvent>,
    private val callsById: Map<Long, SkeletonTraceEvent>,
    private val fallbackCallsByReturnOrder: Map<Long, SkeletonTraceEvent>,
) {
    fun eventFor(selection: SkeletonReplaySelectionPayload): SkeletonTraceEvent? =
        eventsByOrder[selection.event_order]

    fun pairedCallFor(returnEvent: SkeletonTraceEvent): SkeletonTraceEvent? {
        if (returnEvent.event_type != "return") {
            return null
        }
        return returnEvent.call_id?.let(callsById::get) ?: fallbackCallsByReturnOrder[returnEvent.order]
    }

    fun pairedCallFor(selection: SkeletonReplaySelectionPayload): SkeletonTraceEvent? =
        eventFor(selection)?.let(::pairedCallFor)

    companion object {
        val EMPTY = SkeletonTraceIndex(emptyMap(), emptyMap(), emptyMap())

        fun fromEvents(events: List<SkeletonTraceEvent>): SkeletonTraceIndex {
            val byOrder = events.associateBy { event -> event.order }
            val callsById = events
                .asSequence()
                .filter { event -> event.event_type == "call" && event.call_id != null }
                .associateBy { event -> event.call_id ?: -1L }
            val fallbackPairs = mutableMapOf<Long, SkeletonTraceEvent>()
            val activeCalls = mutableListOf<SkeletonTraceEvent>()
            for (event in events.sortedBy { candidate -> candidate.order }) {
                when (event.event_type) {
                    "call" -> activeCalls.add(event)
                    "return" -> {
                        val matchIndex = activeCalls.indexOfLast { call -> call.callee.focusKey() == event.callee.focusKey() }
                        if (matchIndex >= 0) {
                            fallbackPairs[event.order] = activeCalls.removeAt(matchIndex)
                        }
                    }
                }
            }
            return SkeletonTraceIndex(byOrder, callsById, fallbackPairs)
        }
    }
}

object SkeletonTraceInlayFormatter {
    fun textForSelection(selection: SkeletonReplaySelectionPayload, traceIndex: SkeletonTraceIndex?): String? {
        val event = traceIndex?.eventFor(selection)
        val args = when {
            event?.event_type == "call" -> event.args
            event?.event_type == "return" -> traceIndex.pairedCallFor(event)?.args
            selection.event_type == "call" -> null
            else -> null
        }
        val returnValue = event?.return_value
        val parts = mutableListOf<String>()
        formatArgs(args)?.let(parts::add)
        if (selection.event_type == "return" && returnValue != null) {
            parts.add("return -> ${summaryText(returnValue)}")
        }
        if (parts.isEmpty()) {
            return null
        }
        return "# ${parts.joinToString("; ")}"
    }

    private fun formatArgs(args: JsonObject?): String? {
        if (args == null || args.isEmpty()) {
            return null
        }
        return args.entries.joinToString(", ") { (name, value) -> "$name = ${summaryText(value)}" }
    }

    private fun summaryText(value: JsonElement): String {
        if (value is JsonPrimitive) {
            value.booleanOrNull?.let { return it.toString() }
            value.contentOrNull?.let { return if (value.toString().startsWith("\"")) "\"$it\"" else it }
            return value.toString()
        }
        if (value is JsonObject) {
            val type = value["type"]?.jsonPrimitive?.contentOrNull
            if (type == "redacted") {
                return "<redacted>"
            }
            value["value"]?.let { return summaryText(it) }
            val length = value["len"]?.jsonPrimitive?.contentOrNull
            if (type != null && length != null) {
                return "$type(len=$length)"
            }
            if (type != null) {
                return type
            }
        }
        return value.toString().replace(Regex("\\s+"), " ").take(MAX_SUMMARY_CHARS)
    }

    private const val MAX_SUMMARY_CHARS = 96
}
