package com.ironlog.app.presentation.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.domain.model.AppearanceStyle
import com.ironlog.app.domain.model.RecordType
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.util.WeightFormatting
import com.ironlog.app.presentation.theme.AthleticLabel
import com.ironlog.app.presentation.theme.GlassLevel
import com.ironlog.app.presentation.theme.LocalAppearanceStyle
import com.ironlog.app.presentation.theme.liquidGlass
import com.ironlog.core.designsystem.R
import com.ironlog.feature.statistics.R as StatisticsR
import java.util.Locale
import kotlin.math.roundToInt

private val StatsInk = Color(0xFF0B0D12)

@Composable
internal fun isLiquidGlass(): Boolean = LocalAppearanceStyle.current == AppearanceStyle.LIQUID_GLASS

/** Round glass back button and the exercise's muscle group line. */
@Composable
internal fun GlassStatsHeader(subtitle: String?, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val backLabel = stringResource(R.string.nav_back)
        Box(
            modifier = Modifier
                .size(44.dp)
                .liquidGlass(GlassLevel.STANDARD, CircleShape)
                .clickable(role = Role.Button, onClickLabel = backLabel, onClick = onBack)
                .semantics { contentDescription = backLabel },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        if (subtitle != null) {
            Text(
                subtitle.uppercase(Locale.GERMAN),
                style = AthleticLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Hero card: latest value of the selected metric, change since the first point as a tinted
 * label, the curve with area and end point, and the metric switch.
 */
@Composable
internal fun GlassStatsHero(
    points: List<ChartDataPoint>,
    metric: ChartMetric,
    unitSystem: UnitSystem,
    onMetricSelected: (ChartMetric) -> Unit
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val ink = if (dark) Color.White else StatsInk
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(GlassLevel.STRONG, RoundedCornerShape(34.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val first = points.firstOrNull()
        val latest = points.lastOrNull()
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(metric.labelRes).uppercase(Locale.GERMAN),
                    style = AthleticLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val (number, unit) = splitValue(latest?.value, metric, unitSystem)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        number,
                        fontSize = 52.sp,
                        lineHeight = 54.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-2).sp,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                    Text(" $unit", fontSize = 20.sp, color = ink.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            if (first != null && latest != null && points.size >= 2) {
                val delta = latest.value - first.value
                val percent = if (first.value > 0f) (delta / first.value * 100f).roundToInt() else null
                val deltaText = listOfNotNull(
                    formatDelta(delta, metric, unitSystem),
                    percent?.let { (if (it > 0) "+" else "") + "$it %" }
                ).joinToString(" · ")
                Text(
                    deltaText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .liquidGlass(GlassLevel.TINT, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        if (points.size >= 2) {
            val description = stringResource(
                StatisticsR.string.stats_glass_chart_cd,
                stringResource(metric.labelRes),
                formatValue(first!!.value, metric, unitSystem),
                formatValue(latest!!.value, metric, unitSystem)
            )
            GlassLineChart(
                values = points.map { it.value },
                ink = ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .semantics { contentDescription = description }
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "${first.dateLabel} · ${formatValue(first.value, metric, unitSystem)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${latest.dateLabel} · ${formatValue(latest.value, metric, unitSystem)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            }
        } else {
            Text(
                stringResource(R.string.stats_chart_min_points),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(23.dp))
                .background(Color.Black.copy(alpha = if (dark) 0.18f else 0.05f))
                .border(1.dp, ink.copy(alpha = 0.08f), RoundedCornerShape(23.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ChartMetric.entries.forEach { option ->
                val selected = option == metric
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .then(if (selected) Modifier.shadow(6.dp, RoundedCornerShape(20.dp)) else Modifier)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) (if (dark) Color.White else StatsInk) else Color.Transparent)
                        .clickable(role = Role.Tab) { onMetricSelected(option) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(option.shortLabelRes()),
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                        color = if (selected) (if (dark) StatsInk else Color.White) else ink
                    )
                }
            }
        }
    }
}

private fun ChartMetric.shortLabelRes(): Int = when (this) {
    ChartMetric.WEIGHT -> StatisticsR.string.stats_glass_metric_weight
    ChartMetric.E1RM -> StatisticsR.string.stats_glass_metric_e1rm
    ChartMetric.VOLUME -> StatisticsR.string.stats_glass_metric_volume
}

/** Line with a soft area below and a glowing end point; scaled to the value range. */
@Composable
private fun GlassLineChart(values: List<Float>, ink: Color, modifier: Modifier) {
    Canvas(modifier) {
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val top = 14.dp.toPx()
        val bottom = size.height - 10.dp.toPx()
        val inset = 6.dp.toPx()
        val stepX = (size.width - 2 * inset) / (values.size - 1)
        val points = values.mapIndexed { index, value ->
            Offset(inset + index * stepX, bottom - (value - min) / range * (bottom - top))
        }
        listOf(1f / 3f, 2f / 3f).forEach { fraction ->
            val y = size.height * fraction
            drawLine(ink.copy(alpha = 0.10f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        drawPath(area, ink.copy(alpha = 0.10f))
        drawPath(line, ink, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(ink.copy(alpha = 0.25f), radius = 10.dp.toPx(), center = points.last())
        drawCircle(ink, radius = 5.dp.toPx(), center = points.last())
    }
}

/** Four equal record tiles in a 2 × 2 grid. */
@Composable
internal fun GlassRecordTiles(records: Map<RecordType, Double>, unitSystem: UnitSystem) {
    val tiles = listOf(
        stringResource(R.string.stats_best_1rm_label) to records[RecordType.MAX_E1RM]?.let { WeightFormatting.formatWeight(it, unitSystem) },
        stringResource(R.string.stats_max_weight_label) to records[RecordType.MAX_WEIGHT]?.let { WeightFormatting.formatWeight(it, unitSystem) },
        stringResource(R.string.stats_max_reps_label) to records[RecordType.MAX_REPS]?.toInt()?.toString(),
        stringResource(R.string.stats_max_volume_label) to records[RecordType.MAX_VOLUME]?.let { WeightFormatting.formatVolume(it, unitSystem) }
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .liquidGlass(GlassLevel.STANDARD, RoundedCornerShape(24.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .semantics(mergeDescendants = true) {},
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(label.uppercase(Locale.GERMAN), style = AthleticLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Text(
                            value ?: "–",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private fun formatValue(value: Float, metric: ChartMetric, unitSystem: UnitSystem): String = when (metric) {
    ChartMetric.VOLUME -> WeightFormatting.formatVolume(value.toDouble(), unitSystem)
    ChartMetric.WEIGHT, ChartMetric.E1RM -> WeightFormatting.formatWeight(value.toDouble(), unitSystem)
}

private fun splitValue(value: Float?, metric: ChartMetric, unitSystem: UnitSystem): Pair<String, String> {
    if (value == null) return "–" to WeightFormatting.unitLabel(unitSystem)
    val text = formatValue(value, metric, unitSystem)
    return text.substringBeforeLast(' ') to text.substringAfterLast(' ')
}

private fun formatDelta(value: Float, metric: ChartMetric, unitSystem: UnitSystem): String {
    if (metric != ChartMetric.VOLUME) return WeightFormatting.formatWeightDelta(value.toDouble(), unitSystem)
    return (if (value > 0f) "+" else "") + WeightFormatting.formatVolume(value.toDouble(), unitSystem)
}
