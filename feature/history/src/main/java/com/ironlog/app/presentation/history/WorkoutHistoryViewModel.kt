package com.ironlog.app.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.domain.error.toAppError
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.domain.repository.WorkoutRepository
import com.ironlog.app.domain.util.catchAndLog
import com.ironlog.app.presentation.common.toUserMessage
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
    val isLoading: Boolean = true,
    val error: String? = null
)

class WorkoutHistoryViewModel(
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val _items = MutableStateFlow<List<WorkoutHistoryItem>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<WorkoutHistoryUiState> = combine(_items, _isLoading, _error) { items, loading, error ->
        WorkoutHistoryUiState(workouts = items, isLoading = loading, error = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkoutHistoryUiState())

    init {
        observeHistory()
    }

    private fun observeHistory() {
        viewModelScope.launch {
            workoutRepository.getAllCompletedSessions()
                .catchAndLog("WorkoutHistoryVM")
                .collect { sessions ->
                    try {
                        val limitedSessions = sessions.take(50)
                        val sessionIds = limitedSessions.map { it.id }
                        val setsBySession = workoutRepository
                            .getSetsForSessionsList(sessionIds)
                            .groupBy { it.sessionId }

                        val items = limitedSessions.map { session ->
                            val sessionSets = setsBySession[session.id].orEmpty()
                            WorkoutHistoryItem(
                                session = session,
                                exerciseCount = sessionSets.map { it.exerciseId }.distinct().size,
                                setCount = sessionSets.size,
                                totalVolume = sessionSets.filter { !it.isWarmup }.sumOf { it.weightKg * it.reps }
                            )
                        }
                        _items.value = items
                        _isLoading.value = false
                        _error.value = null
                    } catch (e: Exception) {
                        _isLoading.value = false
                        _error.value = e.toAppError().toUserMessage("Verlauf laden")
                    }
                }
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            try {
                workoutRepository.deleteSession(sessionId)
            } catch (e: Exception) {
                _error.value = e.toAppError().toUserMessage("Training loeschen")
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
