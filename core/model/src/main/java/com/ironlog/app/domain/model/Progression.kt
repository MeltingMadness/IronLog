package com.ironlog.app.domain.model

const val CURRENT_PROGRESSION_RULE_REVISION = 1

enum class ProgressionScheme { MANUAL, LINEAR, DOUBLE, TOTAL_REPS, RPE_RIR }

data class WeightStep(
    val originalValue: Double,
    val originalUnit: UnitSystem,
    val kilograms: Double
)

data class FailurePolicy(
    val stallThreshold: Int = 2,
    val backoffPercent: Double = 10.0
)

sealed interface ProgressionConfig {
    val scheme: ProgressionScheme
    val ruleRevision: Int

    data class Manual(
        override val ruleRevision: Int = CURRENT_PROGRESSION_RULE_REVISION
    ) : ProgressionConfig {
        override val scheme = ProgressionScheme.MANUAL
    }

    data class Linear(
        val step: WeightStep,
        val failurePolicy: FailurePolicy = FailurePolicy(),
        override val ruleRevision: Int = CURRENT_PROGRESSION_RULE_REVISION
    ) : ProgressionConfig {
        override val scheme = ProgressionScheme.LINEAR
    }

    data class DoubleProgression(
        val minReps: Int,
        val maxReps: Int,
        val step: WeightStep,
        val failurePolicy: FailurePolicy = FailurePolicy(),
        override val ruleRevision: Int = CURRENT_PROGRESSION_RULE_REVISION
    ) : ProgressionConfig {
        override val scheme = ProgressionScheme.DOUBLE
    }

    data class TotalReps(
        val targetTotalReps: Long,
        val step: WeightStep,
        val failurePolicy: FailurePolicy = FailurePolicy(),
        override val ruleRevision: Int = CURRENT_PROGRESSION_RULE_REVISION
    ) : ProgressionConfig {
        override val scheme = ProgressionScheme.TOTAL_REPS
    }

    data class RpeRir(
        val targetRpe: Double,
        val tolerance: Double,
        val step: WeightStep,
        val failurePolicy: FailurePolicy = FailurePolicy(),
        override val ruleRevision: Int = CURRENT_PROGRESSION_RULE_REVISION
    ) : ProgressionConfig {
        override val scheme = ProgressionScheme.RPE_RIR
    }

    data class Invalid(
        override val scheme: ProgressionScheme,
        override val ruleRevision: Int,
        val storageReason: String,
        val rawScheme: String = scheme.name
    ) : ProgressionConfig
}

data class ProgressionTarget(val sets: Int, val reps: Int, val weightKg: Double)

data class WorkoutPlanTarget(
    val id: Long,
    val sessionId: Long,
    val planId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val supersetGroupId: Int?,
    val target: ProgressionTarget,
    val config: ProgressionConfig
)

enum class ProgressionOutcomeType { PROPOSE_CHANGE, KEEP_TARGET, INSUFFICIENT_DATA, NOT_APPLICABLE }
enum class ProgressionStreakEffect { INCREMENT, RESET, IGNORE }

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

data class PreviousProgressionOutcome(
    val sourceTarget: WorkoutPlanTarget,
    val streakEffect: ProgressionStreakEffect
)

data class ProgressionContext(
    val sourceTarget: WorkoutPlanTarget,
    val setsForTarget: List<WorkoutSet>,
    val previousComparableOutcomesNewestFirst: List<PreviousProgressionOutcome>
)

sealed interface ProgressionOutcome {
    val type: ProgressionOutcomeType
    val sourceTarget: ProgressionTarget
    val reasonCode: ProgressionReasonCode
    val reasonArguments: Map<String, Double>
    val streakEffect: ProgressionStreakEffect
    val countedSetIds: List<Long>

    data class ProposeChange(
        override val sourceTarget: ProgressionTarget,
        val proposedTarget: ProgressionTarget,
        override val reasonCode: ProgressionReasonCode,
        override val reasonArguments: Map<String, Double> = emptyMap(),
        override val streakEffect: ProgressionStreakEffect,
        override val countedSetIds: List<Long> = emptyList()
    ) : ProgressionOutcome {
        override val type = ProgressionOutcomeType.PROPOSE_CHANGE
    }

    data class KeepTarget(
        override val sourceTarget: ProgressionTarget,
        override val reasonCode: ProgressionReasonCode,
        override val reasonArguments: Map<String, Double> = emptyMap(),
        override val streakEffect: ProgressionStreakEffect,
        override val countedSetIds: List<Long> = emptyList()
    ) : ProgressionOutcome {
        override val type = ProgressionOutcomeType.KEEP_TARGET
    }

    data class InsufficientData(
        override val sourceTarget: ProgressionTarget,
        override val reasonCode: ProgressionReasonCode,
        override val reasonArguments: Map<String, Double> = emptyMap(),
        override val streakEffect: ProgressionStreakEffect = ProgressionStreakEffect.IGNORE,
        override val countedSetIds: List<Long> = emptyList()
    ) : ProgressionOutcome {
        override val type = ProgressionOutcomeType.INSUFFICIENT_DATA
    }

    data class NotApplicable(
        override val sourceTarget: ProgressionTarget,
        override val reasonCode: ProgressionReasonCode = ProgressionReasonCode.MANUAL_SCHEME,
        override val reasonArguments: Map<String, Double> = emptyMap(),
        override val streakEffect: ProgressionStreakEffect = ProgressionStreakEffect.IGNORE,
        override val countedSetIds: List<Long> = emptyList()
    ) : ProgressionOutcome {
        override val type = ProgressionOutcomeType.NOT_APPLICABLE
    }
}

enum class ProgressionSuggestionStatus { PENDING, ACCEPTED, REJECTED, STALE, INFORMATIONAL }

data class ProgressionSuggestion(
    val id: Long,
    val sourceTarget: WorkoutPlanTarget,
    val outcome: ProgressionOutcome,
    val countedSets: List<WorkoutSet>,
    val status: ProgressionSuggestionStatus,
    val wasEdited: Boolean,
    val finalTarget: ProgressionTarget?,
    val createdAtEpochMillis: Long,
    val decidedAtEpochMillis: Long?
) {
    init {
        require(outcome.sourceTarget == sourceTarget.target)
    }
}

data class ProgressionGenerationResult(
    val insertedCount: Int,
    val reviewItemCount: Int,
    val pendingCount: Int
)

sealed interface ProgressionDecisionResult {
    data class Accepted(val suggestionIds: Set<Long>) : ProgressionDecisionResult
    data class Stale(val suggestionIds: Set<Long>) : ProgressionDecisionResult
    data class Invalid(val message: String) : ProgressionDecisionResult
}
