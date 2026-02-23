package com.ironlog.app.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.ironlog.app.domain.error.toAppError
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.domain.repository.WorkoutRepository
import com.ironlog.app.presentation.common.toUserMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class WorkoutHistoryItem(
    val session: WorkoutSession,
    val exerciseCount: Int = 0,
    val setCount: Int = 0,
    val totalVolume: Double = 0.0
)

data class WorkoutHistoryUiState(
    val error: String? = null
)

class WorkoutHistoryViewModel(
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutHistoryUiState())
    val uiState: StateFlow<WorkoutHistoryUiState> = _uiState.asStateFlow()

    val pagedWorkouts: Flow<PagingData<WorkoutHistoryItem>> = workoutRepository.getPagedCompletedWorkoutSummaries()
        .map { pagingData ->
            pagingData.map { summary ->
                WorkoutHistoryItem(
                    session = summary.session,
                    exerciseCount = summary.exerciseCount,
                    setCount = summary.setCount,
                    totalVolume = summary.totalVolume
                )
            }
        }.cachedIn(viewModelScope)

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
}
