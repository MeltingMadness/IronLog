package com.ironlog.app.presentation.progression

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ironlog.app.domain.model.ProgressionReasonCode
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.util.WeightFormatting
import com.ironlog.core.designsystem.R
import java.text.NumberFormat
import kotlin.math.abs

@Composable
fun ProgressionReasonText(
    item: ProgressionReviewItemUi,
    displayUnitSystem: UnitSystem
): String {
    val arguments = item.reasonArguments
    return when (item.reasonCode) {
        ProgressionReasonCode.REP_TARGET_ADVANCED ->
            stringResource(R.string.progression_review_reason_rep_advanced) + weightBasisSuffix(item, displayUnitSystem)

        ProgressionReasonCode.LOAD_ADVANCED -> loadAdvancedText(item, displayUnitSystem)
        ProgressionReasonCode.TOTAL_REPS_COMPLETED -> totalRepsCompletedText(item, displayUnitSystem)
        ProgressionReasonCode.RPE_WITHIN_TARGET -> rpeWithinTargetText(item, displayUnitSystem)
        ProgressionReasonCode.REPEAT_TARGET -> repeatTargetText(arguments)

        ProgressionReasonCode.STALL_BACKOFF -> {
            val percent = arguments.validNumber("backoffPercent")
            if (percent != null && percent in 1.0..30.0) {
                stringResource(
                    R.string.progression_review_reason_stall_backoff,
                    formatNumber(percent)
                )
            } else {
                unavailableReason()
            }
        }

        ProgressionReasonCode.MANUAL_WEIGHT_DEVIATION -> {
            val expected = arguments.validNumber("expectedWeightKg")
            val actual = arguments.validNumber("actualWeightKg")
            if (expected != null && actual != null && expected >= 0.0 && actual >= 0.0) {
                stringResource(
                    R.string.progression_review_reason_weight_deviation,
                    WeightFormatting.formatWeight(expected, displayUnitSystem),
                    WeightFormatting.formatWeight(actual, displayUnitSystem)
                )
            } else {
                unavailableReason()
            }
        }

        ProgressionReasonCode.TOO_FEW_WORK_SETS -> {
            val target = arguments.validWholeNumber("targetSets")
            val actual = arguments.validWholeNumber("actualWorkSets")
            if (target != null && target > 0L && actual != null && actual >= 0L) {
                stringResource(
                    R.string.progression_review_reason_too_few_sets,
                    target.toString(),
                    actual.toString()
                )
            } else {
                unavailableReason()
            }
        }

        ProgressionReasonCode.RPE_MISSING ->
            stringResource(R.string.progression_review_reason_rpe_missing)
        ProgressionReasonCode.RPE_INVALID ->
            stringResource(R.string.progression_review_reason_rpe_invalid)
        ProgressionReasonCode.CONFIG_INVALID ->
            stringResource(R.string.progression_review_reason_config_invalid)
        ProgressionReasonCode.RULE_REVISION_UNSUPPORTED ->
            stringResource(R.string.progression_review_reason_revision_unsupported)
        ProgressionReasonCode.MANUAL_SCHEME ->
            stringResource(R.string.progression_review_reason_manual)
        ProgressionReasonCode.SET_NUMBER_INVALID ->
            stringResource(R.string.progression_review_reason_set_number_invalid)
        ProgressionReasonCode.SET_VALUE_INVALID ->
            stringResource(R.string.progression_review_reason_set_value_invalid)

        ProgressionReasonCode.BACKOFF_FLOOR_REACHED -> {
            val percent = arguments.validNumber("backoffPercent")
            if (percent != null && percent in 1.0..30.0) {
                stringResource(
                    R.string.progression_review_reason_backoff_floor,
                    formatNumber(percent)
                )
            } else {
                unavailableReason()
            }
        }
    }
}

@Composable
private fun loadAdvancedText(item: ProgressionReviewItemUi, displayUnitSystem: UnitSystem): String {
    val step = item.validConfiguredStep() ?: return unavailableReason()
    return stringResource(
        R.string.progression_review_reason_load_advanced,
        formatNumber(step.originalValue),
        WeightFormatting.unitLabel(step.originalUnit)
    ) + weightBasisSuffix(item, displayUnitSystem)
}

@Composable
private fun totalRepsCompletedText(item: ProgressionReviewItemUi, displayUnitSystem: UnitSystem): String {
    val achieved = item.reasonArguments.validWholeNumber("achievedTotalReps")
    val target = item.reasonArguments.validWholeNumber("targetTotalReps")
    val step = item.validConfiguredStep()
    return if (achieved != null && achieved >= 0L && target != null && target > 0L && step != null) {
        stringResource(
            R.string.progression_review_reason_total_completed,
            achieved.toString(),
            target.toString(),
            formatNumber(step.originalValue),
            WeightFormatting.unitLabel(step.originalUnit)
        ) + weightBasisSuffix(item, displayUnitSystem)
    } else {
        unavailableReason()
    }
}

@Composable
private fun rpeWithinTargetText(item: ProgressionReviewItemUi, displayUnitSystem: UnitSystem): String {
    val highest = item.reasonArguments.validNumber("highestRpe")
    val target = item.reasonArguments.validNumber("targetRpe")
    val tolerance = item.reasonArguments.validNumber("tolerance")
    val step = item.validConfiguredStep()
    return if (
        highest != null && highest in 1.0..10.0 &&
        target != null && target in 1.0..10.0 &&
        tolerance != null && tolerance in 0.0..2.0 &&
        step != null
    ) {
        stringResource(
            R.string.progression_review_reason_rpe_within_target,
            formatNumber(highest),
            formatNumber(target),
            formatNumber(tolerance),
            formatNumber(step.originalValue),
            WeightFormatting.unitLabel(step.originalUnit)
        ) + weightBasisSuffix(item, displayUnitSystem)
    } else {
        unavailableReason()
    }
}

/**
 * Explains a proposal whose weight basis differs from the plan target: the
 * suggestion was derived from the actually trained weight, not from the
 * (stale) plan target. Empty when both agree.
 */
@Composable
private fun weightBasisSuffix(item: ProgressionReviewItemUi, displayUnitSystem: UnitSystem): String {
    val actual = item.reasonArguments.validNumber("actualWeightKg") ?: return ""
    val expected = item.source.weightKg
    if (actual < 0.0 || expected < 0.0 || abs(actual - expected) <= WEIGHT_BASIS_TOLERANCE_KG) {
        return ""
    }
    return stringResource(
        R.string.progression_review_reason_weight_basis,
        WeightFormatting.formatWeight(actual, displayUnitSystem),
        WeightFormatting.formatWeight(expected, displayUnitSystem)
    )
}

@Composable
private fun repeatTargetText(arguments: Map<String, Double>): String {
    val highestRpe = arguments.validNumber("highestRpe")
    if (highestRpe != null && highestRpe in 1.0..10.0) {
        return stringResource(
            R.string.progression_review_reason_repeat_rpe,
            formatNumber(highestRpe)
        )
    }

    val targetReps = arguments.validWholeNumber("targetReps")
    val actualReps = arguments.validWholeNumber("actualReps")
    if (targetReps != null && targetReps > 0L && actualReps != null && actualReps >= 0L) {
        return stringResource(
            R.string.progression_review_reason_repeat_reps,
            targetReps.toString(),
            actualReps.toString()
        )
    }

    val achievedTotal = arguments.validWholeNumber("achievedTotalReps")
    val targetTotal = arguments.validWholeNumber("targetTotalReps")
    if (achievedTotal != null && achievedTotal >= 0L && targetTotal != null && targetTotal > 0L) {
        return stringResource(
            R.string.progression_review_reason_repeat_total,
            achievedTotal.toString(),
            targetTotal.toString()
        )
    }

    return unavailableReason()
}

private fun ProgressionReviewItemUi.validConfiguredStep() = configuredStep?.takeIf { step ->
    val argumentValue = reasonArguments.validNumber("stepOriginalValue")
    argumentValue != null &&
        step.originalValue.isFinite() &&
        step.originalValue > 0.0 &&
        abs(argumentValue - step.originalValue) <= STEP_TOLERANCE
}

private fun Map<String, Double>.validNumber(key: String): Double? =
    get(key)?.takeIf(Double::isFinite)

private fun Map<String, Double>.validWholeNumber(key: String): Long? {
    val value = validNumber(key) ?: return null
    if (value < Long.MIN_VALUE.toDouble() || value > Long.MAX_VALUE.toDouble()) return null
    val whole = value.toLong()
    return whole.takeIf { it.toDouble() == value }
}

private fun formatNumber(value: Double): String = NumberFormat.getNumberInstance().apply {
    maximumFractionDigits = 2
    minimumFractionDigits = 0
    isGroupingUsed = false
}.format(value)

@Composable
private fun unavailableReason(): String =
    stringResource(R.string.progression_review_reason_unavailable)

private const val STEP_TOLERANCE = 0.000001
private const val WEIGHT_BASIS_TOLERANCE_KG = 0.1
