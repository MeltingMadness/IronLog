package com.ironlog.shared.readiness

import kotlinx.serialization.Serializable

/**
 * Platform-neutral, persistence-agnostic input for the training-trend and
 * daily-form heuristic.
 *
 * The core never reads a clock and never interprets a calendar. Adapters pass
 * epoch milliseconds and, when a trailing volume window is wanted, its explicit
 * start. All calendar semantics (week start, DST, "today") stay in the adapter.
 */
@Serializable
data class ReadinessInput(
    val nowEpochMillis: Long,
    /** One row per exercise per session, in any order. */
    val sessions: List<ExerciseSessionInput> = emptyList(),
    /** Today's session, used only to relate muscle groups to the current training. */
    val todaySession: TodaySessionInput? = null,
    /** Completed set load per muscle group and session. */
    val muscleLoadHistory: List<MuscleLoadEntry> = emptyList(),
    /**
     * Start of the trailing volume window supplied by the adapter (for example
     * `now - 7 days`). `null` disables the window and keeps only last-load facts.
     */
    val muscleWindowStartEpochMillis: Long? = null,
    /** Optional daily check-in. `null` means the athlete did not report anything. */
    val checkIn: DailyCheckInInput? = null,
    val thresholds: ReadinessThresholds = ReadinessThresholds.DEFAULT,
)

/** One exercise inside one session, with the sets that were logged for it. */
@Serializable
data class ExerciseSessionInput(
    val sessionId: Long,
    val exerciseId: Long,
    val exerciseName: String = "",
    val equipmentType: EquipmentType = EquipmentType.UNKNOWN,
    val completedAtEpochMillis: Long,
    /**
     * Deterministic tie-breaker for rows that share a timestamp, mirroring the
     * ordering token already used by the shared progression inputs.
     */
    val orderingToken: Long = 0L,
    val slotIndex: Int = 0,
    /**
     * Explicit adapter signal that this exercise moved to a different slot
     * since the previous comparable unit. Treated as a neutral reset.
     */
    val slotChangedSincePrevious: Boolean = false,
    val deloadState: DeloadState = DeloadState.NONE,
    val goal: ProgressionGoal = ProgressionGoal(),
    val sets: List<TrendSetInput> = emptyList(),
)

/**
 * One already-persisted set.
 *
 * [rpe] stays nullable on purpose: a missing RPE is *unknown*, never a good or
 * a bad value, and it alone never makes the history unusable.
 *
 * [setType] and [intention] are two different axes (see [SetType] and
 * [SetIntentionCodes]) and must not be collapsed into one enum.
 */
@Serializable
data class TrendSetInput(
    val setNumber: Int,
    val reps: Int,
    val weightKg: Double,
    val rpe: Double? = null,
    val setType: SetType = SetType.UNKNOWN,
    /**
     * Canonical `com.ironlog.shared.readinessdata.SetIntention` name:
     * `PLANNED_FAILURE`, `UNEXPECTED_TARGET_MISS` or `UNKNOWN`. Adapters pass
     * `SetIntention.name`. An unrecognized value fails closed to `UNKNOWN`; the
     * core never invents a new intention.
     */
    val intention: String = SetIntentionCodes.UNKNOWN,
    val completedAtEpochMillis: Long = 0L,
    val orderingToken: Long = 0L,
    val id: Long = 0L,
)

/**
 * **How** a set was logged. Mirrors the persisted Android set-classification
 * vocabulary and is deliberately separate from [SetIntentionCodes].
 *
 * `FAILURE` means only "logged to failure". It must never be re-read as a
 * planned intention; a historical `FAILURE` row carries no intention
 * information at all.
 */
@Serializable
enum class SetType {
    NORMAL,
    WARMUP,
    FAILURE,
    DROP_SET,
    UNKNOWN,
}

/**
 * **Why** a set ended the way it did, as portable string codes for the canonical
 * `com.ironlog.shared.readinessdata.SetIntention` enum.
 *
 * The enum itself lives in the parallel `readinessdata` module. The core keeps
 * the canonical names instead of declaring a second nominal type, so an adapter
 * maps with `SetIntention.name` and no alias layer is needed later.
 */
object SetIntentionCodes {
    /** The lifter deliberately trained the set to failure as part of the plan. */
    const val PLANNED_FAILURE = "PLANNED_FAILURE"

    /** The lifter intended to reach the planned target but did not. */
    const val UNEXPECTED_TARGET_MISS = "UNEXPECTED_TARGET_MISS"

    /** No information: all legacy sets and every unclassified set. */
    const val UNKNOWN = "UNKNOWN"

    /** Every canonical intention name. Nothing outside this set is valid. */
    val ALL: Set<String> = setOf(PLANNED_FAILURE, UNEXPECTED_TARGET_MISS, UNKNOWN)

    /** Never throws: an unknown or null code fails closed to [UNKNOWN]. */
    fun normalize(raw: String?): String = if (raw != null && raw in ALL) raw else UNKNOWN
}

/** Device class, used to resolve a comparable default weight step. */
@Serializable
enum class EquipmentType {
    BARBELL,
    DUMBBELL,
    MACHINE,
    CABLE,
    SMITH_MACHINE,
    BODYWEIGHT,
    KETTLEBELL,
    BAND,
    OTHER,
    UNKNOWN,
}

/**
 * The prescribed goal for the exercise in this unit.
 *
 * [weightIncrementKg] is the smallest achievable load step for this exercise and
 * is always an explicit, adapter-configured value. The core does **not** invent a
 * step per equipment type: `null` means the step is unknown, so goal-reset
 * detection is skipped. A dumbbell series such as 4 / 5.5 / 7 / ... is expressed
 * by configuring `1.5` explicitly, never by assuming it.
 */
@Serializable
data class ProgressionGoal(
    val targetWeightKg: Double? = null,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetRpe: Double? = null,
    val weightIncrementKg: Double? = null,
    val scheme: ProgressionScheme = ProgressionScheme.UNKNOWN,
)

/** Prescribed progression family used to select the comparison metric; never assumed. */
@Serializable
enum class ProgressionScheme {
    DOUBLE_PROGRESSION,
    LINEAR_LOAD,
    TOTAL_REPS,
    RPE_TARGET,
    MANUAL,
    UNKNOWN,
}

/**
 * The explicitly configured weight step, or `null` when it is unknown.
 *
 * A non-positive or non-finite configured value is treated as unknown rather
 * than silently repaired.
 */
fun ProgressionGoal.explicitWeightStepKg(): Double? =
    weightIncrementKg?.takeIf { it.isFinite() && it > 0.0 }

/**
 * Whether a comparable unit happened inside a planned deload or directly after
 * one. Both are removed from the comparison series.
 */
@Serializable
enum class DeloadState {
    NONE,
    PLANNED_DELOAD,
    POST_DELOAD,
    UNKNOWN,
}

/**
 * Optional daily check-in on a documented 1..5 product scale.
 *
 * Every field is optional and no field is ever summed into a total score.
 */
@Serializable
data class DailyCheckInInput(
    val reportedAtEpochMillis: Long,
    /** 1..5, higher is better. */
    val sleepQuality: Int? = null,
    /** 1..5, higher is better. */
    val energy: Int? = null,
    /** 1..5, higher is worse. */
    val stress: Int? = null,
    /** 1..5, higher is worse. */
    val soreness: Int? = null,
)

/** Today's session, used to relate muscle groups to the current training. */
@Serializable
data class TodaySessionInput(
    val sessionId: Long,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null,
    val muscleGroups: List<String> = emptyList(),
    val sorenessByMuscle: Map<String, Int> = emptyMap(),
)

/** Completed set load for one muscle group in one session. */
@Serializable
data class MuscleLoadEntry(
    val muscleGroup: String,
    val sessionId: Long,
    val completedAtEpochMillis: Long,
    val completedSets: Double,
    val soreness: Int? = null,
)

/** Root result of the neutral readiness core. */
@Serializable
data class ReadinessAssessment(
    val generatedAtEpochMillis: Long,
    val trainingTrend: TrainingTrendAssessment,
    /** `null` when no check-in was provided. */
    val dailyForm: DailyFormAssessment? = null,
    val muscleGroups: List<MuscleGroupContext> = emptyList(),
    val thresholdsRevision: Int,
)

/** Multi-week training trend across comparable exercise units. */
@Serializable
data class TrainingTrendAssessment(
    val status: TrainingTrendStatus,
    /** Heuristic training index, 0..100. `null` while evidence is insufficient. */
    val trainingIndex: Int? = null,
    val confidence: EvidenceConfidence,
    val deloadActive: Boolean,
    /** Suggestion only; the core never mutates a plan. */
    val deloadSuggested: Boolean,
    val analyzedExerciseCount: Int,
    val exercisesWithSufficientHistory: Int,
    val repeatedDeclineExerciseCount: Int,
    val exercises: List<ExerciseTrend> = emptyList(),
    val dataQuality: TrendDataQuality,
    val reasons: List<ReadinessReason> = emptyList(),
    val thresholdsRevision: Int,
)

/** Aggregate trend classification. */
@Serializable
enum class TrainingTrendStatus {
    INSUFFICIENT_DATA,
    NO_NOTABLE_STRAIN,
    SINGLE_EXERCISE_DECLINE,
    MULTIPLE_EXERCISE_DECLINE,
}

/** Per-exercise trend classification. */
@Serializable
enum class ExerciseTrendStatus {
    IMPROVING,
    STABLE,
    DECLINING,
    INSUFFICIENT_DATA,
    EXCLUDED,
}

/** Which comparable metric the exercise trend is built on. */
@Serializable
enum class TrendMetricKind {
    /** Load-driven estimate; only used within one consistent rep band. */
    ESTIMATED_ONE_REP_MAX,
    /** Achieved load discounted for missing prescribed repetitions (linear-load progression). */
    GOAL_SCORE,
    /** Unloaded work measured in repetitions. */
    TOTAL_REPS,
    NONE,
}

/** Confidence attached to a signal or an aggregate. */
@Serializable
enum class EvidenceConfidence {
    NONE,
    LOW,
    MODERATE,
    HIGH,
}

/** One exercise's trend against its own comparable history. */
@Serializable
data class ExerciseTrend(
    val exerciseId: Long,
    val exerciseName: String,
    val equipmentType: EquipmentType,
    val metricKind: TrendMetricKind,
    val status: ExerciseTrendStatus,
    val comparableUnitCount: Int,
    val latestMetricValue: Double? = null,
    /** Own baseline outside the recent decline window; never a weekly best. */
    val baselineMetricValue: Double? = null,
    val changePercent: Double? = null,
    val repeatedDeclineCount: Int = 0,
    val confidence: EvidenceConfidence,
    val excludedFromFatigue: Boolean = false,
    val reasons: List<ReadinessReason> = emptyList(),
)

/** Explainable, render-agnostic reason. Arguments are machine-readable. */
@Serializable
data class ReadinessReason(
    val code: ReadinessReasonCode,
    val arguments: Map<String, Double> = emptyMap(),
)

/**
 * Reason vocabulary for trend, daily form and muscle context.
 *
 * UI layers render localized text from these codes; the core never emits prose.
 */
@Serializable
enum class ReadinessReasonCode {
    // Aggregate trend.
    INSUFFICIENT_HISTORY,
    NO_NOTABLE_STRAIN,
    SINGLE_EXERCISE_DECLINE,
    MULTIPLE_EXERCISE_DECLINES,
    DELOAD_IN_PROGRESS,
    DELOAD_SUGGESTED,
    STAGNATION_NEUTRAL,
    PLANNED_FAILURE_NEUTRAL,
    DELOAD_CONTEXT_NEUTRAL,
    MISSING_RPE_NEUTRAL,
    UNKNOWN_INTENTION_PRESENT,
    UNEXPECTED_TARGET_MISS_PRESENT,

    // Per-exercise trend.
    COMPARABLE_UNITS_BELOW_MINIMUM,
    NO_VALID_WORK_SETS,
    DELOAD_UNITS_EXCLUDED,
    EQUIPMENT_CHANGED_RESET,
    SLOT_CHANGED_RESET,
    GOAL_CHANGED_RESET,
    LONG_GAP_RESET,
    REPEATED_NOTABLE_DECLINE,
    SINGLE_NOTABLE_DECLINE,
    WITHIN_TREND_BAND,
    IMPROVING_TREND,
    MISSING_RPE_REDUCES_CONFIDENCE,
    UNKNOWN_INTENTION_REDUCES_CONFIDENCE,
    PLANNED_FAILURE_SETS_PRESENT,
    UNEXPECTED_TARGET_MISS_SETS_PRESENT,
    /** Comparison metric family changed; a new series starts instead of a false drop. */
    METRIC_KIND_CHANGED_RESET,
    /** Same metric, different repetition band; not directly comparable. */
    REP_BAND_CHANGED_RESET,
    /** Different work-set count; not directly comparable. */
    SET_COUNT_CHANGED_RESET,
    /** Repetition target changed; identical performance must not look like fatigue. */
    REP_TARGET_CHANGED_RESET,
    /** Planned-failure unit: its delta is neutral, never "unexpected deterioration". */
    PLANNED_FAILURE_UNIT_NEUTRAL,
    /** Several rows of the same exercise in one session were merged into one unit. */
    MULTIPLE_SLOTS_IN_SESSION_MERGED,
    /** Target RPE was applied as a bounded adjustment to the comparison metric. */
    TARGET_RPE_APPLIED,
    /** Metric was computed with the TOTAL_REPS scheme (sum of work-set repetitions). */
    TOTAL_REPS_SCHEME,
    /** Metric was computed with the LINEAR_LOAD scheme (prescribed load adherence). */
    LINEAR_LOAD_SCHEME,
    /** Metric was computed with the DOUBLE_PROGRESSION scheme (reps first, then load). */
    DOUBLE_PROGRESSION_SCHEME,

    // Daily form.
    NO_CHECK_IN,
    PARTIAL_CHECK_IN,
    CHECK_IN_ALL_NEUTRAL,
    SLEEP_BELOW_THRESHOLD,
    ENERGY_BELOW_THRESHOLD,
    STRESS_ABOVE_THRESHOLD,
    SORENESS_ABOVE_THRESHOLD,

    // Muscle context.
    MUSCLE_TRAINED_TODAY,
    MUSCLE_HIGH_SORENESS,
    MUSCLE_HIGH_RECENT_VOLUME,
    MUSCLE_LOW_RECENT_VOLUME,
    MUSCLE_NO_RECENT_LOAD,
}

/** Daily-form result. Deliberately has no aggregate score. */
@Serializable
data class DailyFormAssessment(
    /** Number of provided dimensions, 0..4. */
    val coverage: Int,
    val confidence: EvidenceConfidence,
    val hasAnyConcern: Boolean,
    val signals: List<DailyFormSignal> = emptyList(),
    val reasons: List<ReadinessReason> = emptyList(),
)

/**
 * One reported daily-form dimension.
 *
 * The [state] fully describes the classification, so no threshold code is
 * attached here. Concern and coverage codes live in
 * [DailyFormAssessment.reasons].
 */
@Serializable
data class DailyFormSignal(
    val dimension: DailyFormDimension,
    val value: Int,
    val state: DailyFormState,
)

/** Daily-form dimensions. */
@Serializable
enum class DailyFormDimension {
    SLEEP_QUALITY,
    ENERGY,
    STRESS,
    SORENESS,
}

/** Classification of a single reported dimension. */
@Serializable
enum class DailyFormState {
    GOOD,
    NEUTRAL,
    CONCERN,
    UNKNOWN,
}

/**
 * Muscle-group facts for today's training.
 *
 * Facts only: no recovery percentage, no "ready in X hours" estimate.
 */
@Serializable
data class MuscleGroupContext(
    val muscleGroup: String,
    /** This group belongs to the caller-selected or active training for today. */
    val plannedToday: Boolean = false,
    val lastTrainedEpochMillis: Long? = null,
    /** Completed sets inside the adapter-supplied window. */
    val setsInWindow: Double = 0.0,
    val setsToday: Double = 0.0,
    val soreness: Int? = null,
    val flags: List<MuscleGroupFlag> = emptyList(),
)

/** Plain-language flags for a muscle group; not a readiness score. */
@Serializable
enum class MuscleGroupFlag {
    TRAINED_TODAY,
    HIGH_SORENESS,
    HIGH_RECENT_VOLUME,
    LOW_RECENT_VOLUME,
    NO_RECENT_LOAD,
}

/** Explicit data-quality summary. Missing data is named, never silently good. */
@Serializable
data class TrendDataQuality(
    val comparableUnitCount: Int,
    val analyzedExerciseCount: Int,
    val exercisesWithSufficientHistory: Int,
    val insufficientDataExerciseCount: Int,
    val missingRpeSetCount: Int,
    val unknownIntentionSetCount: Int,
    val excludedDeloadUnitCount: Int,
    val notes: List<ReadinessDataQualityNote> = emptyList(),
)

/** Data-quality notes surfaced to the adapter. */
@Serializable
enum class ReadinessDataQualityNote {
    MISSING_RPE_TREATED_NEUTRAL,
    UNKNOWN_SET_INTENTION,
    UNKNOWN_SET_TYPE,
    MIXED_EQUIPMENT_HISTORY,
    EXERCISE_SLOT_CHANGED,
    GOAL_RESET_DETECTED,
    DELOAD_UNITS_EXCLUDED,
    COMPARABILITY_BREAK,
    MULTIPLE_SLOTS_IN_SESSION,
    REP_TARGET_CHANGED,
    INSUFFICIENT_HISTORY,
    NO_VALID_WORK_SETS,
    WEIGHT_STEP_UNKNOWN,
}
