package com.ironlog.app.data.incident

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentReportSanitizerTest {

    @Test
    fun sanitizeText_redactsIdsAndEmail() {
        val raw = "Absturz in Dashboard, sessionId=1234, planId=8, user=test@example.com"

        val sanitized = IncidentReportSanitizer.sanitizeText(raw)

        assertFalse(sanitized.contains("1234"))
        assertFalse(sanitized.contains("test@example.com"))
        assertTrue(sanitized.contains("sessionId=<redacted>"))
        assertTrue(sanitized.contains("planId=<redacted>"))
        assertTrue(sanitized.contains("user=<redacted-email>"))
    }

    @Test
    fun sanitizeText_redactsWordNumberIds() {
        val raw = "Training plan 5 does not exist; Workout set 42 konnte nicht geladen werden"

        val sanitized = IncidentReportSanitizer.sanitizeText(raw)

        assertFalse(sanitized.contains("plan 5"))
        assertFalse(sanitized.contains("set 42"))
        assertTrue(sanitized.contains("Training plan <redacted>"))
        assertTrue(sanitized.contains("Workout set <redacted>"))
    }

    @Test
    fun sanitizeText_leavesWordNumberTextWithoutAnIdUntouched() {
        val raw = "Absturz auf Workout-Screen mit 3 Versuchen"

        val sanitized = IncidentReportSanitizer.sanitizeText(raw)

        assertEquals(raw, sanitized)
    }

    @Test
    fun sanitizeText_passesThroughPlainScreenLabels() {
        val raw = "currentScreen=settings, sessionId=1234"

        val sanitized = IncidentReportSanitizer.sanitizeText(raw)

        // The screen label is passed through unsanitized; only ids are redacted.
        assertTrue(sanitized.contains("currentScreen=settings"))
        assertTrue(sanitized.contains("sessionId=<redacted>"))
    }
}
