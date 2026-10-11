package com.thrym.app.presentation.common

import android.icu.text.DateIntervalFormat
import android.icu.util.DateInterval
import android.icu.util.TimeZone
import android.icu.util.ULocale
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.thrym.app.domain.util.DateFormatting
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Locale of the current UI configuration. It follows the per-app language, and reading it
 * recomposes the caller when the language changes. Use it for dates, weekday names and
 * [String.uppercase] instead of a fixed locale.
 */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

/** The system 12/24-hour clock setting; read again when the configuration changes. */
@Composable
fun rememberIs24HourFormat(): Boolean {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { DateFormat.is24HourFormat(context) }
}

/**
 * Date and clock time in the UI locale and the system 12/24-hour setting:
 * "Feb 10, 2026, 2:30 PM" (en-US), "10.02.2026, 14:30" (de).
 */
@Composable
fun rememberDateTimeFormatter(): DateTimeFormatter {
    val locale = currentLocale()
    val use24Hour = rememberIs24HourFormat()
    return remember(locale, use24Hour) { DateFormatting.dateTime(locale, use24Hour) }
}

/**
 * Formatter for an ICU skeleton such as "MMMMd" in [locale]: "February 10" (en),
 * "10. Februar" (de). [prefix] is a pattern fragment placed in front, e.g. "EEEE · ".
 */
fun skeletonDateFormatter(locale: Locale, skeleton: String, prefix: String = ""): DateTimeFormatter =
    DateTimeFormatter.ofPattern(prefix + DateFormat.getBestDateTimePattern(locale, skeleton), locale)

/**
 * Compact day range without a year: "Feb 10 – 16" / "Feb 24 – Mar 2" (en),
 * "10.–16. Feb." / "24. Feb. – 2. März" (de).
 */
fun formatDayMonthRange(start: LocalDate, end: LocalDate, locale: Locale): String {
    val format = DateIntervalFormat.getInstance("MMMd", ULocale.forLocale(locale))
    format.timeZone = TimeZone.getTimeZone("UTC")
    val startMillis = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val endMillis = end.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    return format.format(DateInterval(startMillis, endMillis))
}
