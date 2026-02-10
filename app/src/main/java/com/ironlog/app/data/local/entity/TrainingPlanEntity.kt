package com.ironlog.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ironlog.app.domain.model.TrainingPlan

@Entity(tableName = "training_plans")
data class TrainingPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long // epoch millis
) {
    fun toDomain(exercises: List<PlanExerciseEntity> = emptyList()): TrainingPlan = TrainingPlan(
        id = id,
        name = name,
        exercises = exercises.map { it.toDomain() }
    )

    companion object {
        fun fromDomain(plan: TrainingPlan): TrainingPlanEntity = TrainingPlanEntity(
            id = plan.id,
            name = plan.name,
            createdAt = EpochConverter.toLong(java.time.LocalDateTime.now())
        )
    }
}
