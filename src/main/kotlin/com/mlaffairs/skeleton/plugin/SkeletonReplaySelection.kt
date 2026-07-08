package com.mlaffairs.skeleton.plugin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SkeletonReplaySelectionPayload(
    val schema_version: Int,
    val event_index: Int,
    val event_order: Long,
    val event_type: String,
    @SerialName("endpoint")
    val focusedEndpoint: SkeletonReplayEndpoint? = null,
    val caller: SkeletonReplayEndpoint? = null,
)

@Serializable
data class SkeletonReplayEndpoint(
    val module: String? = null,
    val function: String? = null,
    val qualified_name: String? = null,
    val file: String? = null,
    val line: Int? = null,
    val node_id: String? = null,
    val class_name: String? = null,
    val endpoint_type: String? = null,
)

class SkeletonReplaySelectionDecoder {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun decode(payloadJson: String): SkeletonReplaySelectionPayload =
        json.decodeFromString<SkeletonReplaySelectionPayload>(payloadJson)
}

class SkeletonReplayEndpointResolver(
    private val isProjectLocal: (SkeletonReplayEndpoint) -> Boolean,
) {
    fun resolve(selection: SkeletonReplaySelectionPayload): SkeletonReplayEndpoint? {
        val focused = selection.focusedEndpoint
        val caller = selection.caller
        if (
            selection.event_type == "return" &&
            caller != null &&
            caller.hasSource() &&
            isProjectLocal(caller) &&
            caller.focusKey() != focused?.focusKey()
        ) {
            return caller
        }
        return listOf(focused, caller)
            .filterNotNull()
            .firstOrNull { endpoint -> endpoint.file != null && isProjectLocal(endpoint) }
    }

    private fun SkeletonReplayEndpoint.hasSource(): Boolean = file != null
}

fun SkeletonReplayEndpoint.focusKey(): String =
    listOf(file.orEmpty(), module.orEmpty(), class_name.orEmpty(), function.orEmpty(), qualified_name.orEmpty())
        .joinToString("|")

class SkeletonReplaySelectionDebouncer {
    private var pendingSelection: SkeletonReplaySelectionPayload? = null
    private var lastAppliedEventOrder: Long? = null

    fun submit(selection: SkeletonReplaySelectionPayload): Boolean {
        if (selection.event_order == lastAppliedEventOrder || selection.event_order == pendingSelection?.event_order) {
            return false
        }
        pendingSelection = selection
        return true
    }

    fun drainLatest(): SkeletonReplaySelectionPayload? {
        val latest = pendingSelection ?: return null
        pendingSelection = null
        if (latest.event_order == lastAppliedEventOrder) {
            return null
        }
        lastAppliedEventOrder = latest.event_order
        return latest
    }

    fun reset() {
        pendingSelection = null
        lastAppliedEventOrder = null
    }
}
