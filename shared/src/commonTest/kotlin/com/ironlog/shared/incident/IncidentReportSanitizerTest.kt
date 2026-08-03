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
}
