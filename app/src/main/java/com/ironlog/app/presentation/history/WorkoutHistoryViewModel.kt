package com.ironlog.app.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkoutHistoryItem(
    val session: WorkoutSession,
    val exerciseCount: Int = 0,
    val setCount: Int = 0,
    val totalVolume: Double = 0.0
)

data class WorkoutHistoryUiState(
    val workouts: List<WorkoutHistoryItem> = emptyList(),
    val isLoading: Boolean = true
)

class WorkoutHistoryViewModel(
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val _items = MutableStateFlow<List<WorkoutHistoryItem>>(emptyList())
    private val _isLoading = MutableStateFlow(true)

    val uiState: StateFlow<WorkoutHistoryUiState> = combine(_items, _isLoading) { items, loading ->
        WorkoutHistoryUiState(workouts = items, isLoading = loading)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkoutHistoryUiState())

    init {
        observeHistory()
    }

    private fun observeHistory() {
        viewModelScope.launch {
            workoutRepository.getAllCompletedSessions().collect { sessions ->
                val items = sessions.map { session ->
                    val exerciseCount = workoutRepository.getExerciseIdsForSession(session.id).size
                    val setCount = workoutRepository.getSetCountForSession(session.id)
                    val volume = workoutRepository.getTotalVolumeForSession(session.id)
                    WorkoutHistoryItem(
                        session = session,
                        exerciseCount = exerciseCount,
                        setCount = setCount,
                        totalVolume = volume
                    )
                }
                _items.value = items
                _isLoading.value = false
            }
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            workoutRepository.deleteSession(sessionId)
        }
    }
}
