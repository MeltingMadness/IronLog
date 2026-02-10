package com.ironlog.app.domain.model

data class TrainingPlan(
    val id: Long = 0,
    val name: String,
    val exercises: List<PlanExercise> = emptyList()
)

data class PlanExercise(
    val id: Long = 0,
    val exerciseId: Long,
    val exerciseName: String = "",
    val orderIndex: Int,
    val targetSets: Int = 3,
    val targetReps: Int = 10,
    val targetWeightKg: Double = 0.0
)
