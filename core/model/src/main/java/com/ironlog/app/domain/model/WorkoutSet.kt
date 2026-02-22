package com.ironlog.app.domain.model

import java.time.LocalDateTime

data class WorkoutSet(
    val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val reps: Int,
    val weightKg: Double,
    val isWarmup: Boolean = false,
    val completedAt: LocalDateTime = LocalDateTime.now(),
    val rpe: Double? = null
)
