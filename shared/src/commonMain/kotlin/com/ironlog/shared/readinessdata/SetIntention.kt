package com.ironlog.shared.readinessdata

import kotlinx.serialization.Serializable

/**
 * Why a logged set ended the way it did.
 *
 * This is a **separate axis** from the existing Android set classification `SetType.FAILURE`.
 * `SetType.FAILURE` records *how* a set was logged (taken to failure) and must not be re-read as
 * an intention. A `NORMAL` or `DROP_SET` set can still carry [PLANNED_FAILURE]; conversely an
 * [UNEXPECTED_TARGET_MISS] is about missing the planned target and says nothing about the set
 * type. Existing databases and backups predate this field, so every set without an explicit
 * record resolves to [UNKNOWN]; the historical value is preserved instead of being guessed.
 */
@Serializable
enum class SetIntention {
    /** The lifter deliberately trained the set to (technical) failure as part of the plan. */
    PLANNED_FAILURE,

    /** The lifter intended to reach the planned target (reps/weight) but did not. */
    UNEXPECTED_TARGET_MISS,

    /** No information: all legacy sets and every set the user did not classify. */
    UNKNOWN;

    companion object {
        /** Value used for imported or legacy data that never recorded an intention. */
        val LEGACY_DEFAULT: SetIntention = UNKNOWN

        /** Never throws: an unknown persisted string fails closed to [UNKNOWN]. */
        fun safeValueOf(name: String?): SetIntention =
            entries.firstOrNull { it.name == name } ?: UNKNOWN
    }
}

/**
 * Optional intention for exactly one logged set, keyed by the durable `setId`.
 *
 * When no record exists for a set, its intention is [SetIntention.UNKNOWN].
 */
@Serializable
data class SetIntentionRecord(
    val setId: Long,
    val intention: SetIntention,
    val recordedAtEpochMillis: Long? = null,
    val note: String = "",
)

/** Read-only helpers that resolve an intention without ever mutating the source data. */
object SetIntentionSemantics {
    /** Absence of a record always means "unknown", never "normal" and never "failure". */
    fun intentionForMissingRecord(): SetIntention = SetIntention.LEGACY_DEFAULT

    /** Persisted intention for [setId]; a missing record resolves to [SetIntention.UNKNOWN]. */
    fun intentionFor(setId: Long, records: List<SetIntentionRecord>): SetIntention =
        records.firstOrNull { it.setId == setId }?.intention ?: intentionForMissingRecord()
}
