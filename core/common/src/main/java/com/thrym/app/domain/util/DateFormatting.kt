package com.thrym.app.domain.util

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Central date and time formatters. They follow the given locale (default: the current app
 * locale), so date order, month names and the clock style match the UI language instead of
 * hard-coded German patterns.
 *
 * [use24Hour] lets Android callers apply the system 12/24-hour setting
 * (`android.text.format.DateFormat.is24HourFormat`); `null` keeps the locale's default.
 * Formatters are cached per locale, so a language change takes effect without a restart.
 */
object DateFormatting {
    private data class Key(val kind: String, val locale: Locale, val use24Hour: Boolean?)

    private val cache = ConcurrentHashMap<Key, DateTimeFormatter>()

    /** Weekday and medium date: "Monday, Feb 10, 2026" (en-US), "Montag, 10.02.2026" (de). */
    fun dateFull(locale: Locale = Locale.getDefault()): DateTimeFormatter =
        cached("dateFull", locale, null) {
            DateTimeFormatterBuilder()
                .appendText(ChronoField.DAY_OF_WEEK, TextStyle.FULL)
                .appendLiteral(", ")
                .appendLocalized(FormatStyle.MEDIUM, null)
                .toFormatter(locale)
        }

    /** Medium date and short time: "Feb 10, 2026, 2:30 PM" (en-US), "10.02.2026, 14:30" (de). */
    fun dateTime(locale: Locale = Locale.getDefault(), use24Hour: Boolean? = null): DateTimeFormatter =
        cached("dateTime", locale, use24Hour) {
            ofPatternOrNull(withHourCycle(localizedPattern(FormatStyle.MEDIUM, FormatStyle.SHORT, locale), use24Hour), locale)
                ?: DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
        }

    /** Clock time: "2:30 PM" (en-US), "14:30" (de). */
    fun timeShort(locale: Locale = Locale.getDefault(), use24Hour: Boolean? = null): DateTimeFormatter =
        cached("timeShort", locale, use24Hour) {
            ofPatternOrNull(withHourCycle(localizedPattern(null, FormatStyle.SHORT, locale), use24Hour), locale)
                ?: DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        }

    /**
     * Day and month without the year, taken from the locale's short date:
     * "2/10" (en-US), "10/02" (en-GB), "10.02." (de). Also used for automatic workout names.
     */
    fun dayMonth(locale: Locale = Locale.getDefault()): DateTimeFormatter =
        cached("dayMonth", locale, null) {
            ofPatternOrNull(withoutYear(localizedPattern(FormatStyle.SHORT, null, locale)), locale)
                ?: DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
        }

    /** Weekday with day and month: "Monday, 2/10" (en-US), "Montag, 10.02." (de). */
    fun weekdayDayMonth(locale: Locale = Locale.getDefault()): DateTimeFormatter =
        cached("weekdayDayMonth", locale, null) {
            DateTimeFormatterBuilder()
                .appendText(ChronoField.DAY_OF_WEEK, TextStyle.FULL)
                .appendLiteral(", ")
                .append(dayMonth(locale))
                .toFormatter(locale)
        }

    fun formatClock(hour: Int, minute: Int, locale: Locale = Locale.getDefault(), use24Hour: Boolean? = null): String =
        LocalTime.of(hour, minute).format(timeShort(locale, use24Hour))

    fun dayShort(dayOfWeek: DayOfWeek, locale: Locale = Locale.getDefault()): String =
        dayOfWeek.getDisplayName(TextStyle.SHORT, locale)

    private fun cached(kind: String, locale: Locale, use24Hour: Boolean?, create: () -> DateTimeFormatter): DateTimeFormatter =
        cache.getOrPut(Key(kind, locale, use24Hour), create)

    private fun localizedPattern(date: FormatStyle?, time: FormatStyle?, locale: Locale): String =
        DateTimeFormatterBuilder.getLocalizedDateTimePattern(date, time, IsoChronology.INSTANCE, locale)

    private fun ofPatternOrNull(pattern: String, locale: Locale): DateTimeFormatter? =
        runCatching { DateTimeFormatter.ofPattern(pattern, locale) }.getOrNull()

    /**
     * Removes the year field and the separator that joined it: "M/d/yy" -> "M/d",
     * "y-MM-dd" -> "MM-dd". A dot before a trailing year stays, so German keeps "dd.MM.".
     */
    internal fun withoutYear(pattern: String): String {
        val match = YEAR_FIELD.find(pattern) ?: return pattern
        val atStart = match.range.first == 0
        val atEnd = match.range.last == pattern.lastIndex
        val before = match.groupValues[1]
        val replacement = when {
            atEnd && !atStart -> if (before.trim() == ".") "." else ""
            atStart -> ""
            else -> before
        }
        return pattern.replaceRange(match.range, replacement).trim()
    }

    /**
     * Switches a localized pattern to the requested hour cycle, skipping quoted literals:
     * "h:mm a" -> "HH:mm" for 24 hours, "HH:mm" -> "h:mm a" for 12 hours.
     */
    internal fun withHourCycle(pattern: String, use24Hour: Boolean?): String {
        if (use24Hour == null) return pattern
        val unquoted = pattern.unquotedChars()
        val has12HourField = unquoted.any { it == 'h' || it == 'K' }
        val has24HourField = unquoted.any { it == 'H' || it == 'k' }
        return when {
            use24Hour && has12HourField -> pattern.mapUnquoted { segment ->
                segment.replace(TWELVE_HOUR_FIELD, "HH").replace(DAY_PERIOD_FIELD, "")
            }.trim()
            !use24Hour && has24HourField -> pattern.mapUnquoted { segment ->
                segment.replace(TWENTY_FOUR_HOUR_FIELD, "h").replace(MINUTE_FIELD, "$1 a")
            }
            else -> pattern
        }
    }

    private val TWELVE_HOUR_FIELD = Regex("[hK]+")
    private val TWENTY_FOUR_HOUR_FIELD = Regex("[Hk]+")
    private val DAY_PERIOD_FIELD = Regex("[\\s\\u00A0\\u202F]*a+")
    private val MINUTE_FIELD = Regex("(m+)(?![\\s\\u00A0\\u202F]*a)")

    private val YEAR_FIELD = Regex("""([^A-Za-z']*)y+([^A-Za-z']*)""")

    private fun String.unquotedChars(): String {
        val builder = StringBuilder()
        mapUnquoted { segment -> segment.also(builder::append) }
        return builder.toString()
    }

    /** Applies [transform] to the parts of a pattern outside single-quoted literals. */
    private fun String.mapUnquoted(transform: (String) -> String): String {
        val result = StringBuilder()
        var index = 0
        while (index < length) {
            val quote = indexOf('\'', index)
            if (quote < 0) {
                result.append(transform(substring(index)))
                break
            }
            result.append(transform(substring(index, quote)))
            val closing = indexOf('\'', quote + 1).let { if (it < 0) length - 1 else it }
            result.append(substring(quote, closing + 1))
            index = closing + 1
        }
        return result.toString()
    }
}
