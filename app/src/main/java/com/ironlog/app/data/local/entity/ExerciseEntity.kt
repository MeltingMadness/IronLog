package com.ironlog.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.ExerciseCategory
import com.ironlog.app.domain.model.MuscleGroup

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val primaryMuscleGroup: String,
    val secondaryMuscleGroups: String, // comma-separated
    val category: String,
    val isCustom: Boolean = false
) {
    fun toDomain(): Exercise = Exercise(
        id = id,
        name = name,
        primaryMuscleGroup = MuscleGroup.valueOf(primaryMuscleGroup),
        secondaryMuscleGroups = if (secondaryMuscleGroups.isBlank()) emptyList()
            else secondaryMuscleGroups.split(",").map { MuscleGroup.valueOf(it.trim()) },
        category = ExerciseCategory.valueOf(category),
        isCustom = isCustom
    )

    companion object {
        fun fromDomain(exercise: Exercise): ExerciseEntity = ExerciseEntity(
            id = exercise.id,
            name = exercise.name,
            primaryMuscleGroup = exercise.primaryMuscleGroup.name,
            secondaryMuscleGroups = exercise.secondaryMuscleGroups.joinToString(",") { it.name },
            category = exercise.category.name,
            isCustom = exercise.isCustom
        )
    }
}
