package com.ironlog.app.presentation.statistics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.core.designsystem.R
import com.ironlog.app.domain.model.AppPreferences
import com.ironlog.app.domain.model.RecordType
import com.ironlog.app.domain.model.SetType
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.domain.util.DateFormatting
import com.ironlog.app.domain.util.WeightFormatting
import com.ironlog.app.presentation.common.IronLogScreenScaffold
import com.ironlog.app.presentation.common.IronLogSurfaceCard
import com.ironlog.app.presentation.common.IronLogSurfaceTone
import com.ironlog.app.presentation.common.LoadingScreen
import com.ironlog.app.presentation.common.StatCard
import com.ironlog.app.presentation.common.StatCardVariant
import com.ironlog.app.presentation.common.WeeklyMuscleVolumeCard
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.app.presentation.theme.semantic
import com.ironlog.feature.statistics.R as StatisticsR
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseStatsScreen(
    onBack: () -> Unit,
    viewModel: ExerciseStatsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val modelProducer = remember { CartesianChartModelProducer() }
    val appPreferencesRepository: AppPreferencesRepository = koinInject()
    val preferences by appPreferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = AppPreferences()
    )
    val dims = ironLogDimens

    LaunchedEffect(state.chartData) {
        if (state.chartData.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                modelProducer.runTransaction {
                    lineSeries {
                        series(state.chartData.map { it.value.toDouble() })
                    }
                }
            }
        }
    }

    IronLogScreenScaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                title = {
                    Text(
                        state.exercise?.name ?: stringResource(id = R.string.stats_title_fallback)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.nav_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            LoadingScreen(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(dims.spacingMd),
                verticalArrangement = Arrangement.spacedBy(dims.spacingMd)
            ) {
                item {
                    state.exercise?.let { exercise ->
                        Text(
                            text = "${exercise.primaryMuscleGroup.displayName} • ${exercise.category.displayName}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    Text(
                        text = stringResource(id = R.string.stats_records_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (state.records.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(id = R.string.stats_empty_records),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = dims.spacingXs)
                        )
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(dims.spacingXs)
                        ) {
                            val maxWeight = state.records.find { it.type == RecordType.MAX_WEIGHT }
                            val maxReps = state.records.find { it.type == RecordType.MAX_REPS }

                            StatCard(
                                label = stringResource(id = R.string.stats_max_weight_label),
                                value = maxWeight?.let {
                                    WeightFormatting.formatWeight(it.value, preferences.unitSystem)
                                } ?: "-",
                                modifier = Modifier.weight(1f),
                                variant = StatCardVariant.PRIMARY
                            )
                            StatCard(
                                label = stringResource(id = R.string.stats_max_reps_label),
                                value = maxReps?.let { "${it.value.toInt()}" } ?: "-",
                                modifier = Modifier.weight(1f),
                                variant = StatCardVariant.TERTIARY
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(dims.spacingXs)
                        ) {
                            val maxE1rm = state.records.find { it.type == RecordType.MAX_E1RM }
                            val maxVolume = state.records.find { it.type == RecordType.MAX_VOLUME }

                            StatCard(
                                label = stringResource(id = R.string.stats_best_1rm_label),
                                value = maxE1rm?.let {
                                    WeightFormatting.formatWeight(it.value, preferences.unitSystem)
                                } ?: "-",
                                modifier = Modifier.weight(1f),
                                variant = StatCardVariant.SECONDARY
                            )
                            StatCard(
                                label = stringResource(id = R.string.stats_max_volume_label),
                                value = maxVolume?.let {
                                    WeightFormatting.formatVolume(it.value, preferences.unitSystem)
                                } ?: "-",
                                modifier = Modifier.weight(1f),
                                variant = StatCardVariant.TERTIARY
                            )
                        }
                    }
                }

                val e1rmProgression = state.e1rmProgression
                if (e1rmProgression != null && state.chartData.size >= 2) {
                    item {
                        Text(
                            text = stringResource(id = R.string.stats_e1rm_progress_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    item {
                        Text(
                            text = stringResource(id = R.string.stats_e1rm_formula_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(dims.spacingXs)
                        ) {
                            StatCard(
                                label = stringResource(id = R.string.stats_e1rm_first_label),
                                value = WeightFormatting.formatWeight(
                                    e1rmProgression.first.toDouble(),
                                    preferences.unitSystem
                                ),
                                modifier = Modifier.weight(1f),
                                variant = StatCardVariant.TERTIARY
                            )
                            StatCard(
                                label = stringResource(id = R.string.stats_e1rm_latest_label),
                                value = WeightFormatting.formatWeight(
                                    e1rmProgression.latest.toDouble(),
                                    preferences.unitSystem
                                ),
                                modifier = Modifier.weight(1f),
                                variant = StatCardVariant.PRIMARY
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(dims.spacingXs)
                        ) {
                            val deltaAbs = WeightFormatting.formatWeightDelta(
                                e1rmProgression.delta.toDouble(),
                                preferences.unitSystem
                            )
                            val deltaSign = if (e1rmProgression.deltaPercent > 0f) "+" else ""
                            val deltaRel = stringResource(
                                id = R.string.stats_e1rm_rel_value,
                                deltaSign + e1rmProgression.deltaPercent.roundToInt()
                            )
                            StatCard(
                                label = stringResource(id = R.string.stats_e1rm_abs_label),
                                value = deltaAbs,
                                modifier = Modifier.weight(1f),
                                variant = StatCardVariant.SECONDARY
                            )
                            StatCard(
                                label = stringResource(id = R.string.stats_e1rm_rel_label),
                                value = deltaRel,
                                modifier = Modifier.weight(1f),
                                variant = StatCardVariant.TERTIARY
                            )
                        }
                    }
                }

                item {
                    WeeklyMuscleVolumeCard(volumes = state.weeklyMuscleVolume)
                }

                item {
                    Text(
                        text = stringResource(id = R.string.stats_progress_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (state.chartData.size >= 2) {
                    item {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(dims.spacingXs)
                        ) {
                            ChartMetric.entries.forEach { metric ->
                                FilterChip(
                                    selected = state.selectedMetric == metric,
                                    onClick = { viewModel.onMetricSelected(metric) },
                                    label = { Text(stringResource(id = metric.labelRes)) }
                                )
                            }
                        }
                    }

                    item {
                        IronLogSurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            tone = IronLogSurfaceTone.ACCENT
                        ) {
                            val lineColor = if (state.selectedMetric == ChartMetric.VOLUME) {
                                MaterialTheme.semantic.teal
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                            CartesianChartHost(
                                chart = rememberCartesianChart(
                                    rememberLineCartesianLayer(
                                        lineProvider = LineCartesianLayer.LineProvider.series(
                                            LineCartesianLayer.rememberLine(
                                                fill = LineCartesianLayer.LineFill.single(fill(lineColor))
                                            )
                                        )
                                    ),
                                    startAxis = VerticalAxis.rememberStart(),
                                    bottomAxis = HorizontalAxis.rememberBottom(),
                                ),
                                modelProducer = modelProducer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                                    .padding(dims.spacingSm)
                            )
                        }
                    }

                    state.lastWorkoutComparison?.let { comparison ->
                        item {
                            LastWorkoutComparisonCard(
                                comparison = comparison,
                                metric = state.selectedMetric,
                                unitSystem = preferences.unitSystem
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            text = stringResource(id = R.string.stats_chart_min_points),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = dims.spacingXs)
                        )
                    }
                }

                if (state.recentSets.isNotEmpty()) {
                    item {
                        RecentSetsCard(
                            sets = state.recentSets,
                            unitSystem = preferences.unitSystem
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(dims.spacingMd)) }
            }
        }
    }
}

@Composable
private fun LastWorkoutComparisonCard(
    comparison: ChartComparison,
    metric: ChartMetric,
    unitSystem: com.ironlog.app.domain.model.UnitSystem
) {
    IronLogSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        tone = IronLogSurfaceTone.MUTED
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ironLogDimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(ironLogDimens.spacingXs)
        ) {
            Text(
                text = stringResource(StatisticsR.string.stats_comparison_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(
                    StatisticsR.string.stats_comparison_previous,
                    chartPointDate(comparison.previous)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatMetricValue(comparison.previous.value, metric, unitSystem),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(
                    StatisticsR.string.stats_comparison_latest,
                    chartPointDate(comparison.latest)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatMetricValue(comparison.latest.value, metric, unitSystem),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(
                    StatisticsR.string.stats_comparison_delta,
                    formatMetricDelta(comparison.delta, metric, unitSystem)
                ),
                style = MaterialTheme.typography.labelLarge,
                color = if (comparison.delta >= 0f) {
                    MaterialTheme.semantic.success
                } else {
                    MaterialTheme.semantic.danger
                }
            )
        }
    }
}

@Composable
private fun RecentSetsCard(
    sets: List<com.ironlog.app.domain.model.WorkoutSet>,
    unitSystem: com.ironlog.app.domain.model.UnitSystem
) {
    val dims = ironLogDimens
    IronLogSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        tone = IronLogSurfaceTone.MUTED
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.spacingMd),
            verticalArrangement = Arrangement.spacedBy(dims.spacingXs)
        ) {
            Text(
                text = stringResource(StatisticsR.string.stats_recent_sets_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            sets.take(12).forEach { set ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = set.completedAt.format(DateFormatting.DATE_TIME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(
                            StatisticsR.string.stats_recent_set_value,
                            recentSetTypeLabel(set.setType),
                            WeightFormatting.formatWeight(set.weightKg, unitSystem),
                            set.reps
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = dims.spacingSm)
                    )
                }
            }
        }
    }
}

@Composable
private fun recentSetTypeLabel(type: SetType): String = stringResource(
    when (type) {
        SetType.NORMAL -> StatisticsR.string.stats_set_type_normal
        SetType.WARMUP -> StatisticsR.string.stats_set_type_warmup
        SetType.DROP_SET -> StatisticsR.string.stats_set_type_drop
        SetType.FAILURE -> StatisticsR.string.stats_set_type_failure
    }
)

private fun chartPointDate(point: ChartDataPoint): String =
    point.timestamp?.format(DateFormatting.DATE_TIME) ?: point.dateLabel

private fun formatMetricValue(
    value: Float,
    metric: ChartMetric,
    unitSystem: com.ironlog.app.domain.model.UnitSystem
): String = when (metric) {
    ChartMetric.VOLUME -> WeightFormatting.formatVolume(value.toDouble(), unitSystem)
    ChartMetric.WEIGHT,
    ChartMetric.E1RM -> WeightFormatting.formatWeight(value.toDouble(), unitSystem)
}

private fun formatMetricDelta(
    value: Float,
    metric: ChartMetric,
    unitSystem: com.ironlog.app.domain.model.UnitSystem
): String {
    if (metric != ChartMetric.VOLUME) {
        return WeightFormatting.formatWeightDelta(value.toDouble(), unitSystem)
    }
    val sign = if (value > 0f) "+" else ""
    return sign + WeightFormatting.formatVolume(value.toDouble(), unitSystem)
}

