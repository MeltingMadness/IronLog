package com.ironlog.app.data.reminder

import com.ironlog.app.domain.model.ReminderConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

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

    @Test
    fun springForwardDay_usesRealElapsedTimeInsteadOfWallClock() {
        val berlin = ZoneId.of("Europe/Berlin")
        val now = LocalDateTime.of(2026, 3, 28, 18, 0) // Saturday before the DST switch
        val config = ReminderConfig(
            enabled = true,
            hour = 3,
            minute = 30,
            daysOfWeek = setOf(DayOfWeek.SUNDAY)
        )

        val delay = ReminderScheduleCalculator.computeDelay(now, config, berlin)

        // 2026-03-29 03:30 CEST is 8.5 real hours after Sat 18:00 CET
        // (wall clock would claim 9.5 h); WorkManager delays run on real time.
        assertEquals(8L * 60L * 60L + 30L * 60L, delay.seconds)
    }

    @Test
    fun springForwardGapTarget_resolvesForwardAndSchedulesOnRealTime() {
        val berlin = ZoneId.of("Europe/Berlin")
        val now = LocalDateTime.of(2026, 3, 28, 18, 0)
        val config = ReminderConfig(
            enabled = true,
            hour = 2,
            minute = 30,
            daysOfWeek = setOf(DayOfWeek.SUNDAY)
        )

        val delay = ReminderScheduleCalculator.computeDelay(now, config, berlin)

        // 02:30 does not exist on 2026-03-29 (gap 02:00-03:00); java.time
        // resolves it forward to 03:30 CEST, i.e. 8.5 real hours.
        assertEquals(8L * 60L * 60L + 30L * 60L, delay.seconds)
    }
}
