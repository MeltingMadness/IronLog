package com.ironlog.app.presentation.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.domain.error.toAppError
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.PersonalRecord
import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.ProgressionOutcome
import com.ironlog.app.domain.model.ProgressionSuggestion
import com.ironlog.app.domain.model.WeightStep
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.repository.ExerciseRepository
import com.ironlog.app.domain.repository.ProgressionRepository
import com.ironlog.app.domain.repository.StatisticsRepository
import com.ironlog.app.domain.repository.WorkoutRepository
import com.ironlog.app.presentation.common.toUserMessage
import com.ironlog.app.presentation.progression.ProgressionReviewItemUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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
    val notFound: Boolean = false,
    val error: String? = null,
    /** Progression coach outcomes that were evaluated for this past session. */
    val progressionOutcomes: List<ProgressionReviewItemUi> = emptyList()
)

class WorkoutDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val statisticsRepository: StatisticsRepository,
    private val progressionRepository: ProgressionRepository
) : ViewModel() {

    private val sessionId: Long = savedStateHandle["sessionId"] ?: -1L

    private val _uiState = MutableStateFlow(WorkoutDetailUiState())
    val uiState: StateFlow<WorkoutDetailUiState> = _uiState

    init {
        loadDetail()
        observeProgressionOutcomes()
    }

    /**
     * Streams the progression suggestions the coach generated for this session
     * (all statuses — pending, accepted, rejected, informational, stale) so the
     * detail screen can show what the coach concluded for each exercise.
     */
    private fun observeProgressionOutcomes() {
        viewModelScope.launch {
            progressionRepository.observeReviewItems(sessionId).collect { suggestions ->
                val exerciseIds = suggestions.map { it.sourceTarget.exerciseId }.distinct()
                val namesById = if (exerciseIds.isEmpty()) {
                    emptyMap()
                } else {
                    exerciseRepository.getExercisesByIds(exerciseIds)
                        .associate { it.id to it.name }
                }
                val items = suggestions.map { suggestion ->
                    suggestion.toDetailItem(namesById[suggestion.sourceTarget.exerciseId])
                }
                _uiState.update { it.copy(progressionOutcomes = items) }
            }
        }
    }

    private fun loadDetail() {
        viewModelScope.launch {
            try {
                val session = workoutRepository.getSessionById(sessionId)
                if (session == null) {
                    // Session was deleted (e.g. from the history list) or the id is invalid —
                    // surface an explicit not-found state instead of an empty scaffold.
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        notFound = true
                    )
                    return@launch
                }

                val sets = workoutRepository.getSetsForSessionList(sessionId)
                val volume = workoutRepository.getTotalVolumeForSession(sessionId)

                val grouped = sets.groupBy { it.exerciseId }
                val exerciseIds = grouped.keys.toList()
                val exercisesById = exerciseRepository.getExercisesByIds(exerciseIds).associateBy { it.id }
                val recordsByExercise = statisticsRepository
                    .getRecordsForExercisesList(exerciseIds)
                    .groupBy { it.exerciseId }

                val exercises = grouped.map { (exerciseId, exerciseSets) ->
                    ExerciseDetail(
                        exercise = exercisesById[exerciseId] ?: Exercise(
                            id = exerciseId,
                            name = "Unbekannt",
                            primaryMuscleGroup = com.ironlog.app.domain.model.MuscleGroup.BRUST,
                            category = com.ironlog.app.domain.model.ExerciseCategory.LANGHANTEL
                        ),
                        sets = exerciseSets.sortedBy { it.setNumber },
                        records = recordsByExercise[exerciseId].orEmpty()
                    )
                }

                _uiState.update {
                    it.copy(
                        session = session,
                        exercises = exercises,
                        totalVolume = volume,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toAppError().toUserMessage("Training-Details laden")
                )
            }
        }
    }

    /** Maps a stored suggestion to the read-only review row used by the detail screen. */
    private fun ProgressionSuggestion.toDetailItem(
        exerciseName: String?
    ): ProgressionReviewItemUi {
        val proposed = (outcome as? ProgressionOutcome.ProposeChange)?.proposedTarget
        return ProgressionReviewItemUi(
            id = id,
            sourceSessionId = sourceTarget.sessionId,
            planId = sourceTarget.planId,
            exerciseId = sourceTarget.exerciseId,
            exerciseName = exerciseName,
            orderIndex = sourceTarget.orderIndex,
            scheme = sourceTarget.config.scheme,
            source = sourceTarget.target,
            proposed = proposed,
            countedSets = countedSets,
            reasonCode = outcome.reasonCode,
            reasonArguments = outcome.reasonArguments,
            status = status,
            // History is read-only: decision actions stay in the review screen.
            canDecide = false,
            configuredStep = sourceTarget.config.configuredStep()
        )
    }

    private fun ProgressionConfig.configuredStep(): WeightStep? = when (this) {
        is ProgressionConfig.Linear -> step
        is ProgressionConfig.DoubleProgression -> step
        is ProgressionConfig.TotalReps -> step
        is ProgressionConfig.RpeRir -> step
        is ProgressionConfig.Manual,
        is ProgressionConfig.Invalid -> null
    }
}
