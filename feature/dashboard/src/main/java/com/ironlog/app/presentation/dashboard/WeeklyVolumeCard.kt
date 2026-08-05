package com.ironlog.app.presentation.dashboard

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
import com.ironlog.core.designsystem.R
import com.ironlog.app.presentation.theme.glassmorphism
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.app.presentation.theme.ironLogSurfaceRoles
import com.ironlog.app.presentation.theme.semantic
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Displays a line chart of total training volume per week over the last 8 weeks.
 */
@Composable
fun WeeklyVolumeCard(
    weeklyVolume: List<Pair<String, Double>>,
    modifier: Modifier = Modifier
) {
    val dims = ironLogDimens
    val surfaces = ironLogSurfaceRoles
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(weeklyVolume) {
        if (weeklyVolume.size >= 2) {
            withContext(Dispatchers.Default) {
                modelProducer.runTransaction {
                    lineSeries {
                        series(weeklyVolume.map { it.second })
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
                        startAxis = VerticalAxis.rememberStart(),
                        bottomAxis = HorizontalAxis.rememberBottom(),
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
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
