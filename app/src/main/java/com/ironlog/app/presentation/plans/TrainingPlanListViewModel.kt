package com.ironlog.app.presentation.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.domain.repository.ExerciseRepository
import com.ironlog.app.domain.repository.TrainingPlanRepository
import com.ironlog.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PlanListItem(
    val plan: TrainingPlan,
    val exerciseNames: List<String>
)

data class PlanListUiState(
    val plans: List<PlanListItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class TrainingPlanListViewModel(
    private val planRepository: TrainingPlanRepository,
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanListUiState())
    val uiState: StateFlow<PlanListUiState> = _uiState

    init {
        loadPlans()
    }

    private fun loadPlans() {
        viewModelScope.launch {
            planRepository.getAllPlans().collect { plans ->
                val items = plans.map { plan ->
                    val names = plan.exercises.map { exercise ->
                        val ex = exerciseRepository.getExerciseById(exercise.exerciseId)
                        ex?.name ?: "Unbekannt"
                    }
                    PlanListItem(plan = plan, exerciseNames = names)
                }
                _uiState.value = PlanListUiState(
                    plans = items,
                    isLoading = false
                )
            }
        }
    }

    fun deletePlan(planId: Long) {
        viewModelScope.launch {
            try {
                planRepository.deletePlan(planId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Plan konnte nicht gelöscht werden: ${e.message}"
                )
            }
        }
    }

    fun startPlanWorkout(plan: TrainingPlan, onSessionCreated: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                val sessionId = workoutRepository.startWorkout(plan.name)
                onSessionCreated(sessionId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Training konnte nicht gestartet werden: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
