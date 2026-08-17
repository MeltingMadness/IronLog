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

internal object LinearProgressionRuleV1 : ProgressionRule {
    override fun evaluate(context: ProgressionContext): ProgressionOutcome {
        val prepared = prepareProgressionEvaluation(context)
        if (prepared is PreparedProgressionEvaluation.Invalid) return prepared.outcome
        val countedSets = (prepared as PreparedProgressionEvaluation.Valid).sets
        val target = context.sourceTarget.target
        val config = context.sourceTarget.config as ProgressionConfig.Linear

        val actualWeightKg = evaluationWeightKg(countedSets)
            ?: return mixedWeightOutcome(target, countedSets)
        val actualReps = countedSets.minOf(WorkoutSet::reps)
        val repetitionArguments = mapOf(
            "targetReps" to target.reps.toDouble(),
            "actualReps" to actualReps.toDouble()
        )
        if (actualReps < target.reps) {
            return repetitionMissOutcome(
                context,
                countedSets,
                config.failurePolicy,
                repetitionArguments,
                actualWeightKg
            )
        }
        return ProgressionOutcome.ProposeChange(
            sourceTarget = target,
            proposedTarget = target.copy(weightKg = increasedWeight(actualWeightKg, config.step)),
            reasonCode = ProgressionReasonCode.LOAD_ADVANCED,
            reasonArguments = repetitionArguments +
                ("stepOriginalValue" to config.step.originalValue) +
                ("actualWeightKg" to actualWeightKg),
            streakEffect = ProgressionStreakEffect.RESET,
            countedSetIds = countedSets.map(WorkoutSet::id)
        )
    }
}
