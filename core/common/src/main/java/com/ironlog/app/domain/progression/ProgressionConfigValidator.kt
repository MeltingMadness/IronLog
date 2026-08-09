package com.ironlog.app.domain.progression

import com.ironlog.app.domain.model.FailurePolicy
import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.ProgressionTarget
import com.ironlog.app.domain.model.WeightStep
import com.ironlog.app.domain.util.WeightFormatting
import kotlin.math.abs

internal object ProgressionConfigValidator {
    fun isValid(target: ProgressionTarget, config: ProgressionConfig): Boolean {
        if (target.sets <= 0 || target.reps <= 0 || !target.weightKg.isFinite() || target.weightKg < 0.0) {
            return false
        }

        return when (config) {
            is ProgressionConfig.Manual -> true
            is ProgressionConfig.Linear -> activeConfigIsValid(target, config.step, config.failurePolicy)
            is ProgressionConfig.DoubleProgression -> {
                activeConfigIsValid(target, config.step, config.failurePolicy) &&
                    config.minReps >= 1 &&
                    config.minReps <= target.reps &&
                    target.reps <= config.maxReps
            }
            is ProgressionConfig.TotalReps -> {
                activeConfigIsValid(target, config.step, config.failurePolicy) && config.targetTotalReps > 0
            }
            is ProgressionConfig.RpeRir -> {
                activeConfigIsValid(target, config.step, config.failurePolicy) &&
                    config.targetRpe.isFinite() &&
                    config.targetRpe in 1.0..10.0 &&
                    config.tolerance.isFinite() &&
                    config.tolerance in 0.0..2.0
            }
            is ProgressionConfig.Invalid -> false
        }
    }

    private fun activeConfigIsValid(
        target: ProgressionTarget,
        step: WeightStep,
        failurePolicy: FailurePolicy
    ): Boolean {
        if (!step.originalValue.isFinite() || step.originalValue <= 0.0 ||
            !step.kilograms.isFinite() || step.kilograms <= 0.0
        ) {
            return false
        }
        val convertedStep = WeightFormatting.convertToKg(step.originalValue, step.originalUnit)
        if (!convertedStep.isFinite() || abs(convertedStep - step.kilograms) > STEP_STORAGE_TOLERANCE_KG) {
            return false
        }
        if (failurePolicy.stallThreshold !in 1..6 ||
            !failurePolicy.backoffPercent.isFinite() ||
            failurePolicy.backoffPercent !in 1.0..30.0
        ) {
            return false
        }

        val targetInOriginalUnit = WeightFormatting.convertToDisplay(target.weightKg, step.originalUnit)
        val increasedWeight = target.weightKg + step.kilograms
        return targetInOriginalUnit.isFinite() &&
            increasedWeight.isFinite() &&
            increasedWeight > target.weightKg
    }

    private const val STEP_STORAGE_TOLERANCE_KG = 0.000001
}
