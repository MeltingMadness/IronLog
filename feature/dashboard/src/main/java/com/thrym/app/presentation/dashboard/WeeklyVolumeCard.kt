package com.thrym.app.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thrym.core.designsystem.R
import com.thrym.app.presentation.theme.glassmorphism
import com.thrym.app.presentation.theme.thrymDimens
import com.thrym.app.presentation.theme.thrymSurfaceRoles
import com.thrym.app.presentation.theme.semantic
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.thrym.app.domain.model.UnitSystem
import com.thrym.app.domain.util.DateFormatting
import com.thrym.app.domain.util.WeightFormatting
import com.thrym.app.presentation.common.currentLocale
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.thrym.feature.dashboard.R as DashboardR
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Displays a line chart of total training volume per week over the last 8 weeks.
 * [weeklyVolume] holds the week start and the volume in kg; the chart, axis and labels
 * show it in [unitSystem].
 */
@Composable
fun WeeklyVolumeCard(
    weeklyVolume: List<Pair<LocalDate, Double>>,
    unitSystem: UnitSystem,
    modifier: Modifier = Modifier
) {
    val dims = thrymDimens
    val surfaces = thrymSurfaceRoles
    val modelProducer = remember { CartesianChartModelProducer() }
    val locale = currentLocale()
    val weekLabels = remember(weeklyVolume, locale) {
        val format = DateFormatting.dayMonth(locale)
        weeklyVolume.map { format.format(it.first) }
    }

    LaunchedEffect(weeklyVolume, unitSystem) {
        if (weeklyVolume.size >= 2) {
            withContext(Dispatchers.Default) {
                modelProducer.runTransaction {
                    lineSeries {
                        series(weeklyVolume.map { WeightFormatting.convertToDisplay(it.second, unitSystem) })
                    }
                }
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .glassmorphism(backgroundColor = surfaces.elevated.copy(alpha = 0.72f)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier.padding(dims.spacingMd),
            verticalArrangement = Arrangement.spacedBy(dims.spacingSm)
        ) {
            Text(
                text = stringResource(id = R.string.dashboard_volume_trend_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = stringResource(DashboardR.string.dashboard_ui_volume_unit, WeightFormatting.unitLabel(unitSystem)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (weeklyVolume.size >= 2) {
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(
                            lineProvider = LineCartesianLayer.LineProvider.series(
                                LineCartesianLayer.rememberLine(
                                    fill = LineCartesianLayer.LineFill.single(fill(MaterialTheme.semantic.teal))
                                )
                            )
                        ),
                        startAxis = VerticalAxis.rememberStart(
                            valueFormatter = CartesianValueFormatter { _, value, _ ->
                                WeightFormatting.formatNumber(value, 0)
                            }
                        ),
                        bottomAxis = HorizontalAxis.rememberBottom(
                            valueFormatter = CartesianValueFormatter { _, value, _ ->
                                weekLabels.getOrNull(value.toInt()).orEmpty()
                            }
                        ),
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
                // The last point is the running week, so its drop is not a real decline.
                Text(
                    text = stringResource(
                        id = R.string.dashboard_volume_trend_current_week,
                        WeightFormatting.formatVolume(weeklyVolume.last().second, unitSystem)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = stringResource(id = R.string.dashboard_volume_trend_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
