package com.ironlog.app.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.domain.model.AppearanceStyle
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.util.WeightFormatting
import com.ironlog.app.presentation.theme.AthleticLabel
import com.ironlog.app.presentation.theme.GlassLevel
import com.ironlog.app.presentation.theme.LocalAppearanceStyle
import com.ironlog.app.presentation.theme.liquidGlass
import com.ironlog.core.designsystem.R
import com.ironlog.feature.history.R as HistoryR
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private val GERMAN = Locale.GERMAN
private val DayMonthFormat = DateTimeFormatter.ofPattern("d. MMM", GERMAN)
private val DayFormat = DateTimeFormatter.ofPattern("d.", GERMAN)

@Composable
internal fun isLiquidGlass(): Boolean = LocalAppearanceStyle.current == AppearanceStyle.LIQUID_GLASS

/** Large title instead of the top bar in Liquid Glass. */
@Composable
internal fun GlassHistoryHeader() {
    Text(
        text = stringResource(R.string.history_title),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 4.dp)
            .semantics { heading() }
    )
}

/** Search as a glass pill; same behavior as the Ember text field. */
@Composable
internal fun GlassHistorySearchField(query: String, onQueryChange: (String) -> Unit) {
    val placeholder = stringResource(HistoryR.string.history_search_placeholder)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .heightIn(min = 52.dp)
            .liquidGlass(GlassLevel.STANDARD, RoundedCornerShape(26.dp))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = placeholder }
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(HistoryR.string.history_search_clear))
            }
        } else {
            Box(Modifier.size(48.dp))
        }
    }
}

/**
 * Week header: name of the week, number of trainings, date range and total time, and mini
 * bars Monday to Sunday (height = training minutes of the day). The bars count every
 * training of the week, also when a filter hides some of them in the list below.
 */
@Composable
internal fun GlassWeekHeader(
    weekStart: LocalDate,
    summary: HistoryWeekSummary?,
    today: LocalDate,
    modifier: Modifier = Modifier
) {
    val currentWeek = today.historyWeekStart()
    val weekName = when (weekStart) {
        currentWeek -> stringResource(HistoryR.string.history_glass_week_current)
        currentWeek.minusWeeks(1) -> stringResource(HistoryR.string.history_glass_week_last)
        else -> stringResource(HistoryR.string.history_glass_week_number, weekStart.get(WeekFields.ISO.weekOfWeekBasedYear()))
    }
    val weekEnd = weekStart.plusDays(6)
    val range = if (weekStart.month == weekEnd.month) {
        "${weekStart.format(DayFormat)}–${weekEnd.format(DayMonthFormat)}"
    } else {
        "${weekStart.format(DayMonthFormat)} – ${weekEnd.format(DayMonthFormat)}"
    }
    val count = summary?.workoutCount ?: 0
    val minutes = summary?.totalMinutes ?: 0

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .liquidGlass(GlassLevel.STANDARD, RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(weekName.uppercase(GERMAN), style = AthleticLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                pluralStringResource(HistoryR.plurals.history_glass_week_workouts, count, count),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                listOf(range, formatMinutes(minutes)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        WeekMiniBars(summary?.minutesPerDay ?: List(7) { 0 }, today = today, weekStart = weekStart)
    }
}

@Composable
private fun WeekMiniBars(minutesPerDay: List<Int>, today: LocalDate, weekStart: LocalDate) {
    val max = (minutesPerDay.maxOrNull() ?: 0).coerceAtLeast(1)
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        DayOfWeek.entries.forEachIndexed { index, day ->
            val value = minutesPerDay.getOrElse(index) { 0 }
            val isToday = weekStart.plusDays(index.toLong()) == today
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.height(40.dp).width(8.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().height(40.dp).background(track, RoundedCornerShape(4.dp)))
                    if (value > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((40f * value / max).coerceAtLeast(6f).dp)
                                .background(primary, RoundedCornerShape(4.dp))
                        )
                    }
                }
                Text(
                    day.getDisplayName(TextStyle.NARROW, GERMAN),
                    fontSize = 10.sp,
                    fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun formatMinutes(minutes: Int): String =
    if (minutes >= 60) {
        stringResource(HistoryR.string.history_glass_hours_minutes, minutes / 60, minutes % 60)
    } else {
        stringResource(HistoryR.string.history_glass_minutes, minutes)
    }

/** One training as a glass row: date lens, name and key figures. */
@Composable
internal fun GlassHistoryRow(
    item: WorkoutHistoryItem,
    unitSystem: UnitSystem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val start = item.session.startTime
    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(GlassLevel.STANDARD, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(
            modifier = Modifier
                .size(50.dp)
                .liquidGlass(GlassLevel.STANDARD, CircleShape),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(start.dayOfMonth.toString(), fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                start.dayOfWeek.getDisplayName(TextStyle.SHORT, GERMAN).removeSuffix(".").uppercase(GERMAN),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.session.name.ifBlank { stringResource(R.string.history_title) },
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val figures = buildList {
                add(
                    if (item.session.durationSeconds < 60) stringResource(HistoryR.string.history_glass_under_minute)
                    else formatMinutes((item.session.durationSeconds / 60).toInt())
                )
                add(pluralStringResource(HistoryR.plurals.history_glass_exercises, item.exerciseCount, item.exerciseCount))
                if (item.totalVolume > 0) add(WeightFormatting.formatVolume(item.totalVolume, unitSystem))
            }
            Text(
                figures.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
