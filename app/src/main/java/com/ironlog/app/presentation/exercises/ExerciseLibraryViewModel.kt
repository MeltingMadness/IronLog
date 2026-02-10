package com.ironlog.app.presentation.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.ExerciseCategory
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.domain.repository.ExerciseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExerciseLibraryUiState(
    val exercises: List<Exercise> = emptyList(),
    val searchQuery: String = "",
    val selectedMuscleGroup: MuscleGroup? = null,
    val showAddDialog: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseLibraryViewModel(
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedMuscleGroup = MutableStateFlow<MuscleGroup?>(null)
    private val showAddDialog = MutableStateFlow(false)

    private val exercises = combine(searchQuery, selectedMuscleGroup) { query, group ->
        Pair(query, group)
    }.flatMapLatest { (query, group) ->
        when {
            query.isNotBlank() -> exerciseRepository.searchExercises(query)
            group != null -> exerciseRepository.getExercisesByMuscleGroup(group)
            else -> exerciseRepository.getAllExercises()
        }
    }

    val uiState: StateFlow<ExerciseLibraryUiState> = combine(
        exercises, searchQuery, selectedMuscleGroup, showAddDialog
    ) { exerciseList, query, group, showDialog ->
        ExerciseLibraryUiState(
            exercises = exerciseList,
            searchQuery = query,
            selectedMuscleGroup = group,
            showAddDialog = showDialog
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExerciseLibraryUiState())

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onMuscleGroupSelected(group: MuscleGroup?) {
        selectedMuscleGroup.value = group
    }

    fun onShowAddDialog() {
        showAddDialog.value = true
    }

    fun onDismissAddDialog() {
        showAddDialog.value = false
    }

    fun addCustomExercise(name: String, muscleGroup: MuscleGroup, category: ExerciseCategory) {
        viewModelScope.launch {
            exerciseRepository.addCustomExercise(
                Exercise(
                    name = name,
                    primaryMuscleGroup = muscleGroup,
                    category = category,
                    isCustom = true
                )
            )
            showAddDialog.value = false
        }
    }

    fun deleteCustomExercise(id: Long) {
        viewModelScope.launch {
            exerciseRepository.deleteCustomExercise(id)
        }
    }
}
