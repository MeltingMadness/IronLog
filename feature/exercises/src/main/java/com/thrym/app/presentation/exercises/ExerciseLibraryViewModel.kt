package com.thrym.app.presentation.exercises

import com.thrym.app.presentation.common.UiStrings
import com.thrym.app.presentation.common.exerciseName
import com.thrym.app.presentation.common.filterAndSortByDisplayName
import com.thrym.app.presentation.common.toUserDetail
import com.thrym.feature.exercises.R as UxR
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thrym.app.domain.model.Exercise
import com.thrym.app.domain.model.ExerciseCategory
import com.thrym.app.domain.model.ExerciseTrainingSummary
import com.thrym.app.domain.model.MuscleGroup
import com.thrym.app.domain.repository.ExerciseRepository
import com.thrym.app.domain.repository.StatisticsRepository
import com.thrym.app.domain.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class ExerciseEditorState(
    val id: Long? = null,
    val name: String = "",
    val primaryMuscleGroup: MuscleGroup = MuscleGroup.BRUST,
    val secondaryMuscleGroups: Set<MuscleGroup> = emptySet(),
    val category: ExerciseCategory = ExerciseCategory.LANGHANTEL,
    val notes: String = ""
) {
    val isEditMode: Boolean get() = id != null

    companion object {
        fun fromExercise(exercise: Exercise): ExerciseEditorState = ExerciseEditorState(
            id = exercise.id,
            name = exercise.name,
            primaryMuscleGroup = exercise.primaryMuscleGroup,
            secondaryMuscleGroups = exercise.secondaryMuscleGroups.toSet(),
            category = exercise.category,
            notes = exercise.notes
        )
    }
}

data class ExerciseLibraryUiState(
    val exercises: List<Exercise> = emptyList(),
    val searchQuery: String = "",
    val selectedMuscleGroup: MuscleGroup? = null,
    /** Training counts per exercise id; exercises never trained are missing. */
    val trainingSummaries: Map<Long, ExerciseTrainingSummary> = emptyMap(),
    val editor: ExerciseEditorState? = null,
    val error: String? = null,
    val isLoading: Boolean = true
) {
    val showExerciseDialog: Boolean get() = editor != null
}

class ExerciseLibraryViewModel(
    private val exerciseRepository: ExerciseRepository,
    private val statisticsRepository: StatisticsRepository,
    private val strings: UiStrings
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedMuscleGroup = MutableStateFlow<MuscleGroup?>(null)
    private val editor = MutableStateFlow<ExerciseEditorState?>(null)
    private val error = MutableStateFlow<String?>(null)
    // UI locale; a runtime language switch keeps this ViewModel, so the screen reports it
    // and the list is sorted again by the names in the new language.
    private val locale = MutableStateFlow(strings.locale)

    // Filtering and sorting run in memory on the localized display name: the database
    // only knows the stored (German canonical) names of the built-in exercises.
    private val exercises = combine(
        exerciseRepository.getAllExercises(),
        searchQuery,
        selectedMuscleGroup,
        locale
    ) { list, query, group, uiLocale ->
        list.filterNot { it.isArchived }
            .filter { query.isNotBlank() || group == null || it.primaryMuscleGroup == group }
            .filterAndSortByDisplayName(query, displayName = { strings.exerciseName(it) }, locale = uiLocale)
    }.catch { e ->
        // Unlike catchAndLog, this must keep the combine() below alive with a value so
        // isLoading doesn't stay stuck at its initial `true` forever (see History pattern).
        // Logging is best-effort: it must never prevent the error/isLoading state below
        // from being applied (e.g. android.util.Log is unavailable in plain JVM unit tests).
        runCatching { AppLogger.e("ExerciseLibraryVM", "Flow-Fehler: ${e.message}", e) }
        error.value = strings.get(UxR.string.exercises_load_failed, e.toUserDetail(strings))
        emit(emptyList())
    }

    // Counts are optional decoration: a failure must not hide the exercise list.
    private val trainingSummaries = statisticsRepository.observeExerciseTrainingSummaries()
        .map { list -> list.associateBy { it.exerciseId } }
        .catch { e ->
            runCatching { AppLogger.w("ExerciseLibraryVM", "Trainingszahlen nicht geladen: ${e.message}", e) }
            emit(emptyMap())
        }

    val uiState: StateFlow<ExerciseLibraryUiState> = combine(
        exercises,
        searchQuery,
        selectedMuscleGroup,
        editor,
        error,
        trainingSummaries
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        ExerciseLibraryUiState(
            exercises = args[0] as List<Exercise>,
            searchQuery = args[1] as String,
            selectedMuscleGroup = args[2] as MuscleGroup?,
            editor = args[3] as ExerciseEditorState?,
            error = args[4] as String?,
            trainingSummaries = args[5] as Map<Long, ExerciseTrainingSummary>,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExerciseLibraryUiState())

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    /** Called by the screen with the current UI locale; a change re-sorts the list. */
    fun onLocaleChanged(locale: Locale) {
        this.locale.value = locale
    }

    fun onMuscleGroupSelected(group: MuscleGroup?) {
        selectedMuscleGroup.value = group
    }

    fun onShowAddDialog() {
        editor.value = ExerciseEditorState()
    }

    fun onShowEditDialog(exercise: Exercise) {
        if (!exercise.isCustom) return
        editor.value = ExerciseEditorState.fromExercise(exercise)
    }

    fun onDismissExerciseDialog() {
        editor.value = null
    }

    fun saveCustomExercise(
        id: Long?,
        name: String,
        primaryMuscleGroup: MuscleGroup,
        secondaryMuscleGroups: List<MuscleGroup>,
        category: ExerciseCategory,
        notes: String
    ) {
        viewModelScope.launch {
            try {
                val payload = Exercise(
                    id = id ?: 0L,
                    name = name,
                    primaryMuscleGroup = primaryMuscleGroup,
                    secondaryMuscleGroups = secondaryMuscleGroups,
                    category = category,
                    isCustom = true,
                    notes = notes
                )
                if (id == null) {
                    exerciseRepository.addCustomExercise(payload)
                } else {
                    exerciseRepository.updateCustomExercise(payload)
                }
                editor.value = null
            } catch (e: Exception) {
                error.value = strings.get(UxR.string.exercises_save_failed, e.toUserDetail(strings))
            }
        }
    }

    fun deleteCustomExercise(id: Long) {
        viewModelScope.launch {
            try {
                exerciseRepository.deleteCustomExercise(id)
            } catch (e: Exception) {
                error.value = strings.get(UxR.string.exercises_delete_failed, e.toUserDetail(strings))
            }
        }
    }

    fun clearError() {
        error.value = null
    }
}
