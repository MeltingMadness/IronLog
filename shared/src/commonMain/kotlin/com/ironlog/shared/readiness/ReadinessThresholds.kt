package com.ironlog.shared.readiness

import kotlinx.serialization.Serializable

/**
 * Every product threshold used by the readiness core, in one versioned place.
 *
 * These numbers are product heuristics, not measured or clinical limits. They are
 * intentionally conservative, documented in `README.md`, and mirrored into the
 * results via [revision] so an adapter can detect a threshold change.
 *
 * [DEFAULT] is deliberately stable; changing any value requires bumping
 * [revision] and documenting the change.
 */
@Serializable
data class ReadinessThresholds(
    /** Schema/semantics revision of the threshold set. */
    val revision: Int = 3,

    /** Completed history considered for the current training trend. */
    val analysisWindowDays: Int = 28,

    // --- Comparable-history gates -------------------------------------------------

    /** Minimum comparable units before a per-exercise trend is reported. */
    val minComparableUnits: Int = 3,

    /** Number of most recent deltas inspected for repeated decline. */
    val declineWindowUnits: Int = 2,

    /** How many of those deltas must be notable declines to count as "repeated". */
    val repeatedDeclineCount: Int = 2,

    /** |change| within this band is reported as STABLE, not as progress. */
    val trendBandPercent: Double = 2.5,

    /** A single-unit drop at or above this percent is a notable decline. */
    val notableDeclinePercent: Double = 5.0,

    /** A surplus at or above this percent is reported as IMPROVING. */
    val improveThresholdPercent: Double = 2.5,

    /** Longest gap between units still considered one continuous series. */
    val segmentationGapMillis: Long = 14L * 24L * 60L * 60L * 1000L,

    /** Goal drop beyond a full step (minus this tolerance) starts a new series. */
    val goalResetToleranceKg: Double = 0.01,

    /** Reps at or below this bound may be used for an Epley estimate. */
    val maxRepsForE1rmEstimate: Int = 12,

    /** Repetition band width for E1RM comparability (1..3, 4..6, ...). */
    val repBandWidth: Int = 3,

    /** Bounded metric bonus/penalty per RPE point away from the configured target RPE. */
    val rpeAdjustmentPerPointPercent: Double = 1.0,

    /** Upper bound of the RPE adjustment, in percent. */
    val rpeAdjustmentMaxPercent: Double = 5.0,

    /** Upper bound of the goal-relative reps ratio. */
    val maxGoalRepRatio: Double = 1.5,

    // --- Evidence / confidence ----------------------------------------------------

    /** Minimum RPE coverage before confidence is downgraded. */
    val minRpeCoverageForConfidence: Double = 0.5,

    /** Maximum share of unknown set intentions before confidence is downgraded. */
    val maxUnknownIntentionRatioForConfidence: Double = 0.5,

    /** Minimum exercises with sufficient history before a trend index is emitted. */
    val minExercisesForIndex: Int = 2,

    /** Minimum total comparable units before a trend index is emitted. */
    val minIndexEvidenceUnits: Int = 6,

    /** Exercises with repeated decline required to call a multi-exercise decline. */
    val multiExerciseDeclineCount: Int = 2,

    /** Exercises with sufficient history for HIGH aggregate confidence. */
    val highConfidenceExercises: Int = 4,

    /** Comparable units for HIGH aggregate confidence. */
    val highConfidenceUnits: Int = 12,

    // --- Heuristic training index -------------------------------------------------

    /** Penalty per exercise with repeated unexpected decline. */
    val indexDeclineWeight: Int = 30,

    /** Smaller penalty per exercise with a single notable decline. */
    val indexSingleDeclineWeight: Int = 8,

    /** Upper bound of the accumulated penalty. */
    val indexMaxPenalty: Int = 60,

    /** Penalty factor applied while a deload is active (`0.5` = halved). */
    val deloadPenaltyFactor: Double = 0.5,

    // --- Daily form ---------------------------------------------------------------

    /** Sleep quality at or above this value counts as good. */
    val goodSleepQualityAtLeast: Int = 4,

    /** Sleep quality at or below this value counts as a concern. */
    val concernSleepQualityAtMost: Int = 2,

    /** Energy at or above this value counts as good. */
    val goodEnergyAtLeast: Int = 4,

    /** Energy at or below this value counts as a concern. */
    val concernEnergyAtMost: Int = 2,

    /** Stress at or below this value counts as good. */
    val goodStressAtMost: Int = 2,

    /** Stress at or above this value counts as a concern. */
    val concernStressAtLeast: Int = 4,

    /** Soreness at or below this value counts as good. */
    val goodSorenessAtMost: Int = 2,

    /** Soreness at or above this value counts as a concern. */
    val concernSorenessAtLeast: Int = 4,

    /** Lowest accepted value on the check-in scale. */
    val checkInScaleMin: Int = 1,

    /** Highest accepted value on the check-in scale. */
    val checkInScaleMax: Int = 5,

    // --- Muscle-group context -----------------------------------------------------

    /** Soreness at or above this value raises the HIGH_SORENESS flag. */
    val muscleHighSorenessAtLeast: Int = 4,

    /** Sets at or above this value in the window raise HIGH_RECENT_VOLUME. */
    val muscleHighWeeklySets: Double = 20.0,

    /** Sets above zero and below this value in the window raise LOW_RECENT_VOLUME. */
    val muscleLowWeeklySets: Double = 4.0,
) {
    companion object {
        /** Documented, stable default heuristic set. */
        val DEFAULT = ReadinessThresholds()
    }
}
