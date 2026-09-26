package com.ironlog.app.presentation.workout

import java.time.Instant

private const val REST_TIMER_PAYLOAD_SEPARATOR = "#"
private const val REST_TIMER_STATE_SEPARATOR = ";"
private const val REST_TIMER_FIELD_SEPARATOR = ","
private const val REST_TIMER_PLANNED = "planned"
private const val REST_TIMER_AD_HOC = "adhoc"

internal fun encodeRestTimerState(
    timers: Map<WorkoutExerciseKey, RestTimerUi>,
    sessionStartEpochMillis: Long
): String {
    val records = timers.entries.joinToString(REST_TIMER_STATE_SEPARATOR) { (key, timer) ->
        val keyType = when (key) {
            is WorkoutExerciseKey.Planned -> REST_TIMER_PLANNED
            is WorkoutExerciseKey.AdHoc -> REST_TIMER_AD_HOC
        }
        val keyId = when (key) {
            is WorkoutExerciseKey.Planned -> key.snapshotId
            is WorkoutExerciseKey.AdHoc -> key.exerciseId
        }
        listOf(
            keyType,
            keyId.toString(),
            timer.startTime.toEpochMilli().toString(),
            timer.durationSeconds.toString()
        ).joinToString(REST_TIMER_FIELD_SEPARATOR)
    }
    return sessionStartEpochMillis.toString() + REST_TIMER_PAYLOAD_SEPARATOR + records
}

internal fun decodeRestTimerState(
    encodedState: String,
    expectedSessionStartEpochMillis: Long
): Map<WorkoutExerciseKey, RestTimerUi> {
    val payloadSeparatorIndex = encodedState.indexOf(REST_TIMER_PAYLOAD_SEPARATOR)
    if (payloadSeparatorIndex <= 0) return emptyMap()
    val storedSessionStart = encodedState
        .substring(0, payloadSeparatorIndex)
        .toLongOrNull()
        ?: return emptyMap()
    if (storedSessionStart != expectedSessionStartEpochMillis) return emptyMap()

    val records = encodedState.substring(payloadSeparatorIndex + 1)
    if (records.isBlank()) return emptyMap()
    return records.split(REST_TIMER_STATE_SEPARATOR).mapNotNull { record ->
        val fields = record.split(REST_TIMER_FIELD_SEPARATOR, limit = 4)
        if (fields.size != 4) return@mapNotNull null
        val keyId = fields[1].toLongOrNull() ?: return@mapNotNull null
        val startEpochMillis = fields[2].toLongOrNull() ?: return@mapNotNull null
        val durationSeconds = fields[3].toIntOrNull()?.takeIf { it >= 0 }
            ?: return@mapNotNull null
        val key = when (fields[0]) {
            REST_TIMER_PLANNED -> WorkoutExerciseKey.Planned(keyId)
            REST_TIMER_AD_HOC -> WorkoutExerciseKey.AdHoc(keyId)
            else -> return@mapNotNull null
        }
        key to RestTimerUi(
            startTime = Instant.ofEpochMilli(startEpochMillis),
            durationSeconds = durationSeconds
        )
    }.toMap()
}
