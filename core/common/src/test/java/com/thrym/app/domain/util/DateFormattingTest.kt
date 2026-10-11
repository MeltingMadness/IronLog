package com.thrym.app.domain.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locale-aware date formatting; every case pins its locale so the JVM default does not matter. */
class DateFormattingTest {

    private val date = LocalDate.of(2026, 2, 10)
    private val afternoon = LocalDateTime.of(2026, 2, 10, 14, 30)

    @Test
    fun `German keeps the previous patterns`() {
        assertEquals("Dienstag, 10.02.2026", date.format(DateFormatting.dateFull(Locale.GERMANY)))
        assertEquals("10.02.2026, 14:30", afternoon.format(DateFormatting.dateTime(Locale.GERMANY)))
        assertEquals("10.02.", date.format(DateFormatting.dayMonth(Locale.GERMANY)))
        assertEquals("Dienstag, 10.02.", date.format(DateFormatting.weekdayDayMonth(Locale.GERMANY)))
        assertEquals("14:30", DateFormatting.formatClock(14, 30, Locale.GERMANY))
        assertEquals("Di.", DateFormatting.dayShort(DayOfWeek.TUESDAY, Locale.GERMANY))
    }

    @Test
    fun `English uses month first and the 12-hour clock`() {
        assertEquals("Tuesday, Feb 10, 2026", date.format(DateFormatting.dateFull(Locale.US)))
        assertEquals("2/10", date.format(DateFormatting.dayMonth(Locale.US)))
        assertEquals("Tuesday, 2/10", date.format(DateFormatting.weekdayDayMonth(Locale.US)))
        assertEquals("Tue", DateFormatting.dayShort(DayOfWeek.TUESDAY, Locale.US))
        val clock = DateFormatting.formatClock(14, 30, Locale.US)
        assertTrue(clock, clock.startsWith("2:30") && clock.endsWith("PM"))
        val dateTime = afternoon.format(DateFormatting.dateTime(Locale.US))
        assertTrue(dateTime, dateTime.startsWith("Feb 10, 2026") && dateTime.endsWith("PM"))
    }

    @Test
    fun `British English puts the day first`() {
        assertEquals("10/02", date.format(DateFormatting.dayMonth(Locale.UK)))
    }

    @Test
    fun `system clock setting overrides the locale default`() {
        assertEquals("14:30", DateFormatting.formatClock(14, 30, Locale.US, use24Hour = true))
        val dateTime = afternoon.format(DateFormatting.dateTime(Locale.US, use24Hour = true))
        assertTrue(dateTime, dateTime.endsWith("14:30"))
        assertFalse(dateTime, dateTime.contains("PM"))

        val germanTwelveHour = DateFormatting.formatClock(14, 30, Locale.GERMANY, use24Hour = false)
        assertTrue(germanTwelveHour, germanTwelveHour.startsWith("2:30 "))
        assertEquals("09:05", DateFormatting.formatClock(9, 5, Locale.GERMANY, use24Hour = true))
    }

    @Test
    fun `year is removed together with its separator`() {
        assertEquals("M/d", DateFormatting.withoutYear("M/d/yy"))
        assertEquals("dd.MM.", DateFormatting.withoutYear("dd.MM.yy"))
        assertEquals("dd/MM", DateFormatting.withoutYear("dd/MM/y"))
        assertEquals("MM-dd", DateFormatting.withoutYear("y-MM-dd"))
    }

    @Test
    fun `hour cycle switch leaves quoted literals alone`() {
        assertEquals("HH:mm", DateFormatting.withHourCycle("h:mm a", use24Hour = true))
        assertEquals("HH:mm", DateFormatting.withHourCycle("h:mm a", use24Hour = true))
        assertEquals("h:mm a", DateFormatting.withHourCycle("HH:mm", use24Hour = false))
        assertEquals("HH 'h' mm", DateFormatting.withHourCycle("HH 'h' mm", use24Hour = true))
        assertEquals("HH:mm", DateFormatting.withHourCycle("HH:mm", use24Hour = null))
    }
}
