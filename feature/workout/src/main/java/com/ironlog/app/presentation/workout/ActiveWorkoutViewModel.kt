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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class PlanTarget(
    val targetSets: Int = 0,
    val targetReps: Int = 0,
    val targetWeightKg: Double = 0.0
)

data class PreviousExerciseSessionUi(
    val sessionId: Long,
    val sessionStart: LocalDateTime,
    val sets: List<WorkoutSet>,
    val lastWorkSetWeightKg: Double?
)

data class ExerciseWithSets(
    val exercise: Exercise,
    val sets: List<WorkoutSet>,
    val planTarget: PlanTarget? = null,
    val supersetGroupId: Int? = null,
    val previousSession: PreviousExerciseSessionUi? = null
)

data class ActiveWorkoutUiState(
    val session: WorkoutSession? = null,
    val exercisesWithSets: List<ExerciseWithSets> = emptyList(),
    val showExercisePicker: Boolean = false,
    val showFinishDialog: Boolean = false,
    val restTimers: Map<Long, Instant> = emptyMap(),
    val error: String? = null
)

private data class ActiveWorkoutChromeState(
    val showExercisePicker: Boolean = false,
    val showFinishDialog: Boolean = false,
    val restTimers: Map<Long, Instant> = emptyMap(),
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
    private val _restTimers = MutableStateFlow<Map<Long, Instant>>(emptyMap())
    private val _planTargets = MutableStateFlow<Map<Long, PlanTarget>>(emptyMap())
    private val _planSupersetGroups = MutableStateFlow<Map<Long, Int?>>(emptyMap())
    private val logSetMutex = Mutex()

    private val sessionSets = workoutRepository.getSetsForSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val exercisesWithSets = combine(
        sessionSets,
        addedExercises,
        _planTargets,
        _planSupersetGroups
    ) { sets, added, targets, supersets ->
        val setsByExercise = sets.groupBy { it.exerciseId }
        val exerciseList = added.distinctBy { it.id }
        val previousSessionsByExercise = try {
            workoutRepository.getPreviousSessionDataForExercises(
                currentSessionId = sessionId,
                exerciseIds = exerciseList.map { it.id },
                planId = planId.takeIf { it > 0L }
            )
        } catch (e: Exception) {
            AppLogger.w("ActiveWorkoutVM", "Vorherige Sessiondaten konnten nicht geladen werden: ${e.message}", e)
            emptyMap()
        }

        exerciseList.map { exercise ->
            val previousSession = previousSessionsByExercise[exercise.id]
            ExerciseWithSets(
                exercise = exercise,
                sets = (setsByExercise[exercise.id] ?: emptyList()).sortedBy { it.setNumber },
                planTarget = targets[exercise.id],
                supersetGroupId = supersets[exercise.id],
                previousSession = previousSession?.let {
                    PreviousExerciseSessionUi(
                        sessionId = it.sessionId,
                        sessionStart = it.sessionStart,
                        sets = it.sets,
                        lastWorkSetWeightKg = it.lastWorkSetWeightKg
                    )
                }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = MutableSharedFlow<WorkoutEvent>()
    val events = _events.asSharedFlow()

    private val chromeState = combine(
        showExercisePicker,
        showFinishDialog,
        _restTimers,
        _error
    ) { pickerVisible, finishDialogVisible, restTimers, error ->
        ActiveWorkoutChromeState(
            showExercisePicker = pickerVisible,
            showFinishDialog = finishDialogVisible,
            restTimers = restTimers,
            error = error
        )
    }

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        workoutRepository.observeSessionById(sessionId),
        exercisesWithSets,
        chromeState
    ) { session, exercises, chrome ->
        ActiveWorkoutUiState(
            session = session,
            exercisesWithSets = exercises,
            showExercisePicker = chrome.showExercisePicker,
            showFinishDialog = chrome.showFinishDialog,
            restTimers = chrome.restTimers,
            error = chrome.error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ActiveWorkoutUiState())

    init {
        observeExerciseReconciliation()
        if (planId > 0L) {
            loadPlanExercises()
        }
    }

    private fun observeExerciseReconciliation() {
        viewModelScope.launch {
            sessionSets.collect { sets ->
                val knownIds = addedExercises.value.map { it.id }.toSet()
                val missingExercises = sets
                    .map { it.exerciseId }
                    .distinct()
                    .filterNot { it in knownIds }
                    .mapNotNull { exerciseRepository.getExerciseById(it) }

                if (missingExercises.isNotEmpty()) {
                    addedExercises.update { current ->
                        val currentIds = current.map { it.id }.toSet()
                        current + missingExercises.filterNot { it.id in currentIds }
                    }
                }
            }
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
                addedExercises.update { current ->
                    (newExercises + current).distinctBy { it.id }
                }
            } catch (e: Exception) {
                _error.value = "Plan-Übungen konnten nicht geladen werden: ${e.message}"
            }
        }
    }

    fun addExercise(exercise: Exercise) {
        addedExercises.update { current ->
            if (current.any { it.id == exercise.id }) current else current + exercise
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
                logSetMutex.withLock {
                    val persistedSets = workoutRepository.getSetsForSessionList(sessionId)
                        .filter { it.exerciseId == exerciseId }
                    val setNumber = (persistedSets.maxOfOrNull { it.setNumber } ?: 0) + 1
                    val parsedRpe = parseIntensity(intensity)
                    val completedAtInstant = Instant.now()

                    val set = WorkoutSet(
                        sessionId = sessionId,
                        exerciseId = exerciseId,
                        setNumber = setNumber,
                        reps = reps,
                        weightKg = weightKg,
                        isWarmup = isWarmup,
                        completedAt = LocalDateTime.ofInstant(
                            completedAtInstant,
                            ZoneId.systemDefault()
                        ),
                        rpe = parsedRpe
                    )
                    workoutRepository.addSet(set)
                    val planTarget = _planTargets.value[exerciseId]
                    val completedWorkSetCount = persistedSets.count { !it.isWarmup } + if (isWarmup) 0 else 1
                    val reachedPlannedSetCount = !isWarmup &&
                        planTarget != null &&
                        planTarget.targetSets > 0 &&
                        completedWorkSetCount >= planTarget.targetSets

                    _restTimers.update { currentTimers ->
                        if (reachedPlannedSetCount) {
                            currentTimers - exerciseId
                        } else {
                            currentTimers + (exerciseId to completedAtInstant)
                        }
                    }
                }

                // Check for personal records
                if (!isWarmup) {
                    checkRecords(exerciseId, reps, weightKg)
                }
            } catch (e: Exception) {
                _error.value = "Satz konnte nicht gespeichert werden: ${e.message}"
            }
        }
    }

    fun dismissRestTimer(exerciseId: Long) {
        _restTimers.update { current ->
            current - exerciseId
        }
    }

    private suspend fun parseIntensity(intensity: String): Double? {
        if (intensity.isBlank()) return null
        val rawVal = intensity.toDoubleOrNull() ?: return null
        val prefs = appPreferencesRepository.preferences.first()
        return when (prefs.intensitySystem) {
            IntensitySystem.OFF -> null
            IntensitySystem.RPE -> rawVal
            IntensitySystem.RIR -> 10.0 - rawVal
        }
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

    fun updateSet(setId: Long, reps: Int, weightKg: Double, intensity: String = "") {
        viewModelScope.launch {
            try {
                val sets = workoutRepository.getSetsForSessionList(sessionId)
                val set = sets.find { it.id == setId } ?: return@launch

                val parsedRpe = parseIntensity(intensity)

                val updatedSet = set.copy(
                    reps = reps,
                    weightKg = weightKg,
                    rpe = parsedRpe
                )
                workoutRepository.updateSet(updatedSet)

                // Check for personal records
                if (!updatedSet.isWarmup) {
                    checkRecords(updatedSet.exerciseId, updatedSet.reps, updatedSet.weightKg)
                }
            } catch (e: Exception) {
                _error.value = "Satz konnte nicht aktualisiert werden: ${e.message}"
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





