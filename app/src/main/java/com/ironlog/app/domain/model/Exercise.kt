package com.ironlog.app.domain.model

data class Exercise(
    val id: Long = 0,
    val name: String,
    val primaryMuscleGroup: MuscleGroup,
    val secondaryMuscleGroups: List<MuscleGroup> = emptyList(),
    val category: ExerciseCategory,
    val isCustom: Boolean = false
)
