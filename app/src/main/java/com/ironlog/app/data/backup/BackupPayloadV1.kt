package com.ironlog.app.data.backup

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
    val personalRecords: List<BackupPersonalRecord>
)

@Serializable
data class BackupExercise(
    val id: Long,
    val name: String,
    val primaryMuscleGroup: String,
    val secondaryMuscleGroups: String,
    val category: String,
    val isCustom: Boolean
)

@Serializable
data class BackupWorkoutSession(
    val id: Long,
    val startTime: Long,
    val endTime: Long?,
    val durationSeconds: Long,
    val name: String,
    val notes: String
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
    val completedAt: Long
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
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Double
)

@Serializable
data class BackupPersonalRecord(
    val id: Long,
    val exerciseId: Long,
    val type: String,
    val value: Double,
    val achievedAt: Long
)
