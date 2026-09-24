package com.ironlog.shared.analytics

import kotlinx.serialization.Serializable

/**
 * Stable, platform-neutral dashboard projection.
 *
 * Dates are ISO-8601 calendar dates in the requested time zone. Epoch values
 * remain available for records because a record is an instant, not only a
 * calendar-day label.
 */
@Serializable
data class TrainingAnalytics(
    val generatedAtEpochMillis: Long,
    val timeZoneId: String,
    val weekStartsSunday: Boolean,
    val currentWeekStart: String,
    val workoutsThisWeek: Int,
    val workoutsThisMonth: Int,
    val lastSessionDate: String?,
    val recentRecords: List<TrainingAnalyticsRecord>,
    val muscleVolumes: List<TrainingMuscleVolume>,
    val weeklyVolume: List<TrainingVolumeBin>,
    val exerciseStatistics: List<TrainingExerciseStatistics>,
    val readiness: TrainingReadiness,
    val metaRotationSuggestions: List<MetaRotationSuggestion>,
)

@Serializable
data class TrainingAnalyticsRecord(
    val id: Long,
    val exerciseId: Long,
    val exerciseName: String?,
    val type: String,
    val value: Double,
    val achievedAtEpochMillis: Long,
    val achievedAt: String,
)

/** Weighted work-set volume for the current calendar week. */
@Serializable
data class TrainingMuscleVolume(
    val muscleGroup: String,
    val weeklySets: Double,
    val mev: Double,
    val mav: Double,
    val mrv: Double,
    val status: TrainingVolumeStatus,
)

@Serializable
enum class TrainingVolumeStatus {
    LOW,
    OPTIMAL,
    HIGH,
}

/** One fixed calendar-week bin in the eight-week trend. */
@Serializable
data class TrainingVolumeBin(
    val weekStart: String,
    val volumeKg: Double,
    val workoutCount: Int,
)

/**
 * Muscle-volume projection for one navigable calendar week.
 *
 * This is intentionally separate from [TrainingAnalytics]: dashboard week
 * navigation can reload a historical week without changing current-week
 * counts, the readiness window, or the eight-week trend in the summary.
 */
@Serializable
data class TrainingWeeklyMuscleVolume(
    val weekStart: String,
    val currentWeekStart: String,
    val weekStartsSunday: Boolean,
    val timeZoneId: String,
    val completedWorkoutCount: Int,
    val volumes: List<TrainingMuscleVolume>,
)

/** Per-exercise session series used by the statistics detail screen. */
@Serializable
data class TrainingExerciseStatistics(
    val exerciseId: Long,
    val exerciseName: String?,
    val sessions: List<TrainingExerciseSessionStatistics>,
    val firstE1rmKg: Double,
    val latestE1rmKg: Double,
    val bestE1rmKg: Double,
    val e1rmDeltaKg: Double,
    val e1rmDeltaPercent: Double,
    val lastWorkoutComparison: TrainingExerciseWorkoutComparison?,
    val recentSets: List<TrainingExerciseSetStatistics>,
)

/**
 * Session aggregate matching Android's ExerciseStatsViewModel:
 * best weight/reps/E1RM among valid work sets and total work-set volume.
 */
@Serializable
data class TrainingExerciseSessionStatistics(
    val sessionId: Long,
    val date: String,
    val completedAtEpochMillis: Long,
    val maxWeightKg: Double,
    val maxReps: Int,
    val maxE1rmKg: Double,
    val volumeKg: Double,
)

/** Last-versus-previous session values used by the exercise detail screen. */
@Serializable
data class TrainingExerciseWorkoutComparison(
    val previous: TrainingExerciseSessionStatistics,
    val latest: TrainingExerciseSessionStatistics,
    val weightDeltaKg: Double,
    val repsDelta: Int,
    val e1rmDeltaKg: Double,
    val e1rmDeltaPercent: Double,
    val volumeDeltaKg: Double,
)

/** One recent, non-warmup work set for the exercise detail screen. */
@Serializable
data class TrainingExerciseSetStatistics(
    val id: Long,
    val sessionId: Long,
    val date: String,
    val completedAtEpochMillis: Long,
    val setType: String,
    val weightKg: Double,
    val reps: Int,
    val e1rmKg: Double,
    val volumeKg: Double,
)

/**
 * Deload/readiness evidence from the same heuristic used by Android.
 *
 * [fatigueScore] and [readinessScore] are null while [status] is
 * [TrainingReadinessStatus.INSUFFICIENT_DATA]. This keeps an unavailable score
 * distinct from a measured zero-fatigue result.
 */
@Serializable
data class TrainingReadiness(
    val status: TrainingReadinessStatus,
    val fatigueScore: Int?,
    val readinessScore: Int?,
    val deloadRecommended: Boolean,
    val signals: List<TrainingReadinessSignal>,
    val windowStart: String,
    val windowEnd: String,
    val sessionCount: Int,
    val minimumSessionCount: Int,
    val analysisWindowWeeks: Int,
    val strongestExerciseId: Long?,
    val strongestExerciseChangePercent: Double?,
    val averageRpe: Double?,
    val failureRate: Double,
    val analyzedCompoundCount: Int,
)

@Serializable
enum class TrainingReadinessStatus {
    INSUFFICIENT_DATA,
    NO_NOTABLE_STRAIN,
    SIGNALS_PRESENT,
}

@Serializable
enum class TrainingReadinessSignal {
    E1RM_STAGNATION,
    E1RM_DROP,
    RPE_CREEP,
    FAILURE_FREQUENCY,
}

/** Next valid sub-plan according to the persisted rotation/skip history. */
@Serializable
data class MetaRotationSuggestion(
    val metaPlanId: Long,
    val metaPlanName: String,
    val nextTrainingPlanId: Long,
    val nextTrainingPlanName: String,
    val orderedTrainingPlanIds: List<Long>,
    val canSkip: Boolean,
)
