package com.ironlog.app.presentation.workout

import com.ironlog.app.domain.model.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test

class TargetWeightFormattingTest {

    @Test
    fun `metric target keeps kg value and unit`() {
        assertEquals("100.0 kg", formatTargetWeight(100.0, UnitSystem.METRIC))
    }

    @Test
    fun `imperial target converts to lb and shows unit`() {
        assertEquals("220.5 lb", formatTargetWeight(100.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun `target formatting keeps one decimal`() {
        assertEquals("102.5 kg", formatTargetWeight(102.5, UnitSystem.METRIC))
    }
}
