package com.ironlog.app.data.reminder

import com.ironlog.app.domain.model.ReminderConfig
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

object ReminderScheduleCalculator {

    fun computeDelay(now: LocalDateTime, config: ReminderConfig): Duration {
        if (config.daysOfWeek.isEmpty()) {
            return Duration.ofDays(1)
        }

        val targetTime = LocalTime.of(config.hour, config.minute)

        for (offset in 0..7) {
            val candidateDate = now.toLocalDate().plusDays(offset.toLong())
            if (candidateDate.dayOfWeek !in config.daysOfWeek) continue

            val candidate = LocalDateTime.of(candidateDate, targetTime)
            if (candidate.isAfter(now)) {
                return Duration.between(now, candidate)
            }
        }

        return Duration.ofDays(1)
    }
}
