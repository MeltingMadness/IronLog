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

internal object DoubleProgressionRuleV1 : ProgressionRule {
    override fun evaluate(context: ProgressionContext): ProgressionOutcome {
        val prepared = prepareProgressionEvaluation(context)
        if (prepared is PreparedProgressionEvaluation.Invalid) return prepared.outcome
        val countedSets = (prepared as PreparedProgressionEvaluation.Valid).sets
        val target = context.sourceTarget.target
        val config = context.sourceTarget.config as ProgressionConfig.DoubleProgression

        firstWeightDeviation(countedSets, target.weightKg)?.let {
            return weightDeviationOutcome(target, countedSets, it)
        }
        val actualReps = countedSets.minOf(WorkoutSet::reps)
        if (actualReps < target.reps) {
            return repetitionMissOutcome(
                context,
                countedSets,
                config.failurePolicy,
                mapOf("targetReps" to target.reps.toDouble(), "actualReps" to actualReps.toDouble())
            )
        }
        if (target.reps < config.maxReps) {
            return ProgressionOutcome.ProposeChange(
                sourceTarget = target,
                proposedTarget = target.copy(reps = target.reps + 1),
                reasonCode = ProgressionReasonCode.REP_TARGET_ADVANCED,
                reasonArguments = emptyMap(),
                streakEffect = ProgressionStreakEffect.RESET,
                countedSetIds = countedSets.map(WorkoutSet::id)
            )
        }
        return ProgressionOutcome.ProposeChange(
            sourceTarget = target,
            proposedTarget = target.copy(
                reps = config.minReps,
                weightKg = increasedWeight(target.weightKg, config.step)
            ),
            reasonCode = ProgressionReasonCode.LOAD_ADVANCED,
            reasonArguments = mapOf(
                "targetReps" to target.reps.toDouble(),
                "actualReps" to actualReps.toDouble(),
                "stepOriginalValue" to config.step.originalValue
            ),
            streakEffect = ProgressionStreakEffect.RESET,
            countedSetIds = countedSets.map(WorkoutSet::id)
        )
    }
}
