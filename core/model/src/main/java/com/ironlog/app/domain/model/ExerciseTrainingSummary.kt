package com.ironlog.app.domain.model

import java.time.LocalDateTime

/**
 * How often an exercise was trained: number of completed sessions with at least one
 * work set (warm-ups and empty sets excluded) and when the last such set was logged.
 */
data class ExerciseTrainingSummary(
    val exerciseId: Long,
    val sessionCount: Int,
    val lastCompletedAt: LocalDateTime
)
