package com.ironlog.app.domain.progression

import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.ProgressionContext
import com.ironlog.app.domain.model.ProgressionOutcome
import com.ironlog.app.domain.model.ProgressionReasonCode
import com.ironlog.app.domain.model.ProgressionScheme
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.progression.v1.DoubleProgressionRuleV1
import com.ironlog.app.domain.progression.v1.LinearProgressionRuleV1
import com.ironlog.app.domain.progression.v1.RpeProgressionRuleV1
import com.ironlog.app.domain.progression.v1.TotalRepsProgressionRuleV1

class ProgressionEngine private constructor(
    private val registry: Map<ProgressionRuleKey, ProgressionRule>
) {
    constructor() : this(
        listOf(
            ProgressionRuleKey(ProgressionScheme.LINEAR, 1) to LinearProgressionRuleV1,
            ProgressionRuleKey(ProgressionScheme.DOUBLE, 1) to DoubleProgressionRuleV1,
            ProgressionRuleKey(ProgressionScheme.TOTAL_REPS, 1) to TotalRepsProgressionRuleV1,
            ProgressionRuleKey(ProgressionScheme.RPE_RIR, 1) to RpeProgressionRuleV1
        ).toMap()
    )

    internal constructor(rules: List<Pair<ProgressionRuleKey, ProgressionRule>>) : this(rules.toMap())

    fun evaluate(context: ProgressionContext): ProgressionOutcome {
        val config = context.sourceTarget.config
        val availableEvidenceIds = context.setsForTarget
            .filterNot(WorkoutSet::isWarmup)
            .sortedWith(compareBy(WorkoutSet::setNumber, WorkoutSet::completedAt, WorkoutSet::id))
            .map(WorkoutSet::id)
            .filter { it > 0 }
            .distinct()
        if (config is ProgressionConfig.Invalid) {
            return ProgressionOutcome.InsufficientData(
                sourceTarget = context.sourceTarget.target,
                reasonCode = ProgressionReasonCode.CONFIG_INVALID,
                countedSetIds = availableEvidenceIds
            )
        }
        if (config.scheme == ProgressionScheme.MANUAL) {
            return ProgressionOutcome.NotApplicable(context.sourceTarget.target)
        }
        val rule = registry[ProgressionRuleKey(config.scheme, config.ruleRevision)]
            ?: return ProgressionOutcome.InsufficientData(
                sourceTarget = context.sourceTarget.target,
                reasonCode = ProgressionReasonCode.RULE_REVISION_UNSUPPORTED,
                countedSetIds = availableEvidenceIds
            )
        return rule.evaluate(context)
    }
}
