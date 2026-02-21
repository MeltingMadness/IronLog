package com.ironlog.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppPreferencesVisualDefaultsTest {

    @Test
    fun `visual defaults are brand first`() {
        val preferences = AppPreferences()

        assertEquals(ThemeMode.SYSTEM, preferences.themeMode)
        assertFalse(preferences.useDynamicColor)
        assertFalse(preferences.reducedMotion)
    }
}
