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

internal object DoubleProgressionRuleV1 : ProgressionRule {
    override fun evaluate(context: ProgressionContext): ProgressionOutcome {
        val prepared = prepareProgressionEvaluation(context)
        if (prepared is PreparedProgressionEvaluation.Invalid) return prepared.outcome
        val countedSets = (prepared as PreparedProgressionEvaluation.Valid).sets
        val target = context.sourceTarget.target
        val config = context.sourceTarget.config as ProgressionConfig.DoubleProgression

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
        if (target.reps < config.maxReps) {
            return ProgressionOutcome.ProposeChange(
                sourceTarget = target,
                // Adopt the actually trained weight so the plan converges to
                // reality even when the user deviates from the plan target.
                proposedTarget = target.copy(reps = target.reps + 1, weightKg = actualWeightKg),
                reasonCode = ProgressionReasonCode.REP_TARGET_ADVANCED,
                reasonArguments = mapOf("actualWeightKg" to actualWeightKg),
                streakEffect = ProgressionStreakEffect.RESET,
                countedSetIds = countedSets.map(WorkoutSet::id)
            )
        }
        return ProgressionOutcome.ProposeChange(
            sourceTarget = target,
            proposedTarget = target.copy(
                reps = config.minReps,
                weightKg = increasedWeight(actualWeightKg, config.step)
            ),
            reasonCode = ProgressionReasonCode.LOAD_ADVANCED,
            reasonArguments = mapOf(
                "targetReps" to target.reps.toDouble(),
                "actualReps" to actualReps.toDouble(),
                "stepOriginalValue" to config.step.originalValue,
                "actualWeightKg" to actualWeightKg
            ),
            streakEffect = ProgressionStreakEffect.RESET,
            countedSetIds = countedSets.map(WorkoutSet::id)
        )
    }
}
