package com.ironlog.app.presentation.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.ExerciseCategory
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.domain.repository.ExerciseRepository
import com.ironlog.app.domain.util.catchAndLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    val editor: ExerciseEditorState? = null,
    val error: String? = null,
    val isLoading: Boolean = true
) {
    val showExerciseDialog: Boolean get() = editor != null
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseLibraryViewModel(
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedMuscleGroup = MutableStateFlow<MuscleGroup?>(null)
    private val editor = MutableStateFlow<ExerciseEditorState?>(null)
    private val error = MutableStateFlow<String?>(null)

    private val exercises = combine(searchQuery, selectedMuscleGroup) { query, group ->
        Pair(query, group)
    }.flatMapLatest { (query, group) ->
        when {
            query.isNotBlank() -> exerciseRepository.searchExercises(query)
            group != null -> exerciseRepository.getExercisesByMuscleGroup(group)
            else -> exerciseRepository.getAllExercises()
        }
    }.map { list ->
        list.filterNot { it.isArchived }
    }.catchAndLog("ExerciseLibraryVM")

    val uiState: StateFlow<ExerciseLibraryUiState> = combine(
        exercises,
        searchQuery,
        selectedMuscleGroup,
        editor,
        error
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        ExerciseLibraryUiState(
            exercises = args[0] as List<Exercise>,
            searchQuery = args[1] as String,
            selectedMuscleGroup = args[2] as MuscleGroup?,
            editor = args[3] as ExerciseEditorState?,
            error = args[4] as String?,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExerciseLibraryUiState())

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
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
                error.value = "Uebung konnte nicht gespeichert werden: ${e.message}"
            }
        }
    }

    fun deleteCustomExercise(id: Long) {
        viewModelScope.launch {
            try {
                exerciseRepository.deleteCustomExercise(id)
            } catch (e: Exception) {
                error.value = "Uebung konnte nicht geloescht werden: ${e.message}"
            }
        }
    }

    fun clearError() {
        error.value = null
    }
}
