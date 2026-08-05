package com.ironlog.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ironlog.app.domain.model.PlanExercise

@Entity(
    tableName = "plan_exercises",
    foreignKeys = [
        ForeignKey(
            entity = TrainingPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("planId"), Index("exerciseId")]
)
data class PlanExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val supersetGroupId: Int? = null,
    val targetSets: Int = 3,
    val targetReps: Int = 10,
    val targetWeightKg: Double = 0.0
) {
    fun toDomain(): PlanExercise = PlanExercise(
        id = id,
        exerciseId = exerciseId,
        orderIndex = orderIndex,
        supersetGroupId = supersetGroupId,
        targetSets = targetSets,
        targetReps = targetReps,
        targetWeightKg = targetWeightKg
    )

    companion object {
        fun fromDomain(planId: Long, exercise: PlanExercise): PlanExerciseEntity =
            PlanExerciseEntity(
                id = exercise.id,
                planId = planId,
                exerciseId = exercise.exerciseId,
                orderIndex = exercise.orderIndex,
                supersetGroupId = exercise.supersetGroupId,
                targetSets = exercise.targetSets,
                targetReps = exercise.targetReps,
                targetWeightKg = exercise.targetWeightKg
            )
    }
}
