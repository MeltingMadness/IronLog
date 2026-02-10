package com.ironlog.app.presentation.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.RecordType
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.repository.ExerciseRepository
import com.ironlog.app.domain.repository.StatisticsRepository
import com.ironlog.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class ExerciseWithSets(
    val exercise: Exercise,
    val sets: List<WorkoutSet>
)

data class ActiveWorkoutUiState(
    val session: WorkoutSession? = null,
    val exercisesWithSets: List<ExerciseWithSets> = emptyList(),
    val showExercisePicker: Boolean = false,
    val showFinishDialog: Boolean = false
)

sealed class WorkoutEvent {
    data class NewRecord(val exerciseName: String, val type: RecordType) : WorkoutEvent()
}

class ActiveWorkoutViewModel(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val statisticsRepository: StatisticsRepository
) : ViewModel() {

    private val sessionId: Long = savedStateHandle["sessionId"] ?: -1L

    private val showExercisePicker = MutableStateFlow(false)
    private val showFinishDialog = MutableStateFlow(false)
    private val exercisesWithSets = MutableStateFlow<List<ExerciseWithSets>>(emptyList())
    private val addedExercises = MutableStateFlow<List<Exercise>>(emptyList())

    private val _events = MutableSharedFlow<WorkoutEvent>()
    val events = _events.asSharedFlow()

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        workoutRepository.observeSessionById(sessionId),
        exercisesWithSets,
        showExercisePicker,
        showFinishDialog
    ) { session, ewsList, showPicker, showFinish ->
        ActiveWorkoutUiState(
            session = session,
            exercisesWithSets = ewsList,
            showExercisePicker = showPicker,
            showFinishDialog = showFinish
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ActiveWorkoutUiState())

    init {
        observeSets()
    }

    private fun observeSets() {
        viewModelScope.launch {
            workoutRepository.getSetsForSession(sessionId).collect { sets ->
                val grouped = sets.groupBy { it.exerciseId }
                val fromSets = grouped.map { (exerciseId, exerciseSets) ->
                    val exercise = exerciseRepository.getExerciseById(exerciseId)
                    ExerciseWithSets(
                        exercise = exercise ?: Exercise(
                            id = exerciseId,
                            name = "Unbekannt",
                            primaryMuscleGroup = com.ironlog.app.domain.model.MuscleGroup.BRUST,
                            category = com.ironlog.app.domain.model.ExerciseCategory.LANGHANTEL
                        ),
                        sets = exerciseSets.sortedBy { it.setNumber }
                    )
                }
                // Merge with added exercises that have no sets yet
                val existingIds = fromSets.map { it.exercise.id }.toSet()
                val emptyExercises = addedExercises.value
                    .filter { it.id !in existingIds }
                    .map { ExerciseWithSets(exercise = it, sets = emptyList()) }
                exercisesWithSets.value = fromSets + emptyExercises
            }
        }
    }

    fun addExercise(exercise: Exercise) {
        val current = addedExercises.value
        if (current.none { it.id == exercise.id }) {
            addedExercises.value = current + exercise
            // Trigger UI update
            val existing = exercisesWithSets.value
            if (existing.none { it.exercise.id == exercise.id }) {
                exercisesWithSets.value = existing + ExerciseWithSets(exercise = exercise, sets = emptyList())
            }
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

    fun logSet(exerciseId: Long, reps: Int, weightKg: Double, isWarmup: Boolean = false) {
        viewModelScope.launch {
            val currentSets = exercisesWithSets.value
                .find { it.exercise.id == exerciseId }
                ?.sets ?: emptyList()
            val setNumber = currentSets.size + 1

            val set = WorkoutSet(
                sessionId = sessionId,
                exerciseId = exerciseId,
                setNumber = setNumber,
                reps = reps,
                weightKg = weightKg,
                isWarmup = isWarmup,
                completedAt = LocalDateTime.now()
            )
            workoutRepository.addSet(set)

            // Check for personal records
            if (!isWarmup) {
                checkRecords(exerciseId, reps, weightKg)
            }
        }
    }

    private suspend fun checkRecords(exerciseId: Long, reps: Int, weightKg: Double) {
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
            val e1rm = weightKg * (1 + reps / 30.0)
            if (statisticsRepository.checkAndUpdateRecord(exerciseId, RecordType.MAX_E1RM, e1rm)) {
                _events.emit(WorkoutEvent.NewRecord(exercise.name, RecordType.MAX_E1RM))
            }
        }

        // Volume (weight × reps)
        val volume = weightKg * reps
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
    }

    fun deleteSet(setId: Long) {
        viewModelScope.launch {
            workoutRepository.deleteSet(setId)
        }
    }

    fun finishWorkout() {
        viewModelScope.launch {
            workoutRepository.finishWorkout(sessionId)
            showFinishDialog.value = false
        }
    }
}
