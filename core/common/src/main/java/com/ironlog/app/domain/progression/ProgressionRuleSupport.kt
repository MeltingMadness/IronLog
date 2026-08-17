package com.ironlog.app.domain.progression

import com.ironlog.app.domain.model.FailurePolicy
import com.ironlog.app.domain.model.ProgressionContext
import com.ironlog.app.domain.model.ProgressionOutcome
import com.ironlog.app.domain.model.ProgressionReasonCode
import com.ironlog.app.domain.model.ProgressionStreakEffect
import com.ironlog.app.domain.model.ProgressionTarget
import com.ironlog.app.domain.model.WeightStep
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.util.WeightFormatting
import java.math.BigDecimal
import kotlin.math.ceil
import kotlin.math.floor

// Weight inputs are rounded to one decimal place in the display unit before
// being converted back to kg (kg: up to 0.05 kg error; lb: up to ~0.023 kg).
// The old 0.01 kg tolerance was tighter than the input pipeline, so logging
// the displayed target (or previous) weight often tripped MANUAL_WEIGHT_DEVIATION
// and silently blocked progression. 0.1 kg covers all display-rounding noise
// while still catching every real deviation (plate granularity is >= 0.5 kg).
internal const val WEIGHT_TOLERANCE_KG = 0.1

internal sealed interface CountedWorkSetsResult {
    data class Valid(val sets: List<WorkoutSet>) : CountedWorkSetsResult

    data class Invalid(
        val reasonCode: ProgressionReasonCode,
        val availableSetIds: List<Long>,
        val reasonArguments: Map<String, Double> = emptyMap()
    ) : CountedWorkSetsResult
}

internal sealed interface PreparedProgressionEvaluation {
    data class Valid(val sets: List<WorkoutSet>) : PreparedProgressionEvaluation
    data class Invalid(val outcome: ProgressionOutcome) : PreparedProgressionEvaluation
}

internal fun countedWorkSets(context: ProgressionContext): CountedWorkSetsResult {
    val availableSetIds = availableWorkSetIds(context)
    val allIds = context.setsForTarget.map(WorkoutSet::id)
    val sourceTarget = context.sourceTarget
    val identityIsValid = allIds.all { it > 0 } &&
        allIds.distinct().size == allIds.size &&
        context.setsForTarget.all { set ->
            set.sessionId == sourceTarget.sessionId &&
                set.exerciseId == sourceTarget.exerciseId &&
                set.planTargetSnapshotId == sourceTarget.id
        }
    if (!identityIsValid) {
        return CountedWorkSetsResult.Invalid(
            reasonCode = ProgressionReasonCode.SET_VALUE_INVALID,
            availableSetIds = availableSetIds
        )
    }

    val workSets = context.setsForTarget
        .filterNot(WorkoutSet::isWarmup)
        .sortedWith(compareBy(WorkoutSet::setNumber, WorkoutSet::completedAt, WorkoutSet::id))
    val workSetNumbers = workSets.map(WorkoutSet::setNumber)
    if (workSetNumbers.any { it <= 0 } || workSetNumbers.distinct().size != workSetNumbers.size) {
        return CountedWorkSetsResult.Invalid(
            reasonCode = ProgressionReasonCode.SET_NUMBER_INVALID,
            availableSetIds = availableSetIds
        )
    }

    val targetSets = sourceTarget.target.sets
    if (workSets.size < targetSets) {
        return CountedWorkSetsResult.Invalid(
            reasonCode = ProgressionReasonCode.TOO_FEW_WORK_SETS,
            availableSetIds = availableSetIds,
            reasonArguments = mapOf(
                "targetSets" to targetSets.toDouble(),
                "actualWorkSets" to workSets.size.toDouble()
            )
        )
    }

    val countedSets = workSets.take(targetSets)
    if (countedSets.any { it.reps < 0 || !it.weightKg.isFinite() || it.weightKg < 0.0 }) {
        return CountedWorkSetsResult.Invalid(
            reasonCode = ProgressionReasonCode.SET_VALUE_INVALID,
            availableSetIds = availableSetIds
        )
    }
    return CountedWorkSetsResult.Valid(countedSets)
}

internal fun priorConsecutiveFailures(context: ProgressionContext): Int {
    var failures = 0
    for (outcome in context.previousComparableOutcomesNewestFirst) {
        when (outcome.streakEffect) {
            ProgressionStreakEffect.INCREMENT -> failures += 1
            ProgressionStreakEffect.IGNORE -> Unit
            ProgressionStreakEffect.RESET -> return failures
        }
    }
    return failures
}

internal fun increasedWeight(targetKg: Double, step: WeightStep): Double {
    val currentDisplay = WeightFormatting.convertToDisplay(targetKg, step.originalUnit)
    val rawDisplay = currentDisplay + step.originalValue
    val lower = floor(rawDisplay / step.originalValue) * step.originalValue
    val upper = ceil(rawDisplay / step.originalValue) * step.originalValue
    val rounded = if (rawDisplay - lower <= upper - rawDisplay) lower else upper
    val increasing = if (rounded > currentDisplay) rounded else currentDisplay + step.originalValue
    return WeightFormatting.convertToKg(increasing, step.originalUnit)
}

internal fun backedOffWeight(targetKg: Double, step: WeightStep, backoffPercent: Double): Double {
    val currentDisplay = WeightFormatting.convertToDisplay(targetKg, step.originalUnit)
    val rawDisplay = currentDisplay * (1.0 - backoffPercent / 100.0)
    val lower = floor(rawDisplay / step.originalValue) * step.originalValue
    val upper = ceil(rawDisplay / step.originalValue) * step.originalValue
    val rounded = if (rawDisplay - lower <= upper - rawDisplay) lower else upper
    val decreasing = if (rounded < currentDisplay) rounded else currentDisplay - step.originalValue
    return WeightFormatting.convertToKg(decreasing.coerceAtLeast(0.0), step.originalUnit)
}

internal fun prepareProgressionEvaluation(context: ProgressionContext): PreparedProgressionEvaluation {
    if (!ProgressionConfigValidator.isValid(context.sourceTarget.target, context.sourceTarget.config)) {
        return PreparedProgressionEvaluation.Invalid(
            ProgressionOutcome.InsufficientData(
                sourceTarget = context.sourceTarget.target,
                reasonCode = ProgressionReasonCode.CONFIG_INVALID,
                countedSetIds = availableWorkSetIds(context)
            )
        )
    }
    return when (val counted = countedWorkSets(context)) {
        is CountedWorkSetsResult.Valid -> PreparedProgressionEvaluation.Valid(counted.sets)
        is CountedWorkSetsResult.Invalid -> PreparedProgressionEvaluation.Invalid(
            ProgressionOutcome.InsufficientData(
                sourceTarget = context.sourceTarget.target,
                reasonCode = counted.reasonCode,
                reasonArguments = counted.reasonArguments,
                countedSetIds = counted.availableSetIds
            )
        )
    }
}

internal fun firstWeightDeviation(sets: List<WorkoutSet>, expectedWeightKg: Double): WorkoutSet? =
    sets.firstOrNull {
        BigDecimal.valueOf(it.weightKg)
            .subtract(BigDecimal.valueOf(expectedWeightKg))
            .abs() > BigDecimal.valueOf(WEIGHT_TOLERANCE_KG)
    }

/**
 * The weight the counted sets were actually performed at, when all counted
 * sets agree within [WEIGHT_TOLERANCE_KG]. Progression proposals are based on
 * this actual weight (the plan target only describes the default load), so a
 * consistently higher or lower load still yields a suggestion. Mixed weights
 * within one session are not comparable to a single training weight and
 * return null; callers fall back to [mixedWeightOutcome].
 */
internal fun evaluationWeightKg(countedSets: List<WorkoutSet>): Double? {
    val first = countedSets.firstOrNull() ?: return null
    return if (firstWeightDeviation(countedSets.drop(1), first.weightKg) == null) {
        first.weightKg
    } else {
        null
    }
}

internal fun mixedWeightOutcome(
    target: ProgressionTarget,
    countedSets: List<WorkoutSet>
): ProgressionOutcome {
    val expected = countedSets.first().weightKg
    val actual = firstWeightDeviation(countedSets.drop(1), expected)?.weightKg ?: expected
    return ProgressionOutcome.InsufficientData(
        sourceTarget = target,
        reasonCode = ProgressionReasonCode.MANUAL_WEIGHT_DEVIATION,
        reasonArguments = mapOf(
            "expectedWeightKg" to expected,
            "actualWeightKg" to actual
        ),
        countedSetIds = countedSets.map(WorkoutSet::id)
    )
}

internal fun repetitionMissOutcome(
    context: ProgressionContext,
    countedSets: List<WorkoutSet>,
    failurePolicy: FailurePolicy,
    repeatReasonArguments: Map<String, Double>,
    baseWeightKg: Double
): ProgressionOutcome {
    val target = context.sourceTarget.target
    val failuresIncludingCurrent = priorConsecutiveFailures(context) + 1
    if (failuresIncludingCurrent < failurePolicy.stallThreshold) {
        return ProgressionOutcome.KeepTarget(
            sourceTarget = target,
            reasonCode = ProgressionReasonCode.REPEAT_TARGET,
            reasonArguments = repeatReasonArguments,
            streakEffect = ProgressionStreakEffect.INCREMENT,
            countedSetIds = countedSets.map(WorkoutSet::id)
        )
    }

    // The backoff is based on the actually trained weight, mirroring the
    // proposal basis of the success paths.
    val backedOffWeight = backedOffWeight(baseWeightKg, stepFor(context), failurePolicy.backoffPercent)
    if (backedOffWeight < baseWeightKg) {
        return ProgressionOutcome.ProposeChange(
            sourceTarget = target,
            proposedTarget = target.copy(weightKg = backedOffWeight),
            reasonCode = ProgressionReasonCode.STALL_BACKOFF,
            reasonArguments = mapOf("backoffPercent" to failurePolicy.backoffPercent),
            streakEffect = ProgressionStreakEffect.INCREMENT,
            countedSetIds = countedSets.map(WorkoutSet::id)
        )
    }
    return ProgressionOutcome.KeepTarget(
        sourceTarget = target,
        reasonCode = ProgressionReasonCode.BACKOFF_FLOOR_REACHED,
        reasonArguments = mapOf("backoffPercent" to failurePolicy.backoffPercent),
        streakEffect = ProgressionStreakEffect.INCREMENT,
        countedSetIds = countedSets.map(WorkoutSet::id)
    )
}

private fun availableWorkSetIds(context: ProgressionContext): List<Long> = context.setsForTarget
    .filterNot(WorkoutSet::isWarmup)
    .sortedWith(compareBy(WorkoutSet::setNumber, WorkoutSet::completedAt, WorkoutSet::id))
    .map(WorkoutSet::id)
    .filter { it > 0 }
    .distinct()

private fun stepFor(context: ProgressionContext): WeightStep = when (val config = context.sourceTarget.config) {
    is com.ironlog.app.domain.model.ProgressionConfig.Linear -> config.step
    is com.ironlog.app.domain.model.ProgressionConfig.DoubleProgression -> config.step
    is com.ironlog.app.domain.model.ProgressionConfig.TotalReps -> config.step
    is com.ironlog.app.domain.model.ProgressionConfig.RpeRir -> config.step
    else -> error("A progression miss requires an active configuration")
}
