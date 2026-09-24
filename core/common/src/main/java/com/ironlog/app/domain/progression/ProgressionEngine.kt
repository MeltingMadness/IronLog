package com.ironlog.app.domain.progression

import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.ProgressionContext
import com.ironlog.app.domain.model.ProgressionOutcome
import com.ironlog.app.domain.model.ProgressionReasonCode
import com.ironlog.app.domain.model.ProgressionScheme
import com.ironlog.app.domain.model.SetType
import com.ironlog.app.domain.model.WorkoutSet

/**
 * Android compatibility façade. Rule decisions are implemented once in the
 * KMP shared module; this class only preserves the domain API used by Room and
 * the Android review flow.
 */
class ProgressionEngine private constructor(
    private val registry: Map<ProgressionRuleKey, ProgressionRule>?
) {
    constructor() : this(null)

    /** Retained for package-local tests and custom rule registries. */
    internal constructor(rules: List<Pair<ProgressionRuleKey, ProgressionRule>>) : this(rules.toMap())

    fun evaluate(context: ProgressionContext): ProgressionOutcome {
        val customRegistry = registry
        if (customRegistry == null) {
            return PortableProgressionAdapter.evaluate(context)
        }

        // Preserve the old registry contract for package-local callers while
        // the production registry uses the shared evaluator directly.
        val config = context.sourceTarget.config
        val availableEvidenceIds = context.setsForTarget
            .filter { it.setType == SetType.NORMAL }
            .sortedWith(compareBy(WorkoutSet::setNumber, WorkoutSet::completedAt, WorkoutSet::id))
            .map(WorkoutSet::id)
            .filter { it > 0L }
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
        val rule = customRegistry[ProgressionRuleKey(config.scheme, config.ruleRevision)]
            ?: return ProgressionOutcome.InsufficientData(
                sourceTarget = context.sourceTarget.target,
                reasonCode = ProgressionReasonCode.RULE_REVISION_UNSUPPORTED,
                countedSetIds = availableEvidenceIds
            )
        return rule.evaluate(context)
    }
}
