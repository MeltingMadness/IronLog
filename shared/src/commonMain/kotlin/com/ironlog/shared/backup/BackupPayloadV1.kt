package com.ironlog.shared.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupPayloadV1(
    val formatVersion: Int,
    val schemaVersion: Int,
    val appVersion: String,
    val exportedAtEpochMillis: Long,
    val exercises: List<BackupExercise>,
    val workoutSessions: List<BackupWorkoutSession>,
    val workoutSets: List<BackupWorkoutSet>,
    val trainingPlans: List<BackupTrainingPlan>,
    val planExercises: List<BackupPlanExercise>,
    val personalRecords: List<BackupPersonalRecord>,
    val metaTrainingPlans: List<BackupMetaTrainingPlan> = emptyList(),
    val metaPlanItems: List<BackupMetaPlanItem> = emptyList(),
    val metaPlanSkips: List<BackupMetaPlanSkip> = emptyList(),
    val workoutPlanTargets: List<BackupWorkoutPlanTarget> = emptyList(),
    val progressionSuggestions: List<BackupProgressionSuggestion> = emptyList()
)

@Serializable
data class BackupExercise(
    val id: Long,
    val name: String,
    val primaryMuscleGroup: String,
    val secondaryMuscleGroups: String,
    val category: String,
    val isCustom: Boolean,
    val notes: String = "",
    val isArchived: Boolean = false
)

@Serializable
data class BackupWorkoutSession(
    val id: Long,
    val startTime: Long,
    val endTime: Long?,
    val durationSeconds: Long,
    val name: String,
    val notes: String,
    val planId: Long? = null,
    val metaPlanId: Long? = null
)

@Serializable
data class BackupWorkoutSet(
    val id: Long,
    val sessionId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val reps: Int,
    val weightKg: Double,
    val isWarmup: Boolean,
    val completedAt: Long,
    val rpe: Double? = null,
    val planTargetSnapshotId: Long? = null
)

@Serializable
data class BackupTrainingPlan(
    val id: Long,
    val name: String,
    val createdAt: Long
)

@Serializable
data class BackupPlanExercise(
    val id: Long,
    val planId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val supersetGroupId: Int? = null,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Double,
    val progression: BackupProgressionConfig = BackupProgressionConfig()
)

@Serializable
data class BackupProgressionConfig(
    val scheme: String = "MANUAL",
    val incrementValue: Double? = null,
    val incrementUnit: String? = null,
    val incrementKg: Double? = null,
    val minReps: Int? = null,
    val maxReps: Int? = null,
    val targetTotalReps: Long? = null,
    val targetRpe: Double? = null,
    val rpeTolerance: Double? = null,
    val stallThreshold: Int = 2,
    val backoffPercent: Double = 10.0,
    val ruleRevision: Int = 1
)

@Serializable
data class BackupProgressionTarget(val sets: Int, val reps: Int, val weightKg: Double)

@Serializable
data class BackupWorkoutPlanTarget(
    val id: Long,
    val sessionId: Long,
    val planId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val supersetGroupId: Int? = null,
    val target: BackupProgressionTarget,
    val progression: BackupProgressionConfig
)

@Serializable
data class BackupProgressionSuggestion(
    val id: Long,
    val sourceSessionId: Long,
    val sourceTargetSnapshotId: Long,
    val planId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val supersetGroupId: Int? = null,
    val sourceTarget: BackupProgressionTarget,
    val sourceProgression: BackupProgressionConfig,
    val outcomeType: String,
    val reasonCode: String,
    val reasonArguments: Map<String, Double> = emptyMap(),
    val countedSetIds: List<Long> = emptyList(),
    val streakEffect: String,
    val suggestedTarget: BackupProgressionTarget? = null,
    val status: String,
    val wasEdited: Boolean = false,
    val finalTarget: BackupProgressionTarget? = null,
    val createdAtEpochMillis: Long,
    val decidedAtEpochMillis: Long? = null
)

@Serializable
data class BackupPersonalRecord(
    val id: Long,
    val exerciseId: Long,
    val type: String,
    val value: Double,
    val achievedAt: Long
)

@Serializable
data class BackupMetaTrainingPlan(
    val id: Long,
    val name: String,
    val createdAt: Long
)

@Serializable
data class BackupMetaPlanItem(
    val id: Long,
    val metaPlanId: Long,
    val trainingPlanId: Long,
    val orderIndex: Int
)

@Serializable
data class BackupMetaPlanSkip(
    val id: Long,
    val metaPlanId: Long,
    val trainingPlanId: Long,
    val skippedAt: Long
)
