package com.ironlog.app.domain.progression.v1

import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.ProgressionContext
import com.ironlog.app.domain.model.ProgressionOutcome
import com.ironlog.app.domain.model.ProgressionReasonCode
import com.ironlog.app.domain.model.ProgressionStreakEffect
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.progression.PreparedProgressionEvaluation
import com.ironlog.app.domain.progression.ProgressionRule
import com.ironlog.app.domain.progression.evaluationWeightKg
import com.ironlog.app.domain.progression.increasedWeight
import com.ironlog.app.domain.progression.mixedWeightOutcome
import com.ironlog.app.domain.progression.prepareProgressionEvaluation
import com.ironlog.app.domain.progression.repetitionMissOutcome

internal object RpeProgressionRuleV1 : ProgressionRule {
    override fun evaluate(context: ProgressionContext): ProgressionOutcome {
        val prepared = prepareProgressionEvaluation(context)
        if (prepared is PreparedProgressionEvaluation.Invalid) return prepared.outcome
        val countedSets = (prepared as PreparedProgressionEvaluation.Valid).sets
        val target = context.sourceTarget.target
        val config = context.sourceTarget.config as ProgressionConfig.RpeRir

        val actualWeightKg = evaluationWeightKg(countedSets)
            ?: return mixedWeightOutcome(target, countedSets)
        val actualReps = countedSets.minOf(WorkoutSet::reps)
        if (actualReps < target.reps) {
            return repetitionMissOutcome(
                context,
                countedSets,
                config.failurePolicy,
                mapOf("targetReps" to target.reps.toDouble(), "actualReps" to actualReps.toDouble()),
                actualWeightKg
            )
        }
        if (countedSets.any { it.rpe == null }) {
            return ProgressionOutcome.InsufficientData(
                sourceTarget = target,
                reasonCode = ProgressionReasonCode.RPE_MISSING,
                countedSetIds = countedSets.map(WorkoutSet::id)
            )
        }
        val rpes = countedSets.map { requireNotNull(it.rpe) }
        if (rpes.any { !it.isFinite() || it !in 1.0..10.0 }) {
            return ProgressionOutcome.InsufficientData(
                sourceTarget = target,
                reasonCode = ProgressionReasonCode.RPE_INVALID,
                countedSetIds = countedSets.map(WorkoutSet::id)
            )
        }
        val highestRpe = rpes.max()
        if (highestRpe > config.targetRpe + config.tolerance) {
            return ProgressionOutcome.KeepTarget(
                sourceTarget = target,
                reasonCode = ProgressionReasonCode.REPEAT_TARGET,
                reasonArguments = mapOf("highestRpe" to highestRpe),
                streakEffect = ProgressionStreakEffect.INCREMENT,
                countedSetIds = countedSets.map(WorkoutSet::id)
            )
        }
        return ProgressionOutcome.ProposeChange(
            sourceTarget = target,
            proposedTarget = target.copy(weightKg = increasedWeight(actualWeightKg, config.step)),
            reasonCode = ProgressionReasonCode.RPE_WITHIN_TARGET,
            reasonArguments = mapOf(
                "highestRpe" to highestRpe,
                "targetRpe" to config.targetRpe,
                "tolerance" to config.tolerance,
                "stepOriginalValue" to config.step.originalValue,
                "actualWeightKg" to actualWeightKg
            ),
            streakEffect = ProgressionStreakEffect.RESET,
            countedSetIds = countedSets.map(WorkoutSet::id)
        )
    }
}
