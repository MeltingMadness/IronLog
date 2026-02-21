package com.ironlog.app.domain.util

import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Zentrale Datum- und Zeit-Formatter mit deutscher Locale.
 * Vermeidet hardcoded Patterns in Screen-Dateien.
 */
object DateFormatting {
    private val DE = Locale.GERMAN

    /** z.B. "Montag, 10.02.2026" */
    val DATE_FULL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", DE)

    /** z.B. "10.02.2026, 14:30" */
    val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", DE)

    /** z.B. "14:30" */
    val TIME_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", DE)

    /** z.B. "10.02." fuer automatische Trainingsnamen */
    val DATE_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.", DE)

    fun formatClock(hour: Int, minute: Int, locale: Locale = DE): String {
        return String.format(locale, "%02d:%02d", hour, minute)
    }

    fun dayShort(dayOfWeek: DayOfWeek, locale: Locale = DE): String {
        return dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    }
}
