package com.ironlog.shared.incident

import kotlin.test.Test
import kotlin.test.assertEquals

class IncidentReportSanitizerTest {

    @Test
    fun `redacts keyed ids and emails`() {
        val sanitized = IncidentReportSanitizer.sanitizeText(
            "sessionId=42 planId=7 owner=test@example.com and plain second@example.com",
        )

        assertEquals(
            "sessionId=<redacted> planId=<redacted> owner=<redacted-email> and plain <redacted-email>",
            sanitized,
        )
    }

    @Test
    fun `redacts word plus number ids`() {
        val sanitized = IncidentReportSanitizer.sanitizeText(
            "Training plan 5 does not exist; Workout set 42 failed",
        )

        assertEquals(
            "Training plan <redacted> does not exist; Workout set <redacted> failed",
            sanitized,
        )
    }

    @Test
    fun `passes through screen labels and text without ids`() {
        val sanitized = IncidentReportSanitizer.sanitizeText(
            "currentScreen=settings, sessionId=42",
        )

        assertEquals(
            "currentScreen=settings, sessionId=<redacted>",
            sanitized,
        )
    }
}
