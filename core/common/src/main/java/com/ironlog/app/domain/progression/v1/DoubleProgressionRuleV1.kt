package com.ironlog.app.domain.progression.v1

import com.ironlog.app.domain.model.ProgressionContext
import com.ironlog.app.domain.model.ProgressionOutcome
import com.ironlog.app.domain.progression.PortableProgressionAdapter
import com.ironlog.app.domain.progression.ProgressionRule

/** Compatibility registry entry; the rule itself lives in shared commonMain. */
internal object DoubleProgressionRuleV1 : ProgressionRule {
    override fun evaluate(context: ProgressionContext): ProgressionOutcome =
        PortableProgressionAdapter.evaluate(context)
}
