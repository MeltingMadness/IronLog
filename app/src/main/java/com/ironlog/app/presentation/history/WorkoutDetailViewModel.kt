package com.ironlog.app.presentation.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.PersonalRecord
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.repository.ExerciseRepository
import com.ironlog.app.domain.repository.StatisticsRepository
import com.ironlog.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ExerciseDetail(
    val exercise: Exercise,
    val sets: List<WorkoutSet>,
    val records: List<PersonalRecord> = emptyList()
)

data class WorkoutDetailUiState(
    val session: WorkoutSession? = null,
    val exercises: List<ExerciseDetail> = emptyList(),
    val totalVolume: Double = 0.0,
    val isLoading: Boolean = true,
    val error: String? = null
)

class WorkoutDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val statisticsRepository: StatisticsRepository
) : ViewModel() {

    private val sessionId: Long = savedStateHandle["sessionId"] ?: -1L

    private val _uiState = MutableStateFlow(WorkoutDetailUiState())
    val uiState: StateFlow<WorkoutDetailUiState> = _uiState

    init {
        loadDetail()
    }

    private fun loadDetail() {
        viewModelScope.launch {
            try {
                val session = workoutRepository.getSessionById(sessionId)
                val sets = workoutRepository.getSetsForSessionList(sessionId)
                val volume = workoutRepository.getTotalVolumeForSession(sessionId)

                val grouped = sets.groupBy { it.exerciseId }
                val exercises = grouped.map { (exerciseId, exerciseSets) ->
                    val exercise = exerciseRepository.getExerciseById(exerciseId)
                    val records = try {
                        statisticsRepository.getRecentRecordsList()
                            .filter { it.exerciseId == exerciseId }
                    } catch (_: Exception) { emptyList() }
                    ExerciseDetail(
                        exercise = exercise ?: Exercise(
                            id = exerciseId,
                            name = "Unbekannt",
                            primaryMuscleGroup = com.ironlog.app.domain.model.MuscleGroup.BRUST,
                            category = com.ironlog.app.domain.model.ExerciseCategory.LANGHANTEL
                        ),
                        sets = exerciseSets.sortedBy { it.setNumber },
                        records = records
                    )
                }

                _uiState.value = WorkoutDetailUiState(
                    session = session,
                    exercises = exercises,
                    totalVolume = volume,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Training-Details konnten nicht geladen werden: ${e.message}"
                )
            }
        }
    }
}
