package com.ironlog.app.presentation.statistics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.PersonalRecord
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.repository.ExerciseRepository
import com.ironlog.app.domain.repository.StatisticsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class ChartMetric(val displayName: String) {
    WEIGHT("Gewicht"),
    E1RM("Gesch. 1RM"),
    VOLUME("Volumen")
}

data class ChartDataPoint(
    val dateLabel: String,
    val value: Float
)

data class ExerciseStatsUiState(
    val exercise: Exercise? = null,
    val records: List<PersonalRecord> = emptyList(),
    val selectedMetric: ChartMetric = ChartMetric.WEIGHT,
    val chartData: List<ChartDataPoint> = emptyList(),
    val recentSets: List<WorkoutSet> = emptyList(),
    val isLoading: Boolean = true
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
            val exercise = exerciseRepository.getExerciseById(exerciseId)
            val sets = statisticsRepository.getSetsForExerciseList(exerciseId)
                .filter { !it.isWarmup && it.reps > 0 }

            _uiState.value = _uiState.value.copy(
                exercise = exercise,
                recentSets = sets.take(50),
                isLoading = false
            )

            updateChartData(sets, ChartMetric.WEIGHT)
        }
    }

    private fun observeRecords() {
        viewModelScope.launch {
            statisticsRepository.getRecordsForExercise(exerciseId).collect { records ->
                _uiState.value = _uiState.value.copy(records = records)
            }
        }
    }

    fun onMetricSelected(metric: ChartMetric) {
        viewModelScope.launch {
            val sets = statisticsRepository.getSetsForExerciseList(exerciseId)
                .filter { !it.isWarmup && it.reps > 0 }
            _uiState.value = _uiState.value.copy(selectedMetric = metric)
            updateChartData(sets, metric)
        }
    }

    private fun updateChartData(sets: List<WorkoutSet>, metric: ChartMetric) {
        if (sets.isEmpty()) {
            _uiState.value = _uiState.value.copy(chartData = emptyList())
            return
        }

        // Group by session (date) and compute metric
        val grouped = sets.groupBy { it.sessionId }
        val dataPoints = grouped.map { (_, sessionSets) ->
            val date = sessionSets.first().completedAt
            val dateLabel = "${date.dayOfMonth}.${date.monthValue}"
            val value = when (metric) {
                ChartMetric.WEIGHT -> sessionSets.maxOf { it.weightKg }.toFloat()
                ChartMetric.E1RM -> sessionSets.maxOf { set ->
                    if (set.reps > 1) (set.weightKg * (1 + set.reps / 30.0)).toFloat()
                    else set.weightKg.toFloat()
                }
                ChartMetric.VOLUME -> sessionSets.sumOf { it.weightKg * it.reps }.toFloat()
            }
            ChartDataPoint(dateLabel, value)
        }.sortedBy { it.dateLabel }

        _uiState.value = _uiState.value.copy(chartData = dataPoints)
    }
}
