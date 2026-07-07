package com.mlaffairs.skeleton.plugin

import kotlinx.serialization.Serializable

@Serializable
data class SkeletonSessionManifest(
    val schema_version: Int,
    val skeleton_version: String,
    val command: String,
    val invocation: List<String>,
    val project_root: String,
    val target: SkeletonSessionTarget,
    val artifacts: SkeletonSessionArtifacts,
    val metrics: SkeletonSessionMetrics,
    val target_exit_code: Int,
    val target_error: String?,
    val report_opened: Boolean,
)

@Serializable
data class SkeletonSessionTarget(
    val kind: String,
    val path: String? = null,
    val args: List<String> = emptyList(),
)

@Serializable
data class SkeletonSessionArtifacts(
    val trace: String,
    val snapshot: String,
    val workflow: String,
    val quality: String,
    val quality_markdown: String,
    val session: String,
    val report: String? = null,
)

@Serializable
data class SkeletonSessionMetrics(
    val events: Int,
    val nodes: Int,
    val edges: Int,
)
