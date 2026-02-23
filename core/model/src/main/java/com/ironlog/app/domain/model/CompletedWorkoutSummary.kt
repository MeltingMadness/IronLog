package com.ironlog.app.domain.model

data class CompletedWorkoutSummary(
    val session: WorkoutSession,
    val exerciseCount: Int,
    val setCount: Int,
    val totalVolume: Double
)
