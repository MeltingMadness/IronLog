package com.thrym.app.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UiLocaleTest {

    @Test
    fun `supported locales are kept with region and variant`() {
        assertEquals(Locale.forLanguageTag("de-AT"), uiLocale(Locale.forLanguageTag("de-AT"), "de"))
        assertEquals(Locale.forLanguageTag("en-GB"), uiLocale(Locale.forLanguageTag("en-GB"), "en"))
    }

    @Test
    fun `unsupported language takes the resolved language and keeps the device region`() {
        // fr-FR falls back to English text, so month and weekday names must be English too.
        assertEquals(Locale.forLanguageTag("en-FR"), uiLocale(Locale.FRANCE, "en"))
        // [fr-FR, de-DE] resolves the strings to German.
        assertEquals(Locale.forLanguageTag("de-FR"), uiLocale(Locale.FRANCE, "de"))
        assertEquals(Locale.ENGLISH, uiLocale(Locale.FRENCH, "en"))
    }
}
