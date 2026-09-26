package com.ironlog.app.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.ironlog.app.domain.error.toAppError
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.domain.repository.WorkoutRepository
import com.ironlog.app.domain.repository.TrainingPlanRepository
import com.ironlog.app.presentation.common.toUserMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** The bounded periods offered by the history filter. */
enum class HistoryTimeRange {
    ALL_TIME,
    LAST_30_DAYS,
    LAST_90_DAYS,
    THIS_YEAR
}

data class HistoryFilter(
    val planId: Long? = null,
    val timeRange: HistoryTimeRange = HistoryTimeRange.ALL_TIME,
    /** Free text over training name, note, plan and exercise names. */
    val query: String = ""
) {
    val isActive: Boolean
        get() = planId != null || timeRange != HistoryTimeRange.ALL_TIME || query.isNotBlank()

    fun fromEpochMillis(now: LocalDateTime = LocalDateTime.now()): Long? =
        timeRange.fromEpochMillis(now)
}

/**
 * Converts a UI time range to the lower bound used by the Room query. Keeping this conversion
 * outside the composable makes the filter behavior deterministic and directly testable.
 */
fun HistoryTimeRange.fromEpochMillis(now: LocalDateTime = LocalDateTime.now()): Long? {
    val date = when (this) {
        HistoryTimeRange.ALL_TIME -> return null
        HistoryTimeRange.LAST_30_DAYS -> now.toLocalDate().minusDays(30)
        HistoryTimeRange.LAST_90_DAYS -> now.toLocalDate().minusDays(90)
        HistoryTimeRange.THIS_YEAR -> LocalDate.of(now.year, 1, 1)
    }
    return date.atStartOfDay()
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

data class WorkoutHistoryItem(
    val session: WorkoutSession,
    val exerciseCount: Int = 0,
    val setCount: Int = 0,
    val totalVolume: Double = 0.0
)

data class WorkoutHistoryUiState(
    val filter: HistoryFilter = HistoryFilter(),
    val plans: List<TrainingPlan> = emptyList(),
    val error: String? = null
)

class WorkoutHistoryViewModel(
    private val workoutRepository: WorkoutRepository,
    private val trainingPlanRepository: TrainingPlanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutHistoryUiState())
    val uiState: StateFlow<WorkoutHistoryUiState> = _uiState.asStateFlow()

    private val filter = MutableStateFlow(HistoryFilter())

    /**
     * Recreates the PagingSource whenever a filter changes. The repository applies the predicates
     * before paging, so a filtered result can never miss a matching session that sits on a later
     * page of the unfiltered history.
     */
    val pagedWorkouts: Flow<PagingData<WorkoutHistoryItem>> = filter
        // Typing should not rebuild the PagingSource for every keystroke.
        .debounce { if (it.query.isBlank()) 0L else SEARCH_DEBOUNCE_MILLIS }
        .map { it.copy(query = it.query.trim()) }
        .distinctUntilChanged()
        .flatMapLatest { selected ->
            workoutRepository.getPagedCompletedWorkoutSummaries(
                planId = selected.planId,
                fromEpochMillis = selected.fromEpochMillis(),
                toEpochMillis = null,
                searchQuery = selected.query.ifBlank { null }
            )
        }
        .map { pagingData ->
            pagingData.map { summary ->
                WorkoutHistoryItem(
                    session = summary.session,
                    exerciseCount = summary.exerciseCount,
                    setCount = summary.setCount,
                    totalVolume = summary.totalVolume
                )
            }
        }
        .cachedIn(viewModelScope)

    init {
        observePlans()
    }

    private fun observePlans() {
        viewModelScope.launch {
            trainingPlanRepository.getAllPlans()
                .catch { error ->
                    _uiState.update {
                        it.copy(error = error.toAppError().toUserMessage("Pläne laden"))
                    }
                }
                .collect { plans ->
                    _uiState.update { it.copy(plans = plans, error = null) }
                }
        }
    }

    fun setPlanFilter(planId: Long?) {
        filter.update { it.copy(planId = planId) }
        _uiState.update { it.copy(filter = filter.value) }
    }

    fun setTimeRange(timeRange: HistoryTimeRange) {
        filter.update { it.copy(timeRange = timeRange) }
        _uiState.update { it.copy(filter = filter.value) }
    }

    fun setSearchQuery(query: String) {
        filter.update { it.copy(query = query) }
        _uiState.update { it.copy(filter = filter.value) }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            try {
                workoutRepository.deleteSession(sessionId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toAppError().toUserMessage("Training löschen"))
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}
