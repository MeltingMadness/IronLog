package com.ironlog.shared.readiness

/**
 * Composing entry point of the platform-neutral readiness core.
 *
 * It keeps three concerns explicitly separate:
 * * [TrainingTrendEngine] - the multi-week training trend and the heuristic index;
 * * [DailyFormEngine] - the optional daily check-in, with no aggregate score;
 * * [MuscleContextEngine] - today's muscle-group facts, with no recovery percentage.
 *
 * Nothing here mutates a plan, writes storage, or reads a clock.
 */
object ReadinessEngine {

    /** Runs all three parts for [input] and returns one self-contained assessment. */
    fun assess(input: ReadinessInput): ReadinessAssessment {
        val trend = TrainingTrendEngine.assess(input)
        return ReadinessAssessment(
            generatedAtEpochMillis = input.nowEpochMillis,
            trainingTrend = trend,
            dailyForm = DailyFormEngine.assess(input.checkIn, input.thresholds),
            muscleGroups = MuscleContextEngine.assess(input),
            thresholdsRevision = input.thresholds.revision,
        )
    }
}

/**
 * Daily-form classification from an optional check-in.
 *
 * Each dimension is reported on its own. There is deliberately **no** summed
 * "daily readiness" value and no readiness percentage: only per-dimension states
 * and named concerns. A missing answer stays "not reported".
 */
object DailyFormEngine {

    /** Returns `null` when the athlete did not submit a check-in at all. */
    fun assess(
        checkIn: DailyCheckInInput?,
        thresholds: ReadinessThresholds = ReadinessThresholds.DEFAULT,
    ): DailyFormAssessment? {
        if (checkIn == null) return null

        val signals = buildList {
            checkIn.sleepQuality?.let {
                add(signal(DailyFormDimension.SLEEP_QUALITY, it, higherIsBetter = true, thresholds))
            }
            checkIn.energy?.let {
                add(signal(DailyFormDimension.ENERGY, it, higherIsBetter = true, thresholds))
            }
            checkIn.stress?.let {
                add(signal(DailyFormDimension.STRESS, it, higherIsBetter = false, thresholds))
            }
            checkIn.soreness?.let {
                add(signal(DailyFormDimension.SORENESS, it, higherIsBetter = false, thresholds))
            }
        }

        val expectedDimensions = if (checkIn.soreness != null) 4 else 3
        val answered = signals.count { it.state != DailyFormState.UNKNOWN }
        val hasConcern = signals.any { it.state == DailyFormState.CONCERN }

        val reasons = buildList {
            if (answered == 0) add(reason(ReadinessReasonCode.NO_CHECK_IN))
            if (signals.isNotEmpty() && answered < expectedDimensions) {
                add(
                    reason(
                        ReadinessReasonCode.PARTIAL_CHECK_IN,
                        mapOf("answered" to answered.toDouble(), "total" to expectedDimensions.toDouble()),
                    ),
                )
            }
            if (signals.isNotEmpty() && signals.all { it.state == DailyFormState.NEUTRAL }) {
                add(reason(ReadinessReasonCode.CHECK_IN_ALL_NEUTRAL))
            }
            signals.forEach { signal ->
                if (signal.state != DailyFormState.CONCERN) return@forEach
                val code = when (signal.dimension) {
                    DailyFormDimension.SLEEP_QUALITY -> ReadinessReasonCode.SLEEP_BELOW_THRESHOLD
                    DailyFormDimension.ENERGY -> ReadinessReasonCode.ENERGY_BELOW_THRESHOLD
                    DailyFormDimension.STRESS -> ReadinessReasonCode.STRESS_ABOVE_THRESHOLD
                    DailyFormDimension.SORENESS -> ReadinessReasonCode.SORENESS_ABOVE_THRESHOLD
                }
                add(reason(code, mapOf("value" to signal.value.toDouble())))
            }
        }

        // Subjective self-report never reaches HIGH confidence.
        val confidence = when (answered) {
            0 -> EvidenceConfidence.NONE
            1, 2 -> EvidenceConfidence.LOW
            else -> EvidenceConfidence.MODERATE
        }

        return DailyFormAssessment(
            coverage = answered,
            confidence = confidence,
            hasAnyConcern = hasConcern,
            signals = signals,
            reasons = reasons,
        )
    }

    private fun signal(
        dimension: DailyFormDimension,
        value: Int,
        higherIsBetter: Boolean,
        thresholds: ReadinessThresholds,
    ): DailyFormSignal {
        if (value < thresholds.checkInScaleMin || value > thresholds.checkInScaleMax) {
            // Out-of-range values are never clamped into a plausible answer.
            return DailyFormSignal(dimension = dimension, value = value, state = DailyFormState.UNKNOWN)
        }
        val good: Boolean
        val concern: Boolean
        if (higherIsBetter) {
            good = value >= goodThreshold(dimension, thresholds)
            concern = value <= concernThreshold(dimension, thresholds)
        } else {
            good = value <= goodThreshold(dimension, thresholds)
            concern = value >= concernThreshold(dimension, thresholds)
        }
        val state = when {
            concern -> DailyFormState.CONCERN
            good -> DailyFormState.GOOD
            else -> DailyFormState.NEUTRAL
        }
        return DailyFormSignal(dimension = dimension, value = value, state = state)
    }

    private fun goodThreshold(dimension: DailyFormDimension, thresholds: ReadinessThresholds): Int =
        when (dimension) {
            DailyFormDimension.SLEEP_QUALITY -> thresholds.goodSleepQualityAtLeast
            DailyFormDimension.ENERGY -> thresholds.goodEnergyAtLeast
            DailyFormDimension.STRESS -> thresholds.goodStressAtMost
            DailyFormDimension.SORENESS -> thresholds.goodSorenessAtMost
        }

    private fun concernThreshold(dimension: DailyFormDimension, thresholds: ReadinessThresholds): Int =
        when (dimension) {
            DailyFormDimension.SLEEP_QUALITY -> thresholds.concernSleepQualityAtMost
            DailyFormDimension.ENERGY -> thresholds.concernEnergyAtMost
            DailyFormDimension.STRESS -> thresholds.concernStressAtLeast
            DailyFormDimension.SORENESS -> thresholds.concernSorenessAtLeast
        }

    private fun reason(code: ReadinessReasonCode, arguments: Map<String, Double> = emptyMap()) =
        ReadinessReason(code = code, arguments = arguments)

}

/**
 * Muscle-group facts for today's training.
 *
 * Reports last load, completed sets in the adapter-supplied window, today's sets
 * and observed soreness. It deliberately produces **no** recovery percentage and
 * no "ready in X hours" estimate.
 */
object MuscleContextEngine {

    /** One fact row per muscle group that appears today or in the loaded history. */
    fun assess(input: ReadinessInput): List<MuscleGroupContext> {
        val thresholds = input.thresholds
        val now = input.nowEpochMillis
        val today = input.todaySession
        val windowStart = input.muscleWindowStartEpochMillis

        // commonMain: sortedSetOf is JVM-only, so collect and order explicitly.
        val groups = mutableSetOf<String>()
        today?.muscleGroups?.let { groups += it }
        today?.sorenessByMuscle?.keys?.let { groups += it }
        input.muscleLoadHistory.forEach { groups += it.muscleGroup }

        return groups.filter { it.isNotBlank() }.sorted().map { group ->
            val entries = input.muscleLoadHistory.filter { it.muscleGroup == group }
            val lastTrained = entries
                .filter { it.completedAtEpochMillis <= now }
                .maxOfOrNull { it.completedAtEpochMillis }

            val setsInWindow = if (windowStart != null) {
                entries
                    .filter { it.completedAtEpochMillis in windowStart..now }
                    .sumOf { it.completedSets }
            } else {
                0.0
            }

            val setsToday = if (today != null) {
                entries.filter { it.sessionId == today.sessionId }.sumOf { it.completedSets }
            } else {
                0.0
            }

            val soreness = entries
                .filter { today != null && it.sessionId == today.sessionId }
                .mapNotNull { it.soreness }
                .maxOrNull()
                ?: today?.sorenessByMuscle?.get(group)

            val flags = buildList {
                if (setsToday > 0.0) add(MuscleGroupFlag.TRAINED_TODAY)
                if (soreness != null && soreness >= thresholds.muscleHighSorenessAtLeast) {
                    add(MuscleGroupFlag.HIGH_SORENESS)
                }
                if (setsInWindow >= thresholds.muscleHighWeeklySets) {
                    add(MuscleGroupFlag.HIGH_RECENT_VOLUME)
                } else if (setsInWindow > 0.0 && setsInWindow < thresholds.muscleLowWeeklySets) {
                    add(MuscleGroupFlag.LOW_RECENT_VOLUME)
                }
                if (lastTrained == null) add(MuscleGroupFlag.NO_RECENT_LOAD)
            }

            MuscleGroupContext(
                muscleGroup = group,
                plannedToday = today?.muscleGroups?.contains(group) == true,
                lastTrainedEpochMillis = lastTrained,
                setsInWindow = setsInWindow,
                setsToday = setsToday,
                soreness = soreness,
                flags = flags,
            )
        }
    }
}
