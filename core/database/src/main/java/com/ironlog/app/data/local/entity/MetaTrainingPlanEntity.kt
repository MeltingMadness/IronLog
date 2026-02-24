package com.ironlog.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ironlog.app.domain.model.MetaTrainingPlan

@Entity(tableName = "meta_training_plans")
data class MetaTrainingPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
) {
    fun toDomain(items: List<MetaPlanItemEntity> = emptyList()): MetaTrainingPlan = MetaTrainingPlan(
        id = id,
        name = name,
        items = items.map { it.toDomain() }
    )

    companion object {
        fun fromDomain(plan: MetaTrainingPlan): MetaTrainingPlanEntity = MetaTrainingPlanEntity(
            id = plan.id,
            name = plan.name,
            createdAt = EpochConverter.toLong(java.time.LocalDateTime.now())
        )
    }
}
