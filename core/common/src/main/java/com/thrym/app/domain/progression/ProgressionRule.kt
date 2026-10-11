package com.thrym.app.domain.progression

import com.thrym.app.domain.model.ProgressionContext
import com.thrym.app.domain.model.ProgressionOutcome
import com.thrym.app.domain.model.ProgressionScheme

internal data class ProgressionRuleKey(val scheme: ProgressionScheme, val revision: Int)

internal fun interface ProgressionRule {
    fun evaluate(context: ProgressionContext): ProgressionOutcome
}
