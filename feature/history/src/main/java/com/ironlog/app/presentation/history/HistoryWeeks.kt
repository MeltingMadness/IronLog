package com.ironlog.app.presentation.history

import com.ironlog.app.domain.model.WorkoutSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** One row of the history list in Liquid Glass: a week header or a training. */
sealed interface HistoryListEntry {
    data class Week(val weekStart: LocalDate) : HistoryListEntry
    data class Workout(val item: WorkoutHistoryItem) : HistoryListEntry
}

/** Trainings of one calendar week (Monday to Sunday) for the mini bars of the week header. */
data class HistoryWeekSummary(
    val weekStart: LocalDate,
    /** Training minutes per day, Monday first; always seven entries. */
    val minutesPerDay: List<Int>,
    val workoutCount: Int
) {
    val totalMinutes: Int get() = minutesPerDay.sum()
}

fun LocalDate.historyWeekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/**
 * Header that goes in front of [after] when it starts a new week. The list is sorted newest
 * first, so a header appears above the first (latest) training of each week.
 */
fun historyWeekSeparator(before: WorkoutHistoryItem?, after: WorkoutHistoryItem?): HistoryListEntry.Week? {
    val afterWeek = after?.session?.startTime?.toLocalDate()?.historyWeekStart() ?: return null
    val beforeWeek = before?.session?.startTime?.toLocalDate()?.historyWeekStart()
    return if (afterWeek != beforeWeek) HistoryListEntry.Week(afterWeek) else null
}

/**
 * Groups completed sessions by week. Every training counts at least one minute, so a short
 * training still shows up as a bar.
 */
fun summarizeHistoryWeeks(sessions: List<WorkoutSession>): Map<LocalDate, HistoryWeekSummary> =
    sessions
        .groupBy { it.startTime.toLocalDate().historyWeekStart() }
        .mapValues { (weekStart, weekSessions) ->
            val minutes = IntArray(7)
            weekSessions.forEach { session ->
                val day = session.startTime.dayOfWeek.value - 1
                minutes[day] += maxOf(1, (session.durationSeconds / 60).toInt())
            }
            HistoryWeekSummary(
                weekStart = weekStart,
                minutesPerDay = minutes.toList(),
                workoutCount = weekSessions.size
            )
        }
