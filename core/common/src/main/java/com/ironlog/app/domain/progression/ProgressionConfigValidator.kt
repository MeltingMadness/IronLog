package com.ironlog.app.domain.progression

import com.ironlog.app.domain.model.FailurePolicy
import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.ProgressionTarget
import com.ironlog.app.domain.model.WeightStep
import com.ironlog.app.domain.util.WeightFormatting
import kotlin.math.abs

object ProgressionConfigValidator {
    fun validationErrors(target: ProgressionTarget, config: ProgressionConfig): List<String> {
        val errors = linkedSetOf<String>()
        if (target.sets <= 0) errors += "target.sets"
        if (target.reps <= 0) errors += "target.reps"
        if (!target.weightKg.isFinite() || target.weightKg < 0.0) errors += "target.weightKg"

        when (config) {
            is ProgressionConfig.Manual -> Unit
            is ProgressionConfig.Linear -> {
                addActiveConfigErrors(errors, target, config.step, config.failurePolicy)
            }
            is ProgressionConfig.DoubleProgression -> {
                addActiveConfigErrors(errors, target, config.step, config.failurePolicy)
                if (config.minReps < 1 || config.minReps > target.reps) errors += "config.minReps"
                if (config.maxReps < target.reps || config.maxReps < config.minReps) {
                    errors += "config.maxReps"
                }
            }
            is ProgressionConfig.TotalReps -> {
                addActiveConfigErrors(errors, target, config.step, config.failurePolicy)
                if (config.targetTotalReps <= 0L) errors += "config.targetTotalReps"
            }
            is ProgressionConfig.RpeRir -> {
                addActiveConfigErrors(errors, target, config.step, config.failurePolicy)
                if (!config.targetRpe.isFinite() || config.targetRpe !in 1.0..10.0) {
                    errors += "config.targetRpe"
                }
                if (!config.tolerance.isFinite() || config.tolerance !in 0.0..2.0) {
                    errors += "config.tolerance"
                }
            }
            is ProgressionConfig.Invalid -> errors += "config"
        }
        return errors.toList()
    }

    internal fun isValid(target: ProgressionTarget, config: ProgressionConfig): Boolean =
        validationErrors(target, config).isEmpty()

    private fun addActiveConfigErrors(
        errors: MutableSet<String>,
        target: ProgressionTarget,
        step: WeightStep,
        failurePolicy: FailurePolicy
    ) {
        if (!step.originalValue.isFinite() || step.originalValue <= 0.0) {
            errors += "config.step.originalValue"
        }
        if (!step.kilograms.isFinite() || step.kilograms <= 0.0) {
            errors += "config.step.kilograms"
        }
        val convertedStep = WeightFormatting.convertToKg(step.originalValue, step.originalUnit)
        if (!convertedStep.isFinite() || abs(convertedStep - step.kilograms) > STEP_STORAGE_TOLERANCE_KG) {
            errors += "config.step.kilograms"
        }
        if (failurePolicy.stallThreshold !in 1..6) {
            errors += "config.failurePolicy.stallThreshold"
        }
        if (!failurePolicy.backoffPercent.isFinite() || failurePolicy.backoffPercent !in 1.0..30.0) {
            errors += "config.failurePolicy.backoffPercent"
        }

        val targetInOriginalUnit = WeightFormatting.convertToDisplay(target.weightKg, step.originalUnit)
        val increasedWeight = target.weightKg + step.kilograms
        if (!targetInOriginalUnit.isFinite()) errors += "target.weightKg"
        if (!increasedWeight.isFinite() || increasedWeight <= target.weightKg) {
            errors += "config.step.kilograms"
        }
    }

    private const val STEP_STORAGE_TOLERANCE_KG = 0.000001
}
