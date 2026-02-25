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
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.domain.util.WeightFormatting
import com.ironlog.app.presentation.common.IronLogScreenScaffold
import com.ironlog.app.presentation.common.LoadingScreen
import com.ironlog.app.presentation.common.StatCard
import com.ironlog.app.presentation.theme.ironLogDimens
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

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
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = stringResource(id = R.string.stats_max_reps_label),
                                value = maxReps?.let { "${it.value.toInt()}" } ?: "-",
                                modifier = Modifier.weight(1f)
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
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = stringResource(id = R.string.stats_max_volume_label),
                                value = maxVolume?.let {
                                    WeightFormatting.formatVolume(it.value, preferences.unitSystem)
                                } ?: "-",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
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
                        CartesianChartHost(
                            chart = rememberCartesianChart(
                                rememberLineCartesianLayer(),
                                startAxis = VerticalAxis.rememberStart(),
                                bottomAxis = HorizontalAxis.rememberBottom(),
                            ),
                            modelProducer = modelProducer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                        )
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

                item { Spacer(modifier = Modifier.height(dims.spacingMd)) }
            }
        }
    }
}
