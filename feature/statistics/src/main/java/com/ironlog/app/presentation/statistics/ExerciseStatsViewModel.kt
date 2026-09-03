package com.ironlog.app.presentation.statistics

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.core.designsystem.R
import com.ironlog.app.domain.error.toAppError
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.PersonalRecord
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.repository.ExerciseRepository
import com.ironlog.app.domain.repository.StatisticsRepository
import com.ironlog.app.domain.util.WorkoutCalculations
import com.ironlog.app.domain.util.catchAndLog
import com.ironlog.app.presentation.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

enum class ChartMetric(@StringRes val labelRes: Int) {
    WEIGHT(R.string.stats_metric_weight),
    E1RM(R.string.stats_metric_e1rm),
    VOLUME(R.string.stats_metric_volume)
}

data class ChartDataPoint(
    val dateLabel: String,
    val value: Float
)

/**
 * Entwicklung des geschätzten 1RM über die Trainingseinheiten: erster, aktuellster
 * und bester Session-Bestwert sowie die Veränderung vom ersten zum aktuellen Wert.
 */
data class E1rmProgression(
    val first: Float,
    val latest: Float,
    val best: Float
) {
    /** Veränderung vom ersten zum aktuellsten Session-Bestwert (absolut, in kg). */
    val delta: Float get() = latest - first

    /** Relative Veränderung vom ersten zum aktuellsten Session-Bestwert (in Prozent). */
    val deltaPercent: Float get() = if (first > 0f) delta / first * 100f else 0f
}

data class ExerciseStatsUiState(
    val exercise: Exercise? = null,
    val records: List<PersonalRecord> = emptyList(),
    val selectedMetric: ChartMetric = ChartMetric.WEIGHT,
    val chartData: List<ChartDataPoint> = emptyList(),
    val e1rmProgression: E1rmProgression? = null,
    val recentSets: List<WorkoutSet> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class ExerciseStatsViewModel(
    savedStateHandle: SavedStateHandle,
    private val exerciseRepository: ExerciseRepository,
    private val statisticsRepository: StatisticsRepository
) : ViewModel() {

    private val exerciseId: Long = savedStateHandle["exerciseId"] ?: -1L

    private val _uiState = MutableStateFlow(ExerciseStatsUiState())
    val uiState: StateFlow<ExerciseStatsUiState> = _uiState

    init {
        loadStats()
        observeRecords()
    }

    private fun loadStats() {
        viewModelScope.launch {
            try {
                val exercise = exerciseRepository.getExerciseById(exerciseId)
                val sets = statisticsRepository.getSetsForExerciseList(exerciseId)
                    .filter { !it.isWarmup && it.reps > 0 }

                _uiState.value = _uiState.value.copy(
                    exercise = exercise,
                    recentSets = sets.take(50),
                    isLoading = false
                )

                updateChartData(sets, ChartMetric.WEIGHT)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toAppError().toUserMessage("Statistiken laden")
                )
            }
        }
    }

    private fun observeRecords() {
        viewModelScope.launch {
            statisticsRepository.getRecordsForExercise(exerciseId)
                .catchAndLog("ExerciseStatsVM")
                .collect { records ->
                    _uiState.value = _uiState.value.copy(records = records)
                }
        }
    }

    fun onMetricSelected(metric: ChartMetric) {
        viewModelScope.launch {
            try {
                val sets = statisticsRepository.getSetsForExerciseList(exerciseId)
                    .filter { !it.isWarmup && it.reps > 0 }
                _uiState.value = _uiState.value.copy(selectedMetric = metric)
                updateChartData(sets, metric)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.toAppError().toUserMessage("Metrikwechsel")
                )
            }
        }
    }

    private fun updateChartData(sets: List<WorkoutSet>, metric: ChartMetric) {
        val sessions = aggregateBySession(sets)
        if (sessions.isEmpty()) {
            _uiState.value = _uiState.value.copy(chartData = emptyList(), e1rmProgression = null)
            return
        }

        val ordered = sessions.sortedBy { it.latestTime }
        val e1rmSeries = ordered.map { it.maxE1Rm }

        _uiState.value = _uiState.value.copy(
            chartData = ordered.map { agg ->
                val date = agg.latestTime
                ChartDataPoint(
                    dateLabel = "${date.dayOfMonth}.${date.monthValue}",
                    value = when (metric) {
                        ChartMetric.WEIGHT -> agg.maxWeight.toFloat()
                        ChartMetric.E1RM   -> agg.maxE1Rm.toFloat()
                        ChartMetric.VOLUME -> agg.totalVolume.toFloat()
                    }
                )
            },
            e1rmProgression = E1rmProgression(
                first = e1rmSeries.first().toFloat(),
                latest = e1rmSeries.last().toFloat(),
                best = e1rmSeries.max().toFloat()
            )
        )
    }

    private data class SessionAggregate(
        val latestTime: LocalDateTime,
        val maxWeight: Double,
        val maxE1Rm: Double,
        val totalVolume: Double
    )

    private fun aggregateBySession(sets: List<WorkoutSet>): List<SessionAggregate> {
        val bySession: Map<Long, SessionAggregate> = sets.fold(mutableMapOf()) { acc, set ->
            val e1rm = WorkoutCalculations.calculateE1RM(set.weightKg, set.reps)
            val prev = acc[set.sessionId]
            acc[set.sessionId] = if (prev == null) {
                SessionAggregate(set.completedAt, set.weightKg, e1rm, set.weightKg * set.reps)
            } else {
                SessionAggregate(
                    latestTime = if (set.completedAt > prev.latestTime) set.completedAt else prev.latestTime,
                    maxWeight = maxOf(prev.maxWeight, set.weightKg),
                    maxE1Rm = maxOf(prev.maxE1Rm, e1rm),
                    totalVolume = prev.totalVolume + set.weightKg * set.reps
                )
            }
            acc
        }
        return bySession.values.toList()
    }
}
