package com.ironlog.app.presentation.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.domain.repository.ExerciseRepository
import com.ironlog.app.domain.repository.TrainingPlanRepository
import com.ironlog.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
                // Create session with plan name
                val sessionId = workoutRepository.startWorkout(plan.name)

                // Pre-populate exercises by adding empty sets as placeholders
                // The exercises will be loaded via the plan in ActiveWorkoutViewModel
                for (exercise in plan.exercises.sortedBy { it.orderIndex }) {
                    val ex = exerciseRepository.getExerciseById(exercise.exerciseId)
                    if (ex != null) {
                        // Add a placeholder set with 0 reps to register the exercise
                        val set = com.ironlog.app.domain.model.WorkoutSet(
                            sessionId = sessionId,
                            exerciseId = exercise.exerciseId,
                            setNumber = 0,
                            reps = 0,
                            weightKg = 0.0,
                            completedAt = java.time.LocalDateTime.now()
                        )
                        workoutRepository.addSet(set)
                    }
                }

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
