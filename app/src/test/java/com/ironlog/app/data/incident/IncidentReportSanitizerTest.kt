package com.ironlog.app.data.incident

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
}
