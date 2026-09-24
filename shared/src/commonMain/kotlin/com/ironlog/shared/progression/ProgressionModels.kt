package com.ironlog.shared.progression

import kotlinx.serialization.Serializable

/**
 * Portable representation of the progression configuration.
 *
 * The fields intentionally mirror the backup schema rather than the Android
 * domain sealed hierarchy.  This lets the common evaluator fail closed for a
 * malformed or newer payload without making the iOS framework know about
 * Android-only model classes.
 */
@Serializable
data class ProgressionConfigInput(
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
    val ruleRevision: Int = 1,
    /** Non-null values are storage/parser failures and make the config invalid. */
    val storageReason: String? = null,
    /** Retains an unknown scheme for diagnostics when a payload is malformed. */
    val rawScheme: String = scheme
)

@Serializable
data class ProgressionTargetInput(
    val sets: Int,
    val reps: Int,
    val weightKg: Double
)

@Serializable
data class ProgressionPlanTargetInput(
    val id: Long,
    val sessionId: Long,
    val planId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val supersetGroupId: Int? = null,
    val target: ProgressionTargetInput,
    val config: ProgressionConfigInput
)

/** A set input that contains no platform date/time type. */
@Serializable
data class ProgressionSetInput(
    val id: Long,
    val sessionId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val reps: Int,
    val weightKg: Double,
    val setType: String = "NORMAL",
    val completedAtEpochMillis: Long = 0L,
    /**
     * A sub-millisecond ordering token. Android uses the source timestamp's
     * nanoseconds; backup callers can use the set id. It is only consulted
     * after set number and epoch time, before id.
     */
    val orderingToken: Long = 0L,
    val rpe: Double? = null,
    val planTargetSnapshotId: Long? = null
)

@Serializable
data class ProgressionPreviousOutcomeInput(
    val sourceTarget: ProgressionTargetInput,
    val streakEffect: ProgressionStreakEffect
)

@Serializable
data class ProgressionInput(
    val sourceTarget: ProgressionPlanTargetInput,
    val setsForTarget: List<ProgressionSetInput>,
    val previousComparableOutcomesNewestFirst: List<ProgressionPreviousOutcomeInput> = emptyList()
)

@Serializable
enum class ProgressionOutcomeType {
    PROPOSE_CHANGE,
    KEEP_TARGET,
    INSUFFICIENT_DATA,
    NOT_APPLICABLE
}

@Serializable
enum class ProgressionStreakEffect {
    INCREMENT,
    RESET,
    IGNORE
}

@Serializable
enum class ProgressionReasonCode {
    REP_TARGET_ADVANCED,
    LOAD_ADVANCED,
    TOTAL_REPS_COMPLETED,
    RPE_WITHIN_TARGET,
    REPEAT_TARGET,
    STALL_BACKOFF,
    MANUAL_WEIGHT_DEVIATION,
    TOO_FEW_WORK_SETS,
    RPE_MISSING,
    RPE_INVALID,
    CONFIG_INVALID,
    RULE_REVISION_UNSUPPORTED,
    MANUAL_SCHEME,
    SET_NUMBER_INVALID,
    SET_VALUE_INVALID,
    BACKOFF_FLOOR_REACHED
}

@Serializable
data class ProgressionResult(
    val outcomeType: ProgressionOutcomeType,
    val sourceTarget: ProgressionTargetInput,
    val proposedTarget: ProgressionTargetInput? = null,
    val reasonCode: ProgressionReasonCode,
    val reasonArguments: Map<String, Double> = emptyMap(),
    val streakEffect: ProgressionStreakEffect = ProgressionStreakEffect.IGNORE,
    val countedSetIds: List<Long> = emptyList()
)
