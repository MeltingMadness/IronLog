package com.ironlog.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "meta_plan_skips",
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
    indices = [
        Index("metaPlanId"),
        Index("trainingPlanId"),
        Index(value = ["metaPlanId", "trainingPlanId"])
    ]
)
data class MetaPlanSkipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metaPlanId: Long,
    val trainingPlanId: Long,
    val skippedAt: Long // epoch millis
)
