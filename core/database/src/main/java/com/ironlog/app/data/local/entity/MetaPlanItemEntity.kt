package com.ironlog.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ironlog.app.domain.model.MetaTrainingPlanItem

@Entity(
    tableName = "meta_plan_items",
    foreignKeys = [
        ForeignKey(
            entity = MetaTrainingPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["metaPlanId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TrainingPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["trainingPlanId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("metaPlanId"), Index("trainingPlanId")]
)
data class MetaPlanItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metaPlanId: Long,
    val trainingPlanId: Long,
    val orderIndex: Int
) {
    fun toDomain(): MetaTrainingPlanItem = MetaTrainingPlanItem(
        id = id,
        trainingPlanId = trainingPlanId,
        orderIndex = orderIndex
    )

    companion object {
        fun fromDomain(metaPlanId: Long, item: MetaTrainingPlanItem): MetaPlanItemEntity =
            MetaPlanItemEntity(
                id = item.id,
                metaPlanId = metaPlanId,
                trainingPlanId = item.trainingPlanId,
                orderIndex = item.orderIndex
            )
    }
}
