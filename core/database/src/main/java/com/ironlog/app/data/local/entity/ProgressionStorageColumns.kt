package com.ironlog.app.data.local.entity

import androidx.room.ColumnInfo
import com.ironlog.app.domain.model.FailurePolicy
import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.ProgressionScheme
import com.ironlog.app.domain.model.ProgressionTarget
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.model.WeightStep

data class ProgressionTargetColumns(
    @ColumnInfo(name = "Sets") val sets: Int,
    @ColumnInfo(name = "Reps") val reps: Int,
    @ColumnInfo(name = "WeightKg") val weightKg: Double
) {
    fun toDomain() = ProgressionTarget(sets, reps, weightKg)

    companion object {
        fun fromDomain(value: ProgressionTarget) =
            ProgressionTargetColumns(value.sets, value.reps, value.weightKg)
    }
}

data class ProgressionConfigColumns(
    @ColumnInfo(name = "Scheme", defaultValue = "'MANUAL'")
    val scheme: String = ProgressionScheme.MANUAL.name,
    @ColumnInfo(name = "IncrementValue") val incrementValue: Double? = null,
    @ColumnInfo(name = "IncrementUnit") val incrementUnit: String? = null,
    @ColumnInfo(name = "IncrementKg") val incrementKg: Double? = null,
    @ColumnInfo(name = "MinReps") val minReps: Int? = null,
    @ColumnInfo(name = "MaxReps") val maxReps: Int? = null,
    @ColumnInfo(name = "TargetTotalReps") val targetTotalReps: Long? = null,
    @ColumnInfo(name = "TargetRpe") val targetRpe: Double? = null,
    @ColumnInfo(name = "RpeTolerance") val rpeTolerance: Double? = null,
    @ColumnInfo(name = "StallThreshold", defaultValue = "2") val stallThreshold: Int = 2,
    @ColumnInfo(name = "BackoffPercent", defaultValue = "10.0") val backoffPercent: Double = 10.0,
    @ColumnInfo(name = "RuleRevision", defaultValue = "1") val ruleRevision: Int = 1
) {
    fun toDomain(): ProgressionConfig {
        val parsedScheme = runCatching { enumValueOf<ProgressionScheme>(scheme) }.getOrNull()
            ?: return ProgressionConfig.Invalid(
                scheme = ProgressionScheme.MANUAL,
                ruleRevision = ruleRevision,
                storageReason = "UNKNOWN_SCHEME",
                rawScheme = scheme
            )

        fun invalid(reason: String) = ProgressionConfig.Invalid(
            scheme = parsedScheme,
            ruleRevision = ruleRevision,
            storageReason = reason,
            rawScheme = scheme
        )

        val parsedUnit = incrementUnit?.let {
            runCatching { enumValueOf<UnitSystem>(it) }.getOrNull()
        }
        val step = if (
            incrementValue != null &&
            parsedUnit != null &&
            incrementKg != null
        ) {
            WeightStep(
                originalValue = incrementValue,
                originalUnit = parsedUnit,
                kilograms = incrementKg
            )
        } else {
            null
        }
        val failurePolicy = FailurePolicy(stallThreshold, backoffPercent)

        return when (parsedScheme) {
            ProgressionScheme.MANUAL -> {
                if (!allNull(incrementValue, incrementUnit, incrementKg, minReps, maxReps, targetTotalReps, targetRpe, rpeTolerance)) {
                    invalid("UNUSED_FIELDS_PRESENT")
                } else {
                    ProgressionConfig.Manual(ruleRevision)
                }
            }

            ProgressionScheme.LINEAR -> {
                if (step == null || !allNull(minReps, maxReps, targetTotalReps, targetRpe, rpeTolerance)) {
                    invalid("MALFORMED_LINEAR")
                } else {
                    ProgressionConfig.Linear(step, failurePolicy, ruleRevision)
                }
            }

            ProgressionScheme.DOUBLE -> {
                if (step == null || minReps == null || maxReps == null || !allNull(targetTotalReps, targetRpe, rpeTolerance)) {
                    invalid("MALFORMED_DOUBLE")
                } else {
                    ProgressionConfig.DoubleProgression(
                        minReps,
                        maxReps,
                        step,
                        failurePolicy,
                        ruleRevision
                    )
                }
            }

            ProgressionScheme.TOTAL_REPS -> {
                if (step == null || targetTotalReps == null || !allNull(minReps, maxReps, targetRpe, rpeTolerance)) {
                    invalid("MALFORMED_TOTAL_REPS")
                } else {
                    ProgressionConfig.TotalReps(
                        targetTotalReps,
                        step,
                        failurePolicy,
                        ruleRevision
                    )
                }
            }

            ProgressionScheme.RPE_RIR -> {
                if (step == null || targetRpe == null || rpeTolerance == null || !allNull(minReps, maxReps, targetTotalReps)) {
                    invalid("MALFORMED_RPE_RIR")
                } else {
                    ProgressionConfig.RpeRir(
                        targetRpe,
                        rpeTolerance,
                        step,
                        failurePolicy,
                        ruleRevision
                    )
                }
            }
        }
    }

    companion object {
        fun fromDomain(value: ProgressionConfig): ProgressionConfigColumns = when (value) {
            is ProgressionConfig.Manual -> ProgressionConfigColumns(
                scheme = value.scheme.name,
                ruleRevision = value.ruleRevision
            )

            is ProgressionConfig.Linear -> fromActiveConfig(
                scheme = value.scheme,
                step = value.step,
                failurePolicy = value.failurePolicy,
                ruleRevision = value.ruleRevision
            )

            is ProgressionConfig.DoubleProgression -> fromActiveConfig(
                scheme = value.scheme,
                step = value.step,
                failurePolicy = value.failurePolicy,
                ruleRevision = value.ruleRevision,
                minReps = value.minReps,
                maxReps = value.maxReps
            )

            is ProgressionConfig.TotalReps -> fromActiveConfig(
                scheme = value.scheme,
                step = value.step,
                failurePolicy = value.failurePolicy,
                ruleRevision = value.ruleRevision,
                targetTotalReps = value.targetTotalReps
            )

            is ProgressionConfig.RpeRir -> fromActiveConfig(
                scheme = value.scheme,
                step = value.step,
                failurePolicy = value.failurePolicy,
                ruleRevision = value.ruleRevision,
                targetRpe = value.targetRpe,
                rpeTolerance = value.tolerance
            )

            is ProgressionConfig.Invalid -> throw IllegalArgumentException(
                "ProgressionConfig.Invalid cannot be stored losslessly"
            )
        }

        private fun fromActiveConfig(
            scheme: ProgressionScheme,
            step: WeightStep,
            failurePolicy: FailurePolicy,
            ruleRevision: Int,
            minReps: Int? = null,
            maxReps: Int? = null,
            targetTotalReps: Long? = null,
            targetRpe: Double? = null,
            rpeTolerance: Double? = null
        ) = ProgressionConfigColumns(
            scheme = scheme.name,
            incrementValue = step.originalValue,
            incrementUnit = step.originalUnit.name,
            incrementKg = step.kilograms,
            minReps = minReps,
            maxReps = maxReps,
            targetTotalReps = targetTotalReps,
            targetRpe = targetRpe,
            rpeTolerance = rpeTolerance,
            stallThreshold = failurePolicy.stallThreshold,
            backoffPercent = failurePolicy.backoffPercent,
            ruleRevision = ruleRevision
        )

        private fun allNull(vararg values: Any?): Boolean = values.all { it == null }
    }
}
