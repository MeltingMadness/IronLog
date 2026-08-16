package com.ironlog.app.data.reminder

import com.ironlog.app.domain.model.ReminderConfig
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object ReminderScheduleCalculator {

    /**
     * Computes the real elapsed time until the next configured reminder.
     *
     * WorkManager delays are real-time (elapsed realtime), so the duration is
     * computed between instants in [zoneId] rather than between wall-clock
     * local date-times. That keeps reminders on time across DST transitions:
     * a wall-clock duration would drift by the transition's gap or overlap.
     * Local times that fall into a spring-forward gap are resolved forward by
     * the zone rules (e.g. 02:30 -> 03:30 in Europe/Berlin).
     */
    fun computeDelay(
        now: LocalDateTime,
        config: ReminderConfig,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Duration {
        if (config.daysOfWeek.isEmpty()) {
            return Duration.ofDays(1)
        }

        val targetTime = LocalTime.of(config.hour, config.minute)
        val nowInstant = now.atZone(zoneId).toInstant()

        for (offset in 0..7) {
            val candidateDate = now.toLocalDate().plusDays(offset.toLong())
            if (candidateDate.dayOfWeek !in config.daysOfWeek) continue

            val candidate = LocalDateTime.of(candidateDate, targetTime)
            val candidateInstant = candidate.atZone(zoneId).toInstant()
            if (candidateInstant.isAfter(nowInstant)) {
                return Duration.between(nowInstant, candidateInstant)
            }
        }

        return Duration.ofDays(1)
    }
}
