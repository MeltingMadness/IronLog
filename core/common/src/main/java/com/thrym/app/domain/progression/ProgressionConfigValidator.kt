package com.thrym.app.domain.progression

import com.thrym.app.domain.model.ProgressionConfig
import com.thrym.app.domain.model.ProgressionTarget

/** Android API preserved while validation is evaluated by commonMain. */
object ProgressionConfigValidator {
    fun validationErrors(target: ProgressionTarget, config: ProgressionConfig): List<String> =
        PortableProgressionAdapter.validationErrors(target, config)

    internal fun isValid(target: ProgressionTarget, config: ProgressionConfig): Boolean =
        validationErrors(target, config).isEmpty()
}
