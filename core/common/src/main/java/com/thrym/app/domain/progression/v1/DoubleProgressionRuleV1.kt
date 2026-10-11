package com.thrym.app.domain.progression.v1

import com.thrym.app.domain.model.ProgressionContext
import com.thrym.app.domain.model.ProgressionOutcome
import com.thrym.app.domain.progression.PortableProgressionAdapter
import com.thrym.app.domain.progression.ProgressionRule

/** Compatibility registry entry; the rule itself lives in shared commonMain. */
internal object DoubleProgressionRuleV1 : ProgressionRule {
    override fun evaluate(context: ProgressionContext): ProgressionOutcome =
        PortableProgressionAdapter.evaluate(context)
}
