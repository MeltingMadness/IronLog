package com.thrym.app.presentation.workout

import com.thrym.app.domain.model.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TargetWeightFormattingTest {

    @Test
    fun `metric target shows kg value without trailing zero`() {
        assertEquals("100 kg", formatTargetWeight(100.0, UnitSystem.METRIC, Locale.US))
        assertEquals("100 kg", formatTargetWeight(100.0, UnitSystem.METRIC, Locale.GERMANY))
    }

    @Test
    fun `imperial target converts to lb and shows unit`() {
        assertEquals("220.5 lb", formatTargetWeight(100.0, UnitSystem.IMPERIAL, Locale.US))
        assertEquals("220,5 lb", formatTargetWeight(100.0, UnitSystem.IMPERIAL, Locale.GERMANY))
    }

    @Test
    fun `target formatting keeps the decimal part`() {
        assertEquals("102.5 kg", formatTargetWeight(102.5, UnitSystem.METRIC, Locale.US))
        assertEquals("102,5 kg", formatTargetWeight(102.5, UnitSystem.METRIC, Locale.GERMANY))
    }
}
