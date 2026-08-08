package com.ironlog.app.presentation.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.IntensitySystem
import com.ironlog.app.domain.model.PersonalRecord
import com.ironlog.app.domain.model.PreviousSessionScope
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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

sealed interface ActiveWorkoutSessionPhase {
    data object Loading : ActiveWorkoutSessionPhase
    data class Active(val session: WorkoutSession) : ActiveWorkoutSessionPhase
    data object Missing : ActiveWorkoutSessionPhase
}

/**
 * Describes the last failed mutation so the UI can offer a real retry without
 * storing composable callbacks inside the ViewModel.
 */
sealed interface WorkoutRetryDescriptor {
    data class LogSet(
        val exerciseId: Long,
        val reps: Int,
        val weightKg: Double,
        val isWarmup: Boolean,
        val intensity: String,
        val submissionId: Long
    ) : WorkoutRetryDescriptor

    data class UpdateSet(
        val setId: Long,
        val reps: Int,
        val weightKg: Double,
        val intensity: String
    ) : WorkoutRetryDescriptor

    data class DeleteSet(val setId: Long) : WorkoutRetryDescriptor

    data class FinishWorkout(val discardEmptySession: Boolean) : WorkoutRetryDescriptor
}

data class WorkoutErrorUi(
    val message: String,
    val retry: WorkoutRetryDescriptor?,
    val id: Long
)

data class ActiveWorkoutUiState(
    val sessionPhase: ActiveWorkoutSessionPhase = ActiveWorkoutSessionPhase.Loading,
    val exercisesWithSets: List<ExerciseWithSets> = emptyList(),
    val showExercisePicker: Boolean = false,
    val showFinishDialog: Boolean = false,
    val restTimers: Map<Long, Instant> = emptyMap(),
    val error: WorkoutErrorUi? = null,
    val logInFlightByExercise: Map<Long, Int> = emptyMap(),
    val logSuccessSubmissions: Set<Long> = emptySet(),
    val updateInFlightBySet: Map<Long, Int> = emptyMap(),
    val updateSuccessCountBySet: Map<Long, Int> = emptyMap(),
    val finishInFlight: Boolean = false,
    val workoutFinished: Boolean = false
)

private data class ActiveWorkoutChromeState(
    val showExercisePicker: Boolean = false,
    val showFinishDialog: Boolean = false,
    val restTimers: Map<Long, Instant> = emptyMap(),
    val error: WorkoutErrorUi? = null
)

private data class OperationUiState(
    val logInFlightByExercise: Map<Long, Int> = emptyMap(),
    val logSuccessSubmissions: Set<Long> = emptySet(),
    val updateInFlightBySet: Map<Long, Int> = emptyMap(),
    val updateSuccessCountBySet: Map<Long, Int> = emptyMap(),
    val finishInFlight: Boolean = false,
    val workoutFinished: Boolean = false
)

private val submissionIdSequence = java.util.concurrent.atomic.AtomicLong(0L)

internal fun nextSubmissionId(): Long = submissionIdSequence.incrementAndGet()

sealed class WorkoutEvent {
    data class NewRecord(val exerciseName: String, val type: RecordType) : WorkoutEvent()
}

/**
 * Normalizes user-typed decimal input (weight/intensity) so that comma decimal
 * separators (common on non-US keyboards) are parsed the same as dots.
 */
fun parseDecimal(text: String): Double? = text.trim().replace(",", ".").toDoubleOrNull()

class ActiveWorkoutViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val statisticsRepository: StatisticsRepository,
    private val trainingPlanRepository: TrainingPlanRepository,
    private val appPreferencesRepository: AppPreferencesRepository
) : ViewModel() {

    private val sessionId: Long = savedStateHandle["sessionId"] ?: -1L
    private val planId: Long = savedStateHandle["planId"] ?: 0L
    private val metaPlanId: Long = savedStateHandle["metaPlanId"] ?: 0L

    private val showExercisePicker = MutableStateFlow(false)
    private val showFinishDialog = MutableStateFlow(false)
    private val addedExercises = MutableStateFlow<List<Exercise>>(emptyList())
    private val _error = MutableStateFlow<WorkoutErrorUi?>(null)
    private val _restTimers = MutableStateFlow<Map<Long, Instant>>(emptyMap())
    private val _planTargets = MutableStateFlow<Map<Long, PlanTarget>>(emptyMap())
    private val _planSupersetGroups = MutableStateFlow<Map<Long, Int?>>(emptyMap())
    private val operationState = MutableStateFlow(OperationUiState())
    private var errorSequence = 0L
    private val mutationMutex = Mutex()

    private val sessionPhase = workoutRepository.observeSessionById(sessionId)
        .map<WorkoutSession?, ActiveWorkoutSessionPhase> { session ->
            if (session != null) {
                ActiveWorkoutSessionPhase.Active(session)
            } else {
                ActiveWorkoutSessionPhase.Missing
            }
        }
        .onStart { emit(ActiveWorkoutSessionPhase.Loading) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ActiveWorkoutSessionPhase.Loading)

    private val sessionSets = workoutRepository.getSetsForSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val shareWeightHistoryAcrossContexts =
        appPreferencesRepository.preferences
            .map { it.shareWeightHistoryAcrossContexts }
            .distinctUntilChanged()

    private val exercisesWithSets = combine(
        sessionSets,
        addedExercises,
        _planTargets,
        _planSupersetGroups,
        shareWeightHistoryAcrossContexts
    ) { sets, added, targets, supersets, shareAcrossContexts ->
        val setsByExercise = sets.groupBy { it.exerciseId }
        val exerciseList = added.distinctBy { it.id }
        val previousSessionsByExercise = try {
            workoutRepository.getPreviousSessionDataForExercises(
                currentSessionId = sessionId,
                exerciseIds = exerciseList.map { it.id },
                scope = previousSessionScope(
                    planId = planId,
                    metaPlanId = metaPlanId,
                    shareAcrossContexts = shareAcrossContexts
                )
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

    internal fun previousSessionScope(
        planId: Long,
        metaPlanId: Long,
        shareAcrossContexts: Boolean
    ): PreviousSessionScope = when {
        planId <= 0L -> PreviousSessionScope.Global
        shareAcrossContexts -> PreviousSessionScope.SharedPlan(planId)
        metaPlanId > 0L -> PreviousSessionScope.MetaPlan(planId, metaPlanId)
        else -> PreviousSessionScope.NormalPlan(planId)
    }

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
        sessionPhase,
        exercisesWithSets,
        chromeState,
        operationState
    ) { phase, exercises, chrome, operation ->
        ActiveWorkoutUiState(
            sessionPhase = phase,
            exercisesWithSets = exercises,
            showExercisePicker = chrome.showExercisePicker,
            showFinishDialog = chrome.showFinishDialog,
            restTimers = chrome.restTimers,
            error = chrome.error,
            logInFlightByExercise = operation.logInFlightByExercise,
            logSuccessSubmissions = operation.logSuccessSubmissions,
            updateInFlightBySet = operation.updateInFlightBySet,
            updateSuccessCountBySet = operation.updateSuccessCountBySet,
            finishInFlight = operation.finishInFlight,
            workoutFinished = operation.workoutFinished
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ActiveWorkoutUiState())

    init {
        restoreAddedExercises()
        persistAddedExerciseIds()
        observeExerciseReconciliation()
        if (planId > 0L) {
            loadPlanExercises()
        }
    }

    /**
     * Restores exercises added to this session (e.g. via the exercise picker) after
     * process death, using the exercise IDs persisted in [savedStateHandle].
     */
    private fun restoreAddedExercises() {
        val savedIds: List<Long> = savedStateHandle[KEY_ADDED_EXERCISE_IDS] ?: emptyList()
        if (savedIds.isEmpty()) return
        viewModelScope.launch {
            try {
                val restored = exerciseRepository.getExercisesByIds(savedIds)
                if (restored.isEmpty()) return@launch
                val restoredById = restored.associateBy { it.id }
                val orderedRestored = savedIds.mapNotNull { restoredById[it] }
                addedExercises.update { current ->
                    val currentIds = current.map { it.id }.toSet()
                    (orderedRestored.filterNot { it.id in currentIds } + current).distinctBy { it.id }
                }
            } catch (e: Exception) {
                AppLogger.w("ActiveWorkoutVM", "Hinzugefuegte Uebungen konnten nicht wiederhergestellt werden: ${e.message}", e)
            }
        }
    }

    private fun persistAddedExerciseIds() {
        viewModelScope.launch {
            addedExercises.collect { exercises ->
                savedStateHandle[KEY_ADDED_EXERCISE_IDS] = exercises.map { it.id }
            }
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
                setError("Plan-Übungen konnten nicht geladen werden: ${e.message}")
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

    fun logSet(
        exerciseId: Long,
        reps: Int,
        weightKg: Double,
        isWarmup: Boolean = false,
        intensity: String = "",
        submissionId: Long = nextSubmissionId()
    ) {
        viewModelScope.launch {
            if (operationState.value.logInFlightByExercise[exerciseId] ?: 0 > 0) return@launch
            operationState.update {
                it.copy(logInFlightByExercise = incrementCounter(it.logInFlightByExercise, exerciseId))
            }
            var persisted = false
            try {
                mutationMutex.withLock {
                    if (operationState.value.workoutFinished) return@withLock
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
                    // The repository owns record recalculation and does it inside the same
                    // transaction as the insert. The ViewModel only compares before/after and
                    // must stay inside mutationMutex until that comparison is done, so a
                    // concurrent delete/update cannot interleave and later be overwritten by
                    // a stale add-based PR write.
                    // Best-effort snapshot: a statistics-read failure must never block the
                    // repository mutation. Without a snapshot no NewRecord event is emitted,
                    // because an unknown baseline could otherwise produce false positives.
                    val recordsBefore: Map<RecordType, PersonalRecord>? = if (!isWarmup) {
                        snapshotRecordsBefore(exerciseId)
                    } else {
                        null
                    }
                    workoutRepository.addSet(set)
                    persisted = true
                    if (!isWarmup && recordsBefore != null) {
                        emitImprovedRecords(exerciseId, recordsBefore)
                    }
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
                if (!persisted) return@launch

                operationState.update {
                    it.copy(logSuccessSubmissions = it.logSuccessSubmissions + submissionId)
                }
            } catch (e: Exception) {
                setError(
                    message = "Satz konnte nicht gespeichert werden: ${e.message}",
                    retry = WorkoutRetryDescriptor.LogSet(
                        exerciseId = exerciseId,
                        reps = reps,
                        weightKg = weightKg,
                        isWarmup = isWarmup,
                        intensity = intensity,
                        submissionId = submissionId
                    )
                )
            } finally {
                operationState.update {
                    it.copy(
                        logInFlightByExercise = decrementCounter(
                            it.logInFlightByExercise,
                            exerciseId
                        )
                    )
                }
            }
        }
    }

    fun dismissRestTimer(exerciseId: Long) {
        _restTimers.update { current ->
            current - exerciseId
        }
    }

    private suspend fun parseIntensity(intensity: String): Double? {
        val prefs = appPreferencesRepository.preferences.first()
        return computeIntensity(intensity, prefs.intensitySystem)
    }

    private fun computeIntensity(intensity: String, intensitySystem: IntensitySystem): Double? {
        if (intensity.isBlank()) return null
        val rawVal = parseDecimal(intensity) ?: return null
        return when (intensitySystem) {
            IntensitySystem.OFF -> null
            IntensitySystem.RPE -> rawVal
            IntensitySystem.RIR -> 10.0 - rawVal
        }
    }

    /**
     * Emits NewRecord only for values that actually improved after a successful
     * repository mutation; the repository owns record recalculation, so this
     * never writes a record itself.
     */
    private suspend fun emitImprovedRecords(
        exerciseId: Long,
        recordsBefore: Map<RecordType, PersonalRecord>
    ) {
        try {
            val exercise = exerciseRepository.getExerciseById(exerciseId) ?: return
            val recordsAfter = statisticsRepository.getRecordsForExercisesList(listOf(exerciseId))
                .associateBy { it.type }
            RecordType.entries.forEach { type ->
                val current = recordsAfter[type]?.value ?: return@forEach
                val previous = recordsBefore[type]?.value
                if (previous == null || current > previous) {
                    _events.emit(WorkoutEvent.NewRecord(exercise.name, type))
                }
            }
        } catch (e: Exception) {
            AppLogger.w("ActiveWorkoutVM", "PR-Pruefung nach Mutation fehlgeschlagen: ${e.message}", e)
        }
    }

    /**
     * Loads the current personal records as a comparison baseline before a repository
     * mutation. Best-effort by design: when statistics are temporarily unavailable the
     * mutation still proceeds and the caller simply skips NewRecord emission.
     */
    private suspend fun snapshotRecordsBefore(exerciseId: Long): Map<RecordType, PersonalRecord>? =
        try {
            statisticsRepository.getRecordsForExercisesList(listOf(exerciseId))
                .associateBy { it.type }
        } catch (e: Exception) {
            AppLogger.w("ActiveWorkoutVM", "PR-Snapshot konnte nicht geladen werden: ${e.message}", e)
            null
        }

    fun deleteSet(setId: Long) {
        viewModelScope.launch {
            mutationMutex.withLock {
                if (operationState.value.workoutFinished) return@withLock
                try {
                    workoutRepository.deleteSet(setId)
                } catch (e: Exception) {
                    setError(
                        message = "Satz konnte nicht gelöscht werden: ${e.message}",
                        retry = WorkoutRetryDescriptor.DeleteSet(setId = setId)
                    )
                }
            }
        }
    }

    fun updateSet(setId: Long, reps: Int, weightKg: Double, intensity: String = "") {
        viewModelScope.launch {
            operationState.update {
                it.copy(updateInFlightBySet = incrementCounter(it.updateInFlightBySet, setId))
            }
            try {
                // A cleared/invalid reps field must never persist a stale or zero value.
                if (reps <= 0) return@launch

                mutationMutex.withLock {
                    if (operationState.value.workoutFinished) return@withLock

                    val sets = workoutRepository.getSetsForSessionList(sessionId)
                    val set = sets.find { it.id == setId } ?: return@withLock

                    val prefs = appPreferencesRepository.preferences.first()
                    // When intensity tracking is OFF, the intensity UI is hidden and the
                    // incoming string is always blank — preserve the existing RPE instead
                    // of wiping it out. When intensity tracking is on, a blank string means
                    // the user intentionally cleared the field.
                    val newRpe = if (prefs.intensitySystem == IntensitySystem.OFF) {
                        set.rpe
                    } else {
                        computeIntensity(intensity, prefs.intensitySystem)
                    }

                    val updatedSet = set.copy(
                        reps = reps,
                        weightKg = weightKg,
                        rpe = newRpe
                    )
                    val recordsBefore = snapshotRecordsBefore(updatedSet.exerciseId)
                    workoutRepository.updateSet(updatedSet)
                    if (recordsBefore != null) {
                        emitImprovedRecords(updatedSet.exerciseId, recordsBefore)
                    }
                    operationState.update {
                        it.copy(
                            updateSuccessCountBySet = incrementCounter(
                                it.updateSuccessCountBySet,
                                setId
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                setError(
                    message = "Satz konnte nicht aktualisiert werden: ${e.message}",
                    retry = WorkoutRetryDescriptor.UpdateSet(
                        setId = setId,
                        reps = reps,
                        weightKg = weightKg,
                        intensity = intensity
                    )
                )
            } finally {
                operationState.update {
                    it.copy(updateInFlightBySet = decrementCounter(it.updateInFlightBySet, setId))
                }
            }
        }
    }

    fun finishWorkout() {
        viewModelScope.launch {
            mutationMutex.withLock {
                if (operationState.value.workoutFinished || operationState.value.finishInFlight) {
                    return@withLock
                }
                operationState.update { it.copy(finishInFlight = true) }
                var discardEmptySession = false
                try {
                    discardEmptySession = workoutRepository.getSetCountForSession(sessionId) == 0
                    if (discardEmptySession) {
                        workoutRepository.deleteSession(sessionId)
                    } else {
                        workoutRepository.finishWorkout(sessionId)
                    }
                    showFinishDialog.value = false
                    operationState.update { it.copy(workoutFinished = true) }
                } catch (e: Exception) {
                    setError(
                        message = "Training konnte nicht beendet werden: ${e.message}",
                        retry = WorkoutRetryDescriptor.FinishWorkout(
                            discardEmptySession = discardEmptySession
                        )
                    )
                } finally {
                    operationState.update { it.copy(finishInFlight = false) }
                }
            }
        }
    }

    fun retryLastError() {
        val retry = _error.value?.retry ?: return
        _error.value = null
        when (retry) {
            is WorkoutRetryDescriptor.LogSet -> logSet(
                exerciseId = retry.exerciseId,
                reps = retry.reps,
                weightKg = retry.weightKg,
                isWarmup = retry.isWarmup,
                intensity = retry.intensity,
                submissionId = retry.submissionId
            )
            is WorkoutRetryDescriptor.UpdateSet -> updateSet(
                setId = retry.setId,
                reps = retry.reps,
                weightKg = retry.weightKg,
                intensity = retry.intensity
            )
            is WorkoutRetryDescriptor.DeleteSet -> deleteSet(retry.setId)
            is WorkoutRetryDescriptor.FinishWorkout -> finishWorkout()
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun setError(message: String, retry: WorkoutRetryDescriptor? = null) {
        _error.value = WorkoutErrorUi(
            message = message,
            retry = retry,
            id = errorSequence++
        )
    }

    private fun incrementCounter(counter: Map<Long, Int>, key: Long): Map<Long, Int> =
        counter + (key to ((counter[key] ?: 0) + 1))

    private fun decrementCounter(counter: Map<Long, Int>, key: Long): Map<Long, Int> {
        val next = (counter[key] ?: 0) - 1
        return if (next <= 0) counter - key else counter + (key to next)
    }

    companion object {
        private const val KEY_ADDED_EXERCISE_IDS = "addedExerciseIds"
    }
}





