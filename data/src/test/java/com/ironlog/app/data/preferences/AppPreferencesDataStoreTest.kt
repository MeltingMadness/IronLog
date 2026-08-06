package com.ironlog.app.data.preferences

import com.ironlog.app.domain.model.ReminderConfig
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPreferencesDataStoreTest {

    @Test
    fun `missing key falls back to default reminder days`() {
        val days = parseReminderDays(null)
        assertEquals(ReminderConfig().daysOfWeek, days)
    }

    @Test
    fun `explicitly empty selection stays empty instead of snapping back to default`() {
        val encoded = encodeReminderDays(emptySet())
        val decoded = parseReminderDays(encoded)

        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `legacy blank value keeps historical default decode`() {
        // Older builds encoded empty as "" but then decoded blank back to Mon/Wed/Fri.
        // Keep that decode so already-stored blank values do not suddenly become "no days".
        // New explicit empty selections use the "none" sentinel (covered above).
        val decoded = parseReminderDays("")
        assertEquals(ReminderConfig().daysOfWeek, decoded)
    }

    @Test
    fun `non-empty selection encodes and decodes round trip`() {
        val days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)
        val encoded = encodeReminderDays(days)
        val decoded = parseReminderDays(encoded)

        assertEquals(days, decoded)
    }

    @Test
    fun `invalid tokens are dropped without discarding valid days`() {
        val decoded = parseReminderDays("MONDAY,NOT_A_DAY,FRIDAY")
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), decoded)
    }
}
