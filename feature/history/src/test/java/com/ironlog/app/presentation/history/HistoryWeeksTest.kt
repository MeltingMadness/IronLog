package com.ironlog.app.presentation.history

import com.ironlog.app.domain.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class HistoryWeeksTest {

    private fun item(id: Long, start: LocalDateTime, seconds: Long = 3600) =
        WorkoutHistoryItem(session = WorkoutSession(id = id, startTime = start, durationSeconds = seconds))

    @Test
    fun `Woche beginnt am Montag`() {
        // 27.09.2026 is a Sunday.
        assertEquals(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 27).historyWeekStart())
        assertEquals(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 21).historyWeekStart())
    }

    @Test
    fun `Trenner nur vor dem ersten Training einer Woche`() {
        val sunday = item(1, LocalDateTime.of(2026, 9, 27, 18, 0))
        val monday = item(2, LocalDateTime.of(2026, 9, 21, 7, 0))
        val previousSunday = item(3, LocalDateTime.of(2026, 9, 20, 18, 0))

        assertEquals(HistoryListEntry.Week(LocalDate.of(2026, 9, 21)), historyWeekSeparator(null, sunday))
        assertNull(historyWeekSeparator(sunday, monday))
        assertEquals(HistoryListEntry.Week(LocalDate.of(2026, 9, 14)), historyWeekSeparator(monday, previousSunday))
        assertNull(historyWeekSeparator(previousSunday, null))
    }

    @Test
    fun `Wochen zaehlen Minuten je Tag`() {
        val sessions = listOf(
            item(1, LocalDateTime.of(2026, 9, 21, 7, 0), seconds = 3600).session,
            item(2, LocalDateTime.of(2026, 9, 21, 19, 0), seconds = 1800).session,
            item(3, LocalDateTime.of(2026, 9, 26, 10, 0), seconds = 20).session,
            item(4, LocalDateTime.of(2026, 9, 14, 10, 0), seconds = 2700).session
        )

        val weeks = summarizeHistoryWeeks(sessions)

        val current = weeks.getValue(LocalDate.of(2026, 9, 21))
        assertEquals(listOf(90, 0, 0, 0, 0, 1, 0), current.minutesPerDay)
        assertEquals(3, current.workoutCount)
        assertEquals(91, current.totalMinutes)
        assertEquals(listOf(45, 0, 0, 0, 0, 0, 0), weeks.getValue(LocalDate.of(2026, 9, 14)).minutesPerDay)
    }
}
