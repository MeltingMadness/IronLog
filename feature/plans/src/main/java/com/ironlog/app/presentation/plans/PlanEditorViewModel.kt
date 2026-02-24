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
                    val normalizedExercises = normalizeExercises(exerciseUis)
                    _uiState.value = PlanEditorUiState(
                        planName = plan.name,
                        exercises = normalizedExercises,
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
            supersetGroupId = null,
            targetSets = 3,
            targetReps = 10,
            targetWeightKg = 0.0
        )
        _uiState.value = _uiState.value.copy(showExercisePicker = false)
        setExercises(current + PlanExerciseUi(planExercise = planExercise, exercise = exercise))
    }

    fun removeExercise(index: Int) {
        val current = _uiState.value.exercises.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            setExercises(current)
        }
    }

    fun moveUp(index: Int) {
        if (index <= 0) return
        val current = _uiState.value.exercises.toMutableList()
        val item = current.removeAt(index)
        current.add(index - 1, item)
        setExercises(current)
    }

    fun moveDown(index: Int) {
        val current = _uiState.value.exercises.toMutableList()
        if (index >= current.size - 1) return
        val item = current.removeAt(index)
        current.add(index + 1, item)
        setExercises(current)
    }

    fun groupWithPrevious(index: Int) {
        val current = _uiState.value.exercises
        if (index <= 0 || index >= current.size) return

        val previousGroup = current[index - 1].planExercise.supersetGroupId
        val currentGroup = current[index].planExercise.supersetGroupId
        val nextGroupId = (current.maxOfOrNull { it.planExercise.supersetGroupId ?: 0 } ?: 0) + 1
        val targetGroupId = previousGroup ?: currentGroup ?: nextGroupId

        val grouped = current.mapIndexed { itemIndex, item ->
            val belongsToPreviousGroup = previousGroup != null && item.planExercise.supersetGroupId == previousGroup
            val belongsToCurrentGroup = currentGroup != null && item.planExercise.supersetGroupId == currentGroup
            val shouldGroup = itemIndex == index - 1 ||
                itemIndex == index ||
                belongsToPreviousGroup ||
                belongsToCurrentGroup

            if (shouldGroup) {
                item.copy(planExercise = item.planExercise.copy(supersetGroupId = targetGroupId))
            } else {
                item
            }
        }

        setExercises(grouped)
    }

    fun ungroup(index: Int) {
        val current = _uiState.value.exercises
        if (index !in current.indices) return

        val ungrouped = current.mapIndexed { itemIndex, item ->
            if (itemIndex == index) {
                item.copy(planExercise = item.planExercise.copy(supersetGroupId = null))
            } else {
                item
            }
        }
        setExercises(ungrouped)
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
            setExercises(current)
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
                val normalizedExercises = normalizeExercises(_uiState.value.exercises)
                val exercises = normalizedExercises.map { it.planExercise }
                val plan = TrainingPlan(
                    id = planId,
                    name = name,
                    exercises = exercises
                )
                planRepository.savePlan(plan)
                _uiState.value = _uiState.value.copy(
                    exercises = normalizedExercises,
                    isSaved = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Plan konnte nicht gespeichert werden: ${e.message}"
                )
            }
        }
    }

    private fun setExercises(exercises: List<PlanExerciseUi>) {
        _uiState.value = _uiState.value.copy(exercises = normalizeExercises(exercises))
    }

    private fun normalizeExercises(exercises: List<PlanExerciseUi>): List<PlanExerciseUi> {
        if (exercises.isEmpty()) return emptyList()

        val reindexed = exercises
            .mapIndexed { index, item ->
                item.copy(planExercise = item.planExercise.copy(orderIndex = index))
            }
            .toMutableList()

        // Collapse singleton runs and split reused non-contiguous IDs into independent runs.
        var cursor = 0
        while (cursor < reindexed.size) {
            val runGroupId = reindexed[cursor].planExercise.supersetGroupId
            if (runGroupId == null) {
                cursor++
                continue
            }
            var endExclusive = cursor + 1
            while (
                endExclusive < reindexed.size &&
                reindexed[endExclusive].planExercise.supersetGroupId == runGroupId
            ) {
                endExclusive++
            }
            if (endExclusive - cursor < 2) {
                for (index in cursor until endExclusive) {
                    val item = reindexed[index]
                    reindexed[index] = item.copy(
                        planExercise = item.planExercise.copy(supersetGroupId = null)
                    )
                }
            }
            cursor = endExclusive
        }

        // Reassign visible runs to compact IDs (S1..Sn).
        var nextGroupId = 1
        cursor = 0
        while (cursor < reindexed.size) {
            val runGroupId = reindexed[cursor].planExercise.supersetGroupId
            if (runGroupId == null) {
                cursor++
                continue
            }
            var endExclusive = cursor + 1
            while (
                endExclusive < reindexed.size &&
                reindexed[endExclusive].planExercise.supersetGroupId == runGroupId
            ) {
                endExclusive++
            }
            val normalizedGroupId = nextGroupId++
            for (index in cursor until endExclusive) {
                val item = reindexed[index]
                reindexed[index] = item.copy(
                    planExercise = item.planExercise.copy(supersetGroupId = normalizedGroupId)
                )
            }
            cursor = endExclusive
        }

        return reindexed
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun onPickerError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }
}
