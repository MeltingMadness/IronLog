package com.ironlog.app.presentation.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.IntensitySystem
import com.ironlog.app.domain.model.RecordType
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.domain.repository.ExerciseRepository
import com.ironlog.app.domain.repository.StatisticsRepository
import com.ironlog.app.domain.repository.TrainingPlanRepository
import com.ironlog.app.domain.repository.WorkoutRepository
import com.ironlog.app.domain.util.AppLogger
import com.ironlog.app.domain.util.catchAndLog
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class PlanTarget(
    val targetSets: Int = 0,
    val targetReps: Int = 0,
    val targetWeightKg: Double = 0.0
)

data class ExerciseWithSets(
    val exercise: Exercise,
    val sets: List<WorkoutSet>,
    val planTarget: PlanTarget? = null,
    val supersetGroupId: Int? = null
)

data class ActiveWorkoutUiState(
    val session: WorkoutSession? = null,
    val exercisesWithSets: List<ExerciseWithSets> = emptyList(),
    val showExercisePicker: Boolean = false,
    val showFinishDialog: Boolean = false,
    val restTimerStartTime: LocalDateTime? = null,
    val error: String? = null
)

sealed class WorkoutEvent {
    data class NewRecord(val exerciseName: String, val type: RecordType) : WorkoutEvent()
}

class ActiveWorkoutViewModel(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val statisticsRepository: StatisticsRepository,
    private val trainingPlanRepository: TrainingPlanRepository,
    private val appPreferencesRepository: AppPreferencesRepository
) : ViewModel() {

    private val sessionId: Long = savedStateHandle["sessionId"] ?: -1L
    private val planId: Long = savedStateHandle["planId"] ?: 0L

        private val showExercisePicker = MutableStateFlow(false)
        private val showFinishDialog = MutableStateFlow(false)
        private val addedExercises = MutableStateFlow<List<Exercise>>(emptyList())
        private val _error = MutableStateFlow<String?>(null)
        private val _restTimerStartTime = MutableStateFlow<LocalDateTime?>(null)
    
        private val _planTargets = MutableStateFlow<Map<Long, PlanTarget>>(emptyMap())
        private val _planSupersetGroups = MutableStateFlow<Map<Long, Int?>>(emptyMap())
    private val exercisesWithSets = combine(
        workoutRepository.getSetsForSession(sessionId),
        addedExercises,
        _planTargets,
        _planSupersetGroups
    ) { sets, added, targets, supersets ->
        val setsByExercise = sets.groupBy { it.exerciseId }
        val missingIds = setsByExercise.keys - added.map { it.id }.toSet()
        val dynamicallyAdded = missingIds.mapNotNull { id -> exerciseRepository.getExerciseById(id) }
        
        if (dynamicallyAdded.isNotEmpty()) {
            addedExercises.value = added + dynamicallyAdded
        }
        
        val fullList = added + dynamicallyAdded
        
        fullList.map { exercise ->
            ExerciseWithSets(
                exercise = exercise,
                sets = (setsByExercise[exercise.id] ?: emptyList()).sortedBy { it.setNumber },
                planTarget = targets[exercise.id],
                supersetGroupId = supersets[exercise.id]
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = MutableSharedFlow<WorkoutEvent>()
    val events = _events.asSharedFlow()

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        workoutRepository.observeSessionById(sessionId),
        exercisesWithSets,
        showExercisePicker,
        showFinishDialog,
        _restTimerStartTime,
        _error
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        ActiveWorkoutUiState(
            session = args[0] as WorkoutSession?,
            exercisesWithSets = args[1] as List<ExerciseWithSets>,
            showExercisePicker = args[2] as Boolean,
            showFinishDialog = args[3] as Boolean,
            restTimerStartTime = args[4] as LocalDateTime?,
            error = args[5] as String?
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ActiveWorkoutUiState())

    init {
        if (planId > 0L) {
            loadPlanExercises()
        }
    }

    /**
     * Pre-populate exercises from a training plan (no sets created yet).
     */
    private fun loadPlanExercises() {
        viewModelScope.launch {
            try {
                val plan = trainingPlanRepository.getPlanById(planId) ?: return@launch
                val newTargets = mutableMapOf<Long, PlanTarget>()
                val newSupersets = mutableMapOf<Long, Int?>()
                val newExercises = mutableListOf<Exercise>()
                
                for (planExercise in plan.exercises.sortedBy { it.orderIndex }) {
                    val exercise = exerciseRepository.getExerciseById(planExercise.exerciseId)
                    if (exercise != null) {
                        newTargets[exercise.id] = PlanTarget(
                            targetSets = planExercise.targetSets,
                            targetReps = planExercise.targetReps,
                            targetWeightKg = planExercise.targetWeightKg
                        )
                        newSupersets[exercise.id] = planExercise.supersetGroupId
                        newExercises.add(exercise)
                    }
                }
                _planTargets.value = newTargets
                _planSupersetGroups.value = newSupersets
                addedExercises.value = newExercises
            } catch (e: Exception) {
                _error.value = "Plan-Übungen konnten nicht geladen werden: ${e.message}"
            }
        }
    }

    fun addExercise(exercise: Exercise) {
        val current = addedExercises.value
        if (current.none { it.id == exercise.id }) {
            addedExercises.value = current + exercise
        }
    }

    fun showExercisePicker() {
        showExercisePicker.value = true
    }

    fun dismissExercisePicker() {
        showExercisePicker.value = false
    }

    fun showFinishDialog() {
        showFinishDialog.value = true
    }

    fun dismissFinishDialog() {
        showFinishDialog.value = false
    }

    fun logSet(exerciseId: Long, reps: Int, weightKg: Double, isWarmup: Boolean = false, intensity: String = "") {
        viewModelScope.launch {
            try {
                val persistedSets = workoutRepository.getSetsForSessionList(sessionId)
                    .filter { it.exerciseId == exerciseId }
                val setNumber = (persistedSets.maxOfOrNull { it.setNumber } ?: 0) + 1

                var parsedRpe: Double? = null
                if (intensity.isNotBlank()) {
                    val rawVal = intensity.toDoubleOrNull()
                    if (rawVal != null) {
                        val prefs = appPreferencesRepository.preferences.first()
                        parsedRpe = when (prefs.intensitySystem) {
                            IntensitySystem.OFF -> null
                            IntensitySystem.RPE -> rawVal
                            IntensitySystem.RIR -> 10.0 - rawVal
                        }
                    }
                }

                val set = WorkoutSet(
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    setNumber = setNumber,
                    reps = reps,
                    weightKg = weightKg,
                    isWarmup = isWarmup,
                    completedAt = LocalDateTime.now(),
                    rpe = parsedRpe
                )
                workoutRepository.addSet(set)
                
                _restTimerStartTime.value = LocalDateTime.now()

                // Check for personal records
                if (!isWarmup) {
                    checkRecords(exerciseId, reps, weightKg)
                }
            } catch (e: Exception) {
                _error.value = "Satz konnte nicht gespeichert werden: ${e.message}"
            }
        }
    }

    fun dismissRestTimer() {
        _restTimerStartTime.value = null
    }

    private suspend fun checkRecords(exerciseId: Long, reps: Int, weightKg: Double) {
        try {
            val exercise = exerciseRepository.getExerciseById(exerciseId) ?: return

            // Max weight
            if (statisticsRepository.checkAndUpdateRecord(exerciseId, RecordType.MAX_WEIGHT, weightKg)) {
                _events.emit(WorkoutEvent.NewRecord(exercise.name, RecordType.MAX_WEIGHT))
            }

            // Max reps
            if (statisticsRepository.checkAndUpdateRecord(exerciseId, RecordType.MAX_REPS, reps.toDouble())) {
                _events.emit(WorkoutEvent.NewRecord(exercise.name, RecordType.MAX_REPS))
            }

            // Estimated 1RM (Epley)
            if (reps > 1) {
                val e1rm = com.ironlog.app.domain.util.WorkoutCalculations.calculateE1RM(weightKg, reps)
                if (statisticsRepository.checkAndUpdateRecord(exerciseId, RecordType.MAX_E1RM, e1rm)) {
                    _events.emit(WorkoutEvent.NewRecord(exercise.name, RecordType.MAX_E1RM))
                }
            }

            // Volume (weight × reps)
            val allSets = statisticsRepository.getSetsForExerciseList(exerciseId)
            // Group by session and find max session volume
            val sessionVolumes = allSets
                .filter { !it.isWarmup }
                .groupBy { it.sessionId }
                .mapValues { (_, sets) -> sets.sumOf { it.weightKg * it.reps } }
            val maxVolume = sessionVolumes.values.maxOrNull() ?: 0.0
            if (maxVolume > 0) {
                statisticsRepository.checkAndUpdateRecord(exerciseId, RecordType.MAX_VOLUME, maxVolume)
            }
        } catch (e: Exception) {
            // PR check failure should not crash the app, just log it
            AppLogger.w("ActiveWorkoutVM", "PR-Pruefung fehlgeschlagen: ${e.message}", e)
        }
    }

    fun deleteSet(setId: Long) {
        viewModelScope.launch {
            try {
                workoutRepository.deleteSet(setId)
            } catch (e: Exception) {
                _error.value = "Satz konnte nicht gelöscht werden: ${e.message}"
            }
        }
    }

    fun finishWorkout() {
        viewModelScope.launch {
            try {
                workoutRepository.finishWorkout(sessionId)
                showFinishDialog.value = false
            } catch (e: Exception) {
                _error.value = "Training konnte nicht beendet werden: ${e.message}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}





