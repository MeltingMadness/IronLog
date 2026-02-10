package com.ironlog.app.presentation.plans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.PlanExercise
import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.domain.repository.ExerciseRepository
import com.ironlog.app.domain.repository.TrainingPlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PlanExerciseUi(
    val planExercise: PlanExercise,
    val exercise: Exercise
)

data class PlanEditorUiState(
    val planName: String = "",
    val exercises: List<PlanExerciseUi> = emptyList(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val showExercisePicker: Boolean = false,
    val error: String? = null
)

class PlanEditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val planRepository: TrainingPlanRepository,
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {

    private val planId: Long = savedStateHandle["planId"] ?: 0L
    val isEditMode: Boolean = planId > 0L

    private val _uiState = MutableStateFlow(PlanEditorUiState())
    val uiState: StateFlow<PlanEditorUiState> = _uiState

    init {
        if (isEditMode) {
            loadPlan()
        }
    }

    private fun loadPlan() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val plan = planRepository.getPlanById(planId)
                if (plan != null) {
                    val exerciseUis = plan.exercises.map { pe ->
                        val exercise = exerciseRepository.getExerciseById(pe.exerciseId)
                        PlanExerciseUi(
                            planExercise = pe.copy(exerciseName = exercise?.name ?: "Unbekannt"),
                            exercise = exercise ?: Exercise(
                                id = pe.exerciseId,
                                name = "Unbekannt",
                                primaryMuscleGroup = com.ironlog.app.domain.model.MuscleGroup.BRUST,
                                category = com.ironlog.app.domain.model.ExerciseCategory.LANGHANTEL
                            )
                        )
                    }
                    _uiState.value = PlanEditorUiState(
                        planName = plan.name,
                        exercises = exerciseUis,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Plan konnte nicht geladen werden: ${e.message}"
                )
            }
        }
    }

    fun updatePlanName(name: String) {
        _uiState.value = _uiState.value.copy(planName = name)
    }

    fun showExercisePicker() {
        _uiState.value = _uiState.value.copy(showExercisePicker = true)
    }

    fun dismissExercisePicker() {
        _uiState.value = _uiState.value.copy(showExercisePicker = false)
    }

    fun addExercise(exercise: Exercise) {
        val current = _uiState.value.exercises
        val newIndex = current.size
        val planExercise = PlanExercise(
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            orderIndex = newIndex,
            targetSets = 3,
            targetReps = 10,
            targetWeightKg = 0.0
        )
        _uiState.value = _uiState.value.copy(
            exercises = current + PlanExerciseUi(planExercise = planExercise, exercise = exercise),
            showExercisePicker = false
        )
    }

    fun removeExercise(index: Int) {
        val current = _uiState.value.exercises.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _uiState.value = _uiState.value.copy(exercises = current)
        }
    }

    fun moveUp(index: Int) {
        if (index <= 0) return
        val current = _uiState.value.exercises.toMutableList()
        val item = current.removeAt(index)
        current.add(index - 1, item)
        _uiState.value = _uiState.value.copy(exercises = current)
    }

    fun moveDown(index: Int) {
        val current = _uiState.value.exercises.toMutableList()
        if (index >= current.size - 1) return
        val item = current.removeAt(index)
        current.add(index + 1, item)
        _uiState.value = _uiState.value.copy(exercises = current)
    }

    fun updateTargetSets(index: Int, sets: Int) {
        updateExercise(index) { it.copy(targetSets = sets) }
    }

    fun updateTargetReps(index: Int, reps: Int) {
        updateExercise(index) { it.copy(targetReps = reps) }
    }

    fun updateTargetWeight(index: Int, weight: Double) {
        updateExercise(index) { it.copy(targetWeightKg = weight) }
    }

    private fun updateExercise(index: Int, transform: (PlanExercise) -> PlanExercise) {
        val current = _uiState.value.exercises.toMutableList()
        if (index in current.indices) {
            val item = current[index]
            current[index] = item.copy(planExercise = transform(item.planExercise))
            _uiState.value = _uiState.value.copy(exercises = current)
        }
    }

    fun savePlan() {
        val name = _uiState.value.planName.trim()
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Bitte gib einen Namen ein")
            return
        }

        viewModelScope.launch {
            try {
                val exercises = _uiState.value.exercises.mapIndexed { i, item ->
                    item.planExercise.copy(orderIndex = i)
                }
                val plan = TrainingPlan(
                    id = planId,
                    name = name,
                    exercises = exercises
                )
                planRepository.savePlan(plan)
                _uiState.value = _uiState.value.copy(isSaved = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Plan konnte nicht gespeichert werden: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
