package com.ironlog.app.domain.progression

import com.ironlog.app.domain.model.ProgressionContext
import com.ironlog.app.domain.model.ProgressionOutcome
import com.ironlog.app.domain.model.ProgressionScheme

internal data class ProgressionRuleKey(val scheme: ProgressionScheme, val revision: Int)

internal fun interface ProgressionRule {
    fun evaluate(context: ProgressionContext): ProgressionOutcome
}
