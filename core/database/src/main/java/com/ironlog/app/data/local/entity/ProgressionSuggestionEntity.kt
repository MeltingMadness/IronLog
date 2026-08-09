package com.ironlog.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "progression_suggestions",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutPlanTargetEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceTargetSnapshotId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceSessionId"],
            onDelete = ForeignKey.CASCADE
        ),
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
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("sourceSessionId"),
        Index("planId"),
        Index("exerciseId"),
        Index(
            value = ["sourceTargetSnapshotId", "sourceProgressionRuleRevision"],
            unique = true
        ),
        Index("status")
    ]
)
data class ProgressionSuggestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceSessionId: Long,
    val sourceTargetSnapshotId: Long,
    val planId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val supersetGroupId: Int?,
    @Embedded(prefix = "source") val sourceTarget: ProgressionTargetColumns,
    @Embedded(prefix = "sourceProgression") val sourceProgression: ProgressionConfigColumns,
    val outcomeType: String,
    val reasonCode: String,
    val reasonArgumentsJson: String,
    val countedSetIdsJson: String,
    val streakEffect: String,
    @Embedded(prefix = "suggested") val suggestedTarget: ProgressionTargetColumns?,
    val status: String,
    val wasEdited: Boolean,
    @Embedded(prefix = "final") val finalTarget: ProgressionTargetColumns?,
    val createdAtEpochMillis: Long,
    val decidedAtEpochMillis: Long?
)
