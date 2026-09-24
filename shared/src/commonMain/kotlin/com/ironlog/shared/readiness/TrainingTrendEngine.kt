package com.ironlog.shared.readiness

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Platform-neutral multi-week training-trend heuristic.
 *
 * The engine builds one comparable series per exercise from its own history,
 * removes intentional breaks (deload, slot change, deliberate load reset, long
 * absence, and any change of the comparison metric), and looks for repeated
 * *unexpected* deterioration. It never mutates a plan and never produces a
 * medical readiness percentage.
 *
 * Design rules that keep the comparison honest:
 * * Set type ("how it was logged") and intention ("why it ended that way") are
 *   distinct axes. `SetType.FAILURE` is never re-read as a planned intention.
 * * TOTAL_REPS compares summed repetitions at unchanged loads. LINEAR_LOAD
 *   compares achieved load with a discount for missing prescribed repetitions.
 *   Other weighted schemes compare average E1RM within compatible rep bands.
 * * Units with a different metric kind, rep band or work-set count are never
 *   compared directly; a new series starts with an explicit reason.
 * * Several rows of the same exercise inside one session are one unit, not many.
 * * Units trained deliberately to failure are neutral for the decline test.
 * * The baseline is a robust median, not the maximum of the oldest values.
 *
 * All functions are pure and deterministic for a given [ReadinessInput].
 */
object TrainingTrendEngine {

    /** Assesses the multi-week training trend for every exercise in [input]. */
    fun assess(input: ReadinessInput): TrainingTrendAssessment {
        val thresholds = input.thresholds
        val now = input.nowEpochMillis

        // Only completed, non-future units participate.
        val windowStart = now - thresholds.analysisWindowDays.coerceAtLeast(1).toLong() * 86_400_000L
        val rows = input.sessions.filter { it.completedAtEpochMillis in windowStart..now }

        val analyses = rows
            .groupBy { it.exerciseId }
            .entries
            .sortedBy { it.key }
            .map { (_, exerciseRows) -> analyzeExercise(exerciseRows, thresholds) }

        val trends = analyses.map { it.trend }

        val comparableUnits = trends.sumOf { it.comparableUnitCount }
        val sufficientHistory = trends.count { it.comparableUnitCount >= thresholds.minComparableUnits }

        val repeatedDeclines = trends.filter {
            it.comparableUnitCount >= thresholds.minComparableUnits &&
                it.repeatedDeclineCount >= thresholds.repeatedDeclineCount
        }
        val singleDeclines = trends.filter {
            it.comparableUnitCount >= thresholds.minComparableUnits &&
                it.status == ExerciseTrendStatus.DECLINING &&
                it.repeatedDeclineCount < thresholds.repeatedDeclineCount
        }

        val latestRow = rows.maxWithOrNull(
            compareBy<ExerciseSessionInput> { it.completedAtEpochMillis }
                .thenBy { it.orderingToken }
                .thenBy { it.sessionId },
        )
        val deloadActive = latestRow?.deloadState == DeloadState.PLANNED_DELOAD

        val confidence = aggregateConfidence(
            exercisesWithHistory = sufficientHistory,
            comparableUnits = comparableUnits,
            thresholds = thresholds,
        )

        val hasIndexEvidence = confidence == EvidenceConfidence.MODERATE || confidence == EvidenceConfidence.HIGH
        val index = if (hasIndexEvidence) {
            trainingIndex(
                repeatedDeclineCount = repeatedDeclines.size,
                singleDeclineCount = singleDeclines.size,
                deloadActive = deloadActive,
                thresholds = thresholds,
            )
        } else {
            null
        }

        val status = when {
            sufficientHistory == 0 -> TrainingTrendStatus.INSUFFICIENT_DATA
            repeatedDeclines.isEmpty() -> TrainingTrendStatus.NO_NOTABLE_STRAIN
            repeatedDeclines.size == 1 -> TrainingTrendStatus.SINGLE_EXERCISE_DECLINE
            else -> TrainingTrendStatus.MULTIPLE_EXERCISE_DECLINE
        }

        val deloadSuggested = !deloadActive &&
            repeatedDeclines.size >= thresholds.multiExerciseDeclineCount &&
            hasIndexEvidence

        val missingRpe = analyses.sumOf { it.missingRpeSetCount }
        val unknownIntention = analyses.sumOf { it.unknownIntentionSetCount }
        val unrecognizedIntention = analyses.sumOf { it.unrecognizedIntentionSetCount }
        val unknownSetType = analyses.sumOf { it.unknownSetTypeCount }
        val excludedDeload = rows.count { it.isDeload() }
        val mixedEquipment = analyses.any { it.mixedEquipment }
        val slotChanged = analyses.any { BreakKind.SLOT in it.breakKinds }
        val goalReset = analyses.any { BreakKind.GOAL in it.breakKinds }
        val comparabilityBreak = analyses.any {
            BreakKind.METRIC_KIND in it.breakKinds ||
                BreakKind.REP_BAND in it.breakKinds ||
                BreakKind.SET_COUNT in it.breakKinds
        }
        val multipleSlots = analyses.any { it.mergedSessionCount > 0 }
        val weightStepUnknown = analyses.any { it.weightStepUnknown }
        val plannedFailure = analyses.sumOf { it.plannedFailureSetCount }
        val unexpectedMiss = analyses.sumOf { it.unexpectedMissSetCount }
        val noValidSets = trends.any { it.status == ExerciseTrendStatus.EXCLUDED }

        val reasons = buildList {
            when (status) {
                TrainingTrendStatus.INSUFFICIENT_DATA -> add(reason(ReadinessReasonCode.INSUFFICIENT_HISTORY))
                TrainingTrendStatus.NO_NOTABLE_STRAIN -> add(reason(ReadinessReasonCode.NO_NOTABLE_STRAIN))
                TrainingTrendStatus.SINGLE_EXERCISE_DECLINE -> add(reason(ReadinessReasonCode.SINGLE_EXERCISE_DECLINE))
                TrainingTrendStatus.MULTIPLE_EXERCISE_DECLINE -> add(
                    reason(
                        ReadinessReasonCode.MULTIPLE_EXERCISE_DECLINES,
                        mapOf("exercises" to repeatedDeclines.size.toDouble()),
                    ),
                )
            }
            if (deloadActive) add(reason(ReadinessReasonCode.DELOAD_IN_PROGRESS))
            if (deloadSuggested) add(reason(ReadinessReasonCode.DELOAD_SUGGESTED))
            if (excludedDeload > 0) add(reason(ReadinessReasonCode.DELOAD_CONTEXT_NEUTRAL))
            if (missingRpe > 0) add(reason(ReadinessReasonCode.MISSING_RPE_NEUTRAL))
            if (unknownIntention + unrecognizedIntention > 0) {
                add(reason(ReadinessReasonCode.UNKNOWN_INTENTION_PRESENT))
            }
            if (trends.any { it.status == ExerciseTrendStatus.STABLE }) {
                add(reason(ReadinessReasonCode.STAGNATION_NEUTRAL))
            }
            // Neutral only because the intention says the failure was planned.
            if (plannedFailure > 0) add(reason(ReadinessReasonCode.PLANNED_FAILURE_NEUTRAL))
            if (unexpectedMiss > 0) {
                add(
                    reason(
                        ReadinessReasonCode.UNEXPECTED_TARGET_MISS_PRESENT,
                        mapOf("sets" to unexpectedMiss.toDouble()),
                    ),
                )
            }
            if (comparabilityBreak) add(reason(ReadinessReasonCode.METRIC_KIND_CHANGED_RESET))
            if (multipleSlots) add(reason(ReadinessReasonCode.MULTIPLE_SLOTS_IN_SESSION_MERGED))
        }

        val notes = buildList {
            if (missingRpe > 0) add(ReadinessDataQualityNote.MISSING_RPE_TREATED_NEUTRAL)
            if (unknownIntention + unrecognizedIntention > 0) {
                add(ReadinessDataQualityNote.UNKNOWN_SET_INTENTION)
            }
            if (unknownSetType > 0) add(ReadinessDataQualityNote.UNKNOWN_SET_TYPE)
            if (mixedEquipment) add(ReadinessDataQualityNote.MIXED_EQUIPMENT_HISTORY)
            if (slotChanged) add(ReadinessDataQualityNote.EXERCISE_SLOT_CHANGED)
            if (goalReset) add(ReadinessDataQualityNote.GOAL_RESET_DETECTED)
            if (comparabilityBreak) add(ReadinessDataQualityNote.COMPARABILITY_BREAK)
            if (multipleSlots) add(ReadinessDataQualityNote.MULTIPLE_SLOTS_IN_SESSION)
            if (weightStepUnknown) add(ReadinessDataQualityNote.WEIGHT_STEP_UNKNOWN)
            if (excludedDeload > 0) add(ReadinessDataQualityNote.DELOAD_UNITS_EXCLUDED)
            if (sufficientHistory < trends.size) add(ReadinessDataQualityNote.INSUFFICIENT_HISTORY)
            if (noValidSets) add(ReadinessDataQualityNote.NO_VALID_WORK_SETS)
        }

        val dataQuality = TrendDataQuality(
            comparableUnitCount = comparableUnits,
            analyzedExerciseCount = trends.size,
            exercisesWithSufficientHistory = sufficientHistory,
            insufficientDataExerciseCount = trends.size - sufficientHistory,
            missingRpeSetCount = missingRpe,
            unknownIntentionSetCount = unknownIntention + unrecognizedIntention,
            excludedDeloadUnitCount = excludedDeload,
            notes = notes,
        )

        return TrainingTrendAssessment(
            status = status,
            trainingIndex = index,
            confidence = confidence,
            deloadActive = deloadActive,
            deloadSuggested = deloadSuggested,
            analyzedExerciseCount = trends.size,
            exercisesWithSufficientHistory = sufficientHistory,
            repeatedDeclineExerciseCount = repeatedDeclines.size,
            exercises = trends,
            dataQuality = dataQuality,
            reasons = reasons,
            thresholdsRevision = thresholds.revision,
        )
    }

    // ---------------------------------------------------------------------------------
    // Per-exercise analysis
    // ---------------------------------------------------------------------------------

    private fun analyzeExercise(
        exerciseRows: List<ExerciseSessionInput>,
        thresholds: ReadinessThresholds,
    ): ExerciseAnalysis {
        val ordered = exerciseRows.sortedWith(SESSION_ORDER)
        val latestRow = ordered.last()
        val name = ordered.lastOrNull { it.exerciseName.isNotBlank() }?.exerciseName ?: latestRow.exerciseName

        // Comparable line: same exercise id and same equipment. Analyse the line
        // that contains the most recent unit.
        val byEquipment = ordered.groupBy { it.equipmentType }
        val comparableRows = byEquipment[latestRow.equipmentType].orEmpty()
        val mixedEquipment = byEquipment.size > 1

        val deloadUnits = comparableRows.count { it.isDeload() }
        val kept = comparableRows.filterNot { it.isDeload() }

        // Several rows for the same exercise inside one session are one unit, not
        // several training sessions. Merging prevents inflated evidence.
        val units = mergeBySession(kept)
        val mergedSessionCount = units.count { it.mergedSlots }

        val samples = units.mapNotNull { sampleOf(it, thresholds) }

        val missingRpe = kept.sumOf { unit -> unit.countValidSets { it.rpe == null } }
        val unknownIntention = kept.sumOf { unit ->
            unit.countValidSets { it.normalizedIntention() == SetIntentionCodes.UNKNOWN }
        }
        val unrecognizedIntention = kept.sumOf { unit -> unit.countValidSets { !it.hasRecognizedIntention() } }
        val unknownSetType = kept.sumOf { unit ->
            unit.countValidSets { it.setType == SetType.UNKNOWN }
        }
        val plannedFailure = kept.sumOf { unit ->
            unit.countValidSets { it.normalizedIntention() == SetIntentionCodes.PLANNED_FAILURE }
        }
        val unexpectedMiss = kept.sumOf { unit ->
            unit.countValidSets { it.normalizedIntention() == SetIntentionCodes.UNEXPECTED_TARGET_MISS }
        }
        val validSetCount = kept.sumOf { unit -> unit.countValidSets { true } }
        // A compared target weight without a configured step means goal-reset
        // detection cannot be evaluated; the core says so instead of guessing.
        val weightStepUnknown = comparableRows.any {
            it.goal.targetWeightKg != null && it.goal.explicitWeightStepKg() == null
        }

        val breakKinds = linkedSetOf<BreakKind>()
        val series = latestSeries(samples, breakKinds, thresholds)

        val metricKind = series.lastOrNull()?.metricKind ?: TrendMetricKind.NONE
        val comparableUnitCount = series.size
        val latestMetric = series.lastOrNull()?.metricValue
        val baselineMetric = baselineOf(series, thresholds)
        val changePercent = if (baselineMetric != null && latestMetric != null && abs(baselineMetric) > EPSILON) {
            (latestMetric - baselineMetric) / abs(baselineMetric) * 100.0
        } else {
            null
        }

        val declineWindow = thresholds.declineWindowUnits.coerceAtLeast(1)
        // Planned-failure units are neutral: a deliberately harder set ending at
        // failure is not "unexpected deterioration".
        val recentDeclines = (1 until series.size).toList()
            .takeLast(declineWindow)
            .filter { !series[it].plannedFailureUnit && !series[it - 1].plannedFailureUnit }
            .map { i -> declinePercent(series[i - 1].metricValue, series[i].metricValue) }
            .takeLast(declineWindow)
        val repeatedDeclineCount = recentDeclines.count { it >= thresholds.notableDeclinePercent }
        val repeated = recentDeclines.size >= declineWindow &&
            repeatedDeclineCount >= thresholds.repeatedDeclineCount

        val seriesTotalSets = series.sumOf { it.validSetCount }
        val seriesRpeSets = series.sumOf { it.rpeSetCount }
        val seriesUnknown = series.sumOf { it.unknownIntentionSetCount }
        val rpeCoverage = if (seriesTotalSets == 0) 0.0 else seriesRpeSets.toDouble() / seriesTotalSets
        val unknownRatio = if (seriesTotalSets == 0) 0.0 else seriesUnknown.toDouble() / seriesTotalSets
        val rpeAdjusted = series.any { it.rpeAdjusted }

        val status = when {
            comparableUnitCount == 0 -> ExerciseTrendStatus.EXCLUDED
            comparableUnitCount < thresholds.minComparableUnits -> ExerciseTrendStatus.INSUFFICIENT_DATA
            series.last().plannedFailureUnit -> ExerciseTrendStatus.STABLE
            repeated -> ExerciseTrendStatus.DECLINING
            changePercent != null && changePercent <= -thresholds.notableDeclinePercent -> ExerciseTrendStatus.DECLINING
            changePercent != null && changePercent >= thresholds.improveThresholdPercent -> ExerciseTrendStatus.IMPROVING
            else -> ExerciseTrendStatus.STABLE
        }

        val confidence = when {
            status == ExerciseTrendStatus.EXCLUDED || status == ExerciseTrendStatus.INSUFFICIENT_DATA ->
                EvidenceConfidence.NONE
            else -> {
                var level = if (comparableUnitCount >= thresholds.minComparableUnits + 2) {
                    EvidenceConfidence.HIGH
                } else {
                    EvidenceConfidence.MODERATE
                }
                // Missing RPE or unknown intention reduce confidence, but they
                // never turn weight/reps evidence into "unusable" history.
                if (rpeCoverage < thresholds.minRpeCoverageForConfidence) level = level.downgrade()
                if (unknownRatio > thresholds.maxUnknownIntentionRatioForConfidence) level = level.downgrade()
                level
            }
        }

        val reasons = buildList {
            when (status) {
                ExerciseTrendStatus.EXCLUDED -> add(reason(ReadinessReasonCode.NO_VALID_WORK_SETS))
                ExerciseTrendStatus.INSUFFICIENT_DATA ->
                    add(reason(ReadinessReasonCode.COMPARABLE_UNITS_BELOW_MINIMUM))
                ExerciseTrendStatus.DECLINING -> add(
                    reason(
                        if (repeated) ReadinessReasonCode.REPEATED_NOTABLE_DECLINE
                        else ReadinessReasonCode.SINGLE_NOTABLE_DECLINE,
                        buildMap {
                            changePercent?.let { put("changePercent", it) }
                            put("repeatedDeclines", repeatedDeclineCount.toDouble())
                        },
                    ),
                )
                ExerciseTrendStatus.IMPROVING -> add(
                    reason(
                        ReadinessReasonCode.IMPROVING_TREND,
                        mapOf("changePercent" to (changePercent ?: 0.0)),
                    ),
                )
                ExerciseTrendStatus.STABLE -> add(
                    reason(
                        ReadinessReasonCode.WITHIN_TREND_BAND,
                        mapOf("changePercent" to (changePercent ?: 0.0)),
                    ),
                )
            }
            if (deloadUnits > 0) add(reason(ReadinessReasonCode.DELOAD_UNITS_EXCLUDED))
            if (mixedEquipment) add(reason(ReadinessReasonCode.EQUIPMENT_CHANGED_RESET))
            if (BreakKind.SLOT in breakKinds) add(reason(ReadinessReasonCode.SLOT_CHANGED_RESET))
            if (BreakKind.GOAL in breakKinds) add(reason(ReadinessReasonCode.GOAL_CHANGED_RESET))
            if (BreakKind.LONG_GAP in breakKinds) add(reason(ReadinessReasonCode.LONG_GAP_RESET))
            if (BreakKind.METRIC_KIND in breakKinds) add(reason(ReadinessReasonCode.METRIC_KIND_CHANGED_RESET))
            if (BreakKind.REP_BAND in breakKinds) add(reason(ReadinessReasonCode.REP_BAND_CHANGED_RESET))
            if (BreakKind.SET_COUNT in breakKinds) add(reason(ReadinessReasonCode.SET_COUNT_CHANGED_RESET))
            if (mergedSessionCount > 0) add(reason(ReadinessReasonCode.MULTIPLE_SLOTS_IN_SESSION_MERGED))
            if (rpeAdjusted) add(reason(ReadinessReasonCode.TARGET_RPE_APPLIED))
            if (rpeCoverage < thresholds.minRpeCoverageForConfidence) {
                add(reason(ReadinessReasonCode.MISSING_RPE_REDUCES_CONFIDENCE))
            }
            if (unknownRatio > thresholds.maxUnknownIntentionRatioForConfidence) {
                add(reason(ReadinessReasonCode.UNKNOWN_INTENTION_REDUCES_CONFIDENCE))
            }
            // Only a recorded planned intention is neutral; SetType.FAILURE is not.
            if (plannedFailure > 0) add(reason(ReadinessReasonCode.PLANNED_FAILURE_SETS_PRESENT))
            if (unexpectedMiss > 0) {
                add(reason(ReadinessReasonCode.UNEXPECTED_TARGET_MISS_SETS_PRESENT))
            }
            if (samples.any { it.plannedFailureUnit }) {
                add(reason(ReadinessReasonCode.PLANNED_FAILURE_UNIT_NEUTRAL))
            }
        }

        val trend = ExerciseTrend(
            exerciseId = latestRow.exerciseId,
            exerciseName = name,
            equipmentType = latestRow.equipmentType,
            metricKind = metricKind,
            status = status,
            comparableUnitCount = comparableUnitCount,
            latestMetricValue = latestMetric,
            baselineMetricValue = baselineMetric,
            changePercent = changePercent,
            repeatedDeclineCount = repeatedDeclineCount,
            confidence = confidence,
            excludedFromFatigue = status == ExerciseTrendStatus.EXCLUDED ||
                status == ExerciseTrendStatus.INSUFFICIENT_DATA,
            reasons = reasons,
        )

        return ExerciseAnalysis(
            trend = trend,
            missingRpeSetCount = missingRpe,
            unknownIntentionSetCount = unknownIntention,
            unrecognizedIntentionSetCount = unrecognizedIntention,
            unknownSetTypeCount = unknownSetType,
            plannedFailureSetCount = plannedFailure,
            unexpectedMissSetCount = unexpectedMiss,
            validSetCount = validSetCount,
            mixedEquipment = mixedEquipment,
            weightStepUnknown = weightStepUnknown,
            mergedSessionCount = mergedSessionCount,
            breakKinds = breakKinds,
        )
    }

    /**
     * Collapses every row of the same exercise inside one session into a single
     * unit. Two slots of the same exercise in one workout are one training unit,
     * not two comparable sessions.
     */
    private fun mergeBySession(rows: List<ExerciseSessionInput>): List<MergedUnit> =
        rows.groupBy { it.sessionId }
            .values
            .map { sameSession ->
                val ordered = sameSession.sortedWith(SESSION_ORDER)
                val last = ordered.last()
                MergedUnit(
                    sessionId = last.sessionId,
                    exerciseId = last.exerciseId,
                    exerciseName = last.exerciseName,
                    equipmentType = last.equipmentType,
                    completedAtEpochMillis = last.completedAtEpochMillis,
                    orderingToken = last.orderingToken,
                    slotChangedSincePrevious = ordered.any { it.slotChangedSincePrevious },
                    mixedGoals = ordered.map { it.goal }.distinct().size > 1,
                    deloadState = last.deloadState,
                    goal = last.goal,
                    sets = ordered.flatMap { it.sets },
                    mergedSlots = ordered.size > 1,
                )
            }
            .sortedWith(compareBy({ it.completedAtEpochMillis }, { it.orderingToken }, { it.sessionId }))

    /** Builds the comparable series, cutting at each intentional break. */
    private fun latestSeries(
        samples: List<UnitSample>,
        breakKinds: MutableSet<BreakKind>,
        thresholds: ReadinessThresholds,
    ): List<UnitSample> {
        if (samples.isEmpty()) return emptyList()
        val segments = mutableListOf<MutableList<UnitSample>>()
        var current = mutableListOf(samples.first())
        for (i in 1 until samples.size) {
            val breakKind = breakBetween(samples[i - 1], samples[i], thresholds)
            if (breakKind != null) {
                breakKinds += breakKind
                segments += current
                current = mutableListOf()
            }
            current += samples[i]
        }
        segments += current
        return segments.last()
    }

    private fun breakBetween(
        previous: UnitSample,
        current: UnitSample,
        thresholds: ReadinessThresholds,
    ): BreakKind? {
        val unit = current.unit
        if (unit.slotChangedSincePrevious) return BreakKind.SLOT
        if (unit.mixedGoals || previous.unit.mixedGoals) return BreakKind.GOAL
        val oldGoal = previous.unit.goal
        val newGoal = unit.goal
        if (oldGoal.scheme != newGoal.scheme ||
            oldGoal.targetRepsMin != newGoal.targetRepsMin ||
            oldGoal.targetRepsMax != newGoal.targetRepsMax ||
            oldGoal.targetRpe != newGoal.targetRpe) return BreakKind.GOAL
        // A repetition-sum progression compares repetitions at the same load.
        if (current.metricKind == TrendMetricKind.TOTAL_REPS &&
            previous.unit.sets.filter { it.isWorkSet() }.map { it.weightKg }.sorted() !=
            unit.sets.filter { it.isWorkSet() }.map { it.weightKg }.sorted()) return BreakKind.GOAL
        // Different metric family, rep band or work-set count cannot be compared
        // directly; starting a new series avoids a fabricated decline.
        if (previous.metricKind != current.metricKind) return BreakKind.METRIC_KIND
        if (previous.repBand != null && current.repBand != null && previous.repBand != current.repBand) {
            return BreakKind.REP_BAND
        }
        if (previous.validSetCount != current.validSetCount) return BreakKind.SET_COUNT
        if (previous.unit.sets.filter { it.isWorkSet() }.groupingBy { it.setType }.eachCount() !=
            unit.sets.filter { it.isWorkSet() }.groupingBy { it.setType }.eachCount()) return BreakKind.METRIC_KIND

        val previousTarget = previous.unit.goal.targetWeightKg
        val currentTarget = unit.goal.targetWeightKg
        val step = unit.goal.explicitWeightStepKg()
        if (previousTarget != null && currentTarget != null && step != null) {
            if (previousTarget - currentTarget > step - thresholds.goalResetToleranceKg) {
                return BreakKind.GOAL
            }
        }
        val gap = unit.completedAtEpochMillis - previous.unit.completedAtEpochMillis
        if (gap > thresholds.segmentationGapMillis) return BreakKind.LONG_GAP
        return null
    }

    /**
     * Robust reference: the median of the units outside the recent window, so a
     * single historical maximum does not automatically become the baseline.
     * With short history the reference remains explicitly limited evidence.
     */
    private fun baselineOf(series: List<UnitSample>, thresholds: ReadinessThresholds): Double? {
        if (series.isEmpty()) return null
        val window = thresholds.declineWindowUnits.coerceAtLeast(1)
        val candidates = if (series.size > window) series.dropLast(window) else listOf(series.first())
        val values = candidates.map { it.metricValue }.sorted()
        return median(values)
    }

    private fun median(sortedValues: List<Double>): Double {
        val middle = sortedValues.size / 2
        return if (sortedValues.size % 2 == 1) {
            sortedValues[middle]
        } else {
            (sortedValues[middle - 1] + sortedValues[middle]) / 2.0
        }
    }

    private fun declinePercent(previous: Double, current: Double): Double {
        if (abs(previous) <= EPSILON) return 0.0
        return (previous - current) / abs(previous) * 100.0
    }

    /** Scheme-aware absolute performance; target changes never create a denominator penalty. */
    private fun sampleOf(
        unit: MergedUnit,
        thresholds: ReadinessThresholds,
    ): UnitSample? {
        val valid = unit.sets.filter { it.isWorkSet() }
        if (valid.isEmpty()) return null

        val goal = unit.goal
        val weighted = valid.all { it.weightKg > 0.0 && it.reps <= thresholds.maxRepsForE1rmEstimate }
        val metricKind: TrendMetricKind
        val metric: Double
        when {
            goal.scheme == ProgressionScheme.TOTAL_REPS -> {
                metricKind = TrendMetricKind.TOTAL_REPS
                metric = valid.sumOf { it.reps }.toDouble()
            }
            goal.scheme == ProgressionScheme.LINEAR_LOAD && weighted -> {
                metricKind = TrendMetricKind.GOAL_SCORE
                val repFloor = (goal.targetRepsMin ?: goal.targetRepsMax)?.takeIf { it > 0 }
                // Absolute achieved load, discounted only for missing prescribed
                // repetitions. Raising the target weight alone never lowers this metric.
                metric = valid.map { set ->
                    set.weightKg * (repFloor?.let { (set.reps.toDouble() / it).coerceAtMost(1.0) } ?: 1.0)
                }.average()
            }
            weighted -> {
                metricKind = TrendMetricKind.ESTIMATED_ONE_REP_MAX
                metric = valid.map { e1rm(it.weightKg, it.reps) }.average()
            }
            else -> {
                metricKind = TrendMetricKind.TOTAL_REPS
                metric = valid.sumOf { it.reps }.toDouble()
            }
        }
        // RPE describes effort separately. Availability must never change the
        // numerical performance metric between otherwise identical sessions.
        val adjusted = metric
        val band = if (metricKind == TrendMetricKind.ESTIMATED_ONE_REP_MAX) {
            repBand(valid.map { it.reps }.average().roundToInt(), thresholds)
        } else null

        return UnitSample(
            unit = unit,
            metricValue = adjusted,
            metricKind = metricKind,
            repBand = band,
            validSetCount = valid.size,
            rpeSetCount = valid.count { it.rpe != null },
            unknownIntentionSetCount = valid.count {
                it.normalizedIntention() == SetIntentionCodes.UNKNOWN
            },
            rpeAdjusted = adjusted != metric,
            // Deliberately trained to failure: a lighter result is expected, so
            // the delta from this unit is neutral for the decline test.
            plannedFailureUnit = valid.all {
                it.normalizedIntention() == SetIntentionCodes.PLANNED_FAILURE
            },
        )
    }

    /** Buckets repetitions so only like reps are compared (1..3, 4..6, ...). */
    private fun repBand(reps: Int, thresholds: ReadinessThresholds): Int {
        val width = thresholds.repBandWidth.coerceAtLeast(1)
        return ((reps - 1).coerceAtLeast(0)) / width
    }

    private fun aggregateConfidence(
        exercisesWithHistory: Int,
        comparableUnits: Int,
        thresholds: ReadinessThresholds,
    ): EvidenceConfidence = when {
        exercisesWithHistory == 0 -> EvidenceConfidence.NONE
        exercisesWithHistory < thresholds.minExercisesForIndex ||
            comparableUnits < thresholds.minIndexEvidenceUnits -> EvidenceConfidence.LOW
        exercisesWithHistory >= thresholds.highConfidenceExercises &&
            comparableUnits >= thresholds.highConfidenceUnits -> EvidenceConfidence.HIGH
        else -> EvidenceConfidence.MODERATE
    }

    private fun trainingIndex(
        repeatedDeclineCount: Int,
        singleDeclineCount: Int,
        deloadActive: Boolean,
        thresholds: ReadinessThresholds,
    ): Int {
        val basePenalty = repeatedDeclineCount * thresholds.indexDeclineWeight +
            singleDeclineCount * thresholds.indexSingleDeclineWeight
        val scaled = if (deloadActive) basePenalty * thresholds.deloadPenaltyFactor else basePenalty.toDouble()
        val penalty = min(thresholds.indexMaxPenalty, scaled.roundToInt()).coerceAtLeast(0)
        return (100 - penalty).coerceIn(0, 100)
    }

    /** Only [SetType.WARMUP] is excluded. A missing type is a work set with a note. */
    private fun TrendSetInput.isWorkSet(): Boolean {
        if (!weightKg.isFinite() || weightKg < 0.0) return false
        if (reps <= 0 || reps > MAX_REPS) return false
        return setType != SetType.WARMUP
    }

    private fun TrendSetInput.normalizedIntention(): String = SetIntentionCodes.normalize(intention)

    private fun TrendSetInput.hasRecognizedIntention(): Boolean = intention in SetIntentionCodes.ALL

    private fun ExerciseSessionInput.isDeload(): Boolean =
        deloadState == DeloadState.PLANNED_DELOAD || deloadState == DeloadState.POST_DELOAD

    private fun ExerciseSessionInput.countValidSets(predicate: (TrendSetInput) -> Boolean): Int =
        sets.count { it.isWorkSet() && predicate(it) }

    private fun e1rm(weightKg: Double, reps: Int): Double = weightKg * (1.0 + reps / 30.0)

    private fun EvidenceConfidence.downgrade(): EvidenceConfidence = when (this) {
        EvidenceConfidence.HIGH -> EvidenceConfidence.MODERATE
        EvidenceConfidence.MODERATE -> EvidenceConfidence.LOW
        EvidenceConfidence.LOW, EvidenceConfidence.NONE -> EvidenceConfidence.LOW
    }

    private fun reason(code: ReadinessReasonCode, arguments: Map<String, Double> = emptyMap()) =
        ReadinessReason(code = code, arguments = arguments)

    private const val EPSILON = 1e-9
    private const val MAX_REPS = 100

    private val SESSION_ORDER = compareBy<ExerciseSessionInput> { it.completedAtEpochMillis }
        .thenBy { it.orderingToken }
        .thenBy { it.slotIndex }
        .thenBy { it.sessionId }

    /** Why a comparable series had to be cut. */
    private enum class BreakKind { SLOT, GOAL, LONG_GAP, METRIC_KIND, REP_BAND, SET_COUNT }

    /** One exercise row-set collapsed into one training unit. */
    private data class MergedUnit(
        val sessionId: Long,
        val exerciseId: Long,
        val exerciseName: String,
        val equipmentType: EquipmentType,
        val completedAtEpochMillis: Long,
        val orderingToken: Long,
        val slotChangedSincePrevious: Boolean,
        val deloadState: DeloadState,
        val goal: ProgressionGoal,
        val sets: List<TrendSetInput>,
        val mergedSlots: Boolean,
        val mixedGoals: Boolean,
    )

    private data class UnitSample(
        val unit: MergedUnit,
        val metricValue: Double,
        val metricKind: TrendMetricKind,
        val repBand: Int?,
        val validSetCount: Int,
        val rpeSetCount: Int,
        val unknownIntentionSetCount: Int,
        val rpeAdjusted: Boolean,
        val plannedFailureUnit: Boolean,
    )

    private data class ExerciseAnalysis(
        val trend: ExerciseTrend,
        val missingRpeSetCount: Int,
        val unknownIntentionSetCount: Int,
        val unrecognizedIntentionSetCount: Int,
        val unknownSetTypeCount: Int,
        val plannedFailureSetCount: Int,
        val unexpectedMissSetCount: Int,
        val validSetCount: Int,
        val mixedEquipment: Boolean,
        val weightStepUnknown: Boolean,
        val mergedSessionCount: Int,
        val breakKinds: Set<BreakKind>,
    )
}
