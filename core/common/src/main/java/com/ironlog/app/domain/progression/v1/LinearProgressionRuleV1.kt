package com.ironlog.app.domain.progression.v1

import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.ProgressionContext
import com.ironlog.app.domain.model.ProgressionOutcome
import com.ironlog.app.domain.model.ProgressionReasonCode
import com.ironlog.app.domain.model.ProgressionStreakEffect
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.progression.PreparedProgressionEvaluation
import com.ironlog.app.domain.progression.ProgressionRule
import com.ironlog.app.domain.progression.firstWeightDeviation
import com.ironlog.app.domain.progression.increasedWeight
import com.ironlog.app.domain.progression.prepareProgressionEvaluation
import com.ironlog.app.domain.progression.repetitionMissOutcome
import com.ironlog.app.domain.progression.weightDeviationOutcome

internal object LinearProgressionRuleV1 : ProgressionRule {
    override fun evaluate(context: ProgressionContext): ProgressionOutcome {
        val prepared = prepareProgressionEvaluation(context)
        if (prepared is PreparedProgressionEvaluation.Invalid) return prepared.outcome
        val countedSets = (prepared as PreparedProgressionEvaluation.Valid).sets
        val target = context.sourceTarget.target
        val config = context.sourceTarget.config as ProgressionConfig.Linear

        firstWeightDeviation(countedSets, target.weightKg)?.let {
            return weightDeviationOutcome(target, countedSets, it)
        }
        val actualReps = countedSets.minOf(WorkoutSet::reps)
        val repetitionArguments = mapOf(
            "targetReps" to target.reps.toDouble(),
            "actualReps" to actualReps.toDouble()
        )
        if (actualReps < target.reps) {
            return repetitionMissOutcome(context, countedSets, config.failurePolicy, repetitionArguments)
        }
        return ProgressionOutcome.ProposeChange(
            sourceTarget = target,
            proposedTarget = target.copy(weightKg = increasedWeight(target.weightKg, config.step)),
            reasonCode = ProgressionReasonCode.LOAD_ADVANCED,
            reasonArguments = repetitionArguments + ("stepOriginalValue" to config.step.originalValue),
            streakEffect = ProgressionStreakEffect.RESET,
            countedSetIds = countedSets.map(WorkoutSet::id)
        )
    }
}
