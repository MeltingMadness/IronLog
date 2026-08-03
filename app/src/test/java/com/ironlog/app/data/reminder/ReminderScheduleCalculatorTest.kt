package com.ironlog.app.data.reminder

import com.ironlog.app.domain.model.ReminderConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class ReminderScheduleCalculatorTest {

    @Test
    fun sameDayBeforeReminder_returnsSameDayDelay() {
        val now = LocalDateTime.of(2026, 2, 16, 18, 0) // Monday
        val config = ReminderConfig(
            enabled = true,
            hour = 19,
            minute = 30,
            daysOfWeek = setOf(DayOfWeek.MONDAY)
        )

        val delay = ReminderScheduleCalculator.computeDelay(now, config)

        assertEquals(90 * 60L, delay.seconds)
    }

    @Test
    fun sameDayAfterReminder_rollsToNextConfiguredDay() {
        val now = LocalDateTime.of(2026, 2, 16, 21, 0) // Monday
        val config = ReminderConfig(
            enabled = true,
            hour = 19,
            minute = 30,
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        )

        val delay = ReminderScheduleCalculator.computeDelay(now, config)

        assertEquals(46L * 60L * 60L + 30L * 60L, delay.seconds)
    }
}
