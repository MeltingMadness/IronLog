package com.thrym.app.domain.model

import java.time.LocalDateTime

data class PersonalRecord(
    val id: Long = 0,
    val exerciseId: Long,
    val type: RecordType,
    val value: Double,
    val achievedAt: LocalDateTime
)

/** Persisted code; UI labels come from `labelRes()` in core/designsystem. */
enum class RecordType {
    MAX_WEIGHT,
    MAX_REPS,
    MAX_VOLUME,
    MAX_E1RM;

    companion object {
        fun safeValueOf(name: String, fallback: RecordType = MAX_WEIGHT): RecordType =
            try {
                valueOf(name)
            } catch (_: IllegalArgumentException) {
                fallback
            }
    }
}
