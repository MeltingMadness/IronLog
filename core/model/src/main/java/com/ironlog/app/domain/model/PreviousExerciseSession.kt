package com.ironlog.app.domain.model

import java.time.LocalDateTime

data class PreviousExerciseSession(
    val sessionId: Long,
    val sessionStart: LocalDateTime,
    val sets: List<WorkoutSet>,
    val lastWorkSetWeightKg: Double?
)
