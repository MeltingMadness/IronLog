package com.ironlog.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.ExerciseCategory
import com.ironlog.app.domain.model.MuscleGroup

@Entity(
    tableName = "exercises",
    indices = [Index("isArchived")]
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val primaryMuscleGroup: String,
    val secondaryMuscleGroups: String, // comma-separated
    val category: String,
    val isCustom: Boolean = false,
    val notes: String = "",
    val isArchived: Boolean = false
) {
    fun toDomain(): Exercise = Exercise(
        id = id,
        name = name,
        primaryMuscleGroup = MuscleGroup.safeValueOf(primaryMuscleGroup),
        secondaryMuscleGroups = if (secondaryMuscleGroups.isBlank()) emptyList()
            else secondaryMuscleGroups.split(",").mapNotNull { raw ->
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) null
                else MuscleGroup.safeValueOf(trimmed)
            },
        category = ExerciseCategory.safeValueOf(category),
        isCustom = isCustom,
        notes = notes,
        isArchived = isArchived
    )

    companion object {
        fun fromDomain(exercise: Exercise): ExerciseEntity = ExerciseEntity(
            id = exercise.id,
            name = exercise.name,
            primaryMuscleGroup = exercise.primaryMuscleGroup.name,
            secondaryMuscleGroups = exercise.secondaryMuscleGroups.joinToString(",") { it.name },
            category = exercise.category.name,
            isCustom = exercise.isCustom,
            notes = exercise.notes,
            isArchived = exercise.isArchived
        )
    }
}
