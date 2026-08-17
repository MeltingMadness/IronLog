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

internal object TotalRepsProgressionRuleV1 : ProgressionRule {
    override fun evaluate(context: ProgressionContext): ProgressionOutcome {
        val prepared = prepareProgressionEvaluation(context)
        if (prepared is PreparedProgressionEvaluation.Invalid) return prepared.outcome
        val countedSets = (prepared as PreparedProgressionEvaluation.Valid).sets
        val target = context.sourceTarget.target
        val config = context.sourceTarget.config as ProgressionConfig.TotalReps

        val actualWeightKg = evaluationWeightKg(countedSets)
            ?: return mixedWeightOutcome(target, countedSets)
        val achievedTotalReps = try {
            countedSets.fold(0L) { total, set -> Math.addExact(total, set.reps.toLong()) }
        } catch (_: ArithmeticException) {
            return ProgressionOutcome.InsufficientData(
                sourceTarget = target,
                reasonCode = ProgressionReasonCode.SET_VALUE_INVALID,
                countedSetIds = countedSets.map(WorkoutSet::id)
            )
        }
        val totalArguments = mapOf(
            "achievedTotalReps" to achievedTotalReps.toDouble(),
            "targetTotalReps" to config.targetTotalReps.toDouble()
        )
        if (achievedTotalReps < config.targetTotalReps) {
            return repetitionMissOutcome(context, countedSets, config.failurePolicy, totalArguments, actualWeightKg)
        }
        return ProgressionOutcome.ProposeChange(
            sourceTarget = target,
            proposedTarget = target.copy(weightKg = increasedWeight(actualWeightKg, config.step)),
            reasonCode = ProgressionReasonCode.TOTAL_REPS_COMPLETED,
            reasonArguments = totalArguments +
                ("stepOriginalValue" to config.step.originalValue) +
                ("actualWeightKg" to actualWeightKg),
            streakEffect = ProgressionStreakEffect.RESET,
            countedSetIds = countedSets.map(WorkoutSet::id)
        )
    }
}
