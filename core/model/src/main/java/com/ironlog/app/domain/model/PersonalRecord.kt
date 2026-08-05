package com.ironlog.app.domain.model

import java.time.LocalDateTime

data class PersonalRecord(
    val id: Long = 0,
    val exerciseId: Long,
    val type: RecordType,
    val value: Double,
    val achievedAt: LocalDateTime
)

enum class RecordType(val displayName: String) {
    MAX_WEIGHT("Max Gewicht"),
    MAX_REPS("Max Wiederholungen"),
    MAX_VOLUME("Max Volumen"),
    MAX_E1RM("Bester geschaetzter 1RM");

    companion object {
        fun safeValueOf(name: String, fallback: RecordType = MAX_WEIGHT): RecordType =
            try {
                valueOf(name)
            } catch (_: IllegalArgumentException) {
                fallback
            }
    }
}
