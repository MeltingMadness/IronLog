package com.ironlog.shared.readinessdata

import kotlinx.serialization.Serializable

/** Wire format version of the readiness document (structure, not feature set). */
const val READINESS_DATA_FORMAT_VERSION = 1

/** Schema of the readiness document; bumped when fields are added in a compatible way. */
const val CURRENT_READINESS_DATA_SCHEMA_VERSION = 1

/**
 * Self-contained readiness document.
 *
 * It is deliberately **not** `BackupPayloadV1`: the workout backup owns the canonical training
 * graph and its Room-backed schema, while this document is a small, additive side channel.
 * Keeping it separate means readiness data can be added without changing the workout backup
 * schema or the Room database version.
 */
@Serializable
data class ReadinessData(
    val formatVersion: Int = READINESS_DATA_FORMAT_VERSION,
    val schemaVersion: Int = CURRENT_READINESS_DATA_SCHEMA_VERSION,
    val checkIns: List<ReadinessCheckIn> = emptyList(),
    val setIntentions: List<SetIntentionRecord> = emptyList(),
)
