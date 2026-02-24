package com.ironlog.app.presentation.workout

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.ExerciseCategory
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.domain.repository.ExerciseRepository
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.core.designsystem.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private data class ExerciseCreateState(
    val name: String = "",
    val primaryMuscleGroup: MuscleGroup = MuscleGroup.BRUST,
    val secondaryMuscleGroups: Set<MuscleGroup> = emptySet(),
    val category: ExerciseCategory = ExerciseCategory.LANGHANTEL,
    val notes: String = ""
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
@Composable
fun ExercisePickerSheet(
    onDismiss: () -> Unit,
    onExerciseSelected: (Exercise) -> Unit,
    allowCreateCustomExercise: Boolean = false,
    onCreationError: ((String) -> Unit)? = null,
    exerciseRepository: ExerciseRepository = koinInject()
) {
    val dims = ironLogDimens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<MuscleGroup?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createState by remember { mutableStateOf(ExerciseCreateState()) }

    val queryFlow = remember { MutableStateFlow("") }
    val groupFlow = remember { MutableStateFlow<MuscleGroup?>(null) }

    LaunchedEffect(searchQuery) {
        queryFlow.emit(searchQuery)
    }
    LaunchedEffect(selectedGroup) {
        groupFlow.emit(selectedGroup)
    }

    val exercisesFlow = remember(exerciseRepository, queryFlow, groupFlow, onCreationError) {
        combine(queryFlow, groupFlow) { query, group -> query to group }
            .flatMapLatest { (query, group) ->
                when {
                    query.isNotBlank() -> exerciseRepository.searchExercises(query)
                    group != null -> exerciseRepository.getExercisesByMuscleGroup(group)
                    else -> exerciseRepository.getAllExercises()
                }
            }.map { exercises ->
                exercises.filterNot { it.isArchived }
            }.catch { throwable ->
                onCreationError?.invoke(
                    "Uebungen konnten nicht geladen werden: ${throwable.message ?: "Unbekannter Fehler"}"
                )
                emit(emptyList())
            }
    }
    val exercises by exercisesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(bottom = dims.spacingMd)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(id = R.string.workout_picker_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dims.spacingMd),
                singleLine = true
            )

            if (allowCreateCustomExercise) {
                TextButton(
                    onClick = {
                        createState = ExerciseCreateState()
                        showCreateDialog = true
                    },
                    modifier = Modifier.padding(horizontal = dims.spacingMd)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(
                        text = stringResource(id = R.string.workout_picker_create_custom),
                        modifier = Modifier.padding(start = dims.spacingXs)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = dims.spacingMd, vertical = dims.spacingXs),
                horizontalArrangement = Arrangement.spacedBy(dims.spacingXs)
            ) {
                FilterChip(
                    selected = selectedGroup == null,
                    onClick = { selectedGroup = null },
                    label = { Text(stringResource(id = R.string.common_all)) }
                )
                MuscleGroup.entries.forEach { group ->
                    FilterChip(
                        selected = selectedGroup == group,
                        onClick = { selectedGroup = group },
                        label = { Text(group.displayName) }
                    )
                }
            }

            LazyColumn {
                items(exercises, key = { it.id }) { exercise ->
                    ListItem(
                        headlineContent = { Text(exercise.name) },
                        supportingContent = {
                            Text("${exercise.primaryMuscleGroup.displayName} • ${exercise.category.displayName}")
                        },
                        modifier = Modifier
                            .clickable { onExerciseSelected(exercise) }
                            .padding(horizontal = dims.spacing2)
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateExerciseDialog(
            state = createState,
            onStateChange = { createState = it },
            onDismiss = { showCreateDialog = false },
            onConfirm = {
                scope.launch {
                    runCatching {
                        val exerciseId = exerciseRepository.addCustomExercise(
                            Exercise(
                                name = createState.name,
                                primaryMuscleGroup = createState.primaryMuscleGroup,
                                secondaryMuscleGroups = createState.secondaryMuscleGroups.toList(),
                                category = createState.category,
                                isCustom = true,
                                notes = createState.notes
                            )
                        )
                        exerciseRepository.getExerciseById(exerciseId) ?: Exercise(
                            id = exerciseId,
                            name = createState.name.trim(),
                            primaryMuscleGroup = createState.primaryMuscleGroup,
                            secondaryMuscleGroups = createState.secondaryMuscleGroups.toList(),
                            category = createState.category,
                            isCustom = true,
                            notes = createState.notes.trim()
                        )
                    }.onSuccess { exercise ->
                        showCreateDialog = false
                        onExerciseSelected(exercise)
                    }.onFailure { throwable ->
                        onCreationError?.invoke(
                            throwable.message ?: "Uebung konnte nicht erstellt werden"
                        )
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateExerciseDialog(
    state: ExerciseCreateState,
    onStateChange: (ExerciseCreateState) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val dims = ironLogDimens
    var groupExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.exercises_new_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(dims.spacingSm)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { onStateChange(state.copy(name = it)) },
                    label = { Text(stringResource(id = R.string.exercises_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Box {
                    OutlinedTextField(
                        value = state.primaryMuscleGroup.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(id = R.string.exercises_muscle_group_label)) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { groupExpanded = true }
                    )
                    DropdownMenu(
                        expanded = groupExpanded,
                        onDismissRequest = { groupExpanded = false }
                    ) {
                        MuscleGroup.entries.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.displayName) },
                                onClick = {
                                    onStateChange(
                                        state.copy(
                                            primaryMuscleGroup = group,
                                            secondaryMuscleGroups = state.secondaryMuscleGroups - group
                                        )
                                    )
                                    groupExpanded = false
                                }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(dims.spacingXs)) {
                    Text(
                        text = stringResource(id = R.string.exercises_secondary_muscle_groups_label)
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(dims.spacingXs)
                    ) {
                        MuscleGroup.entries
                            .filter { it != state.primaryMuscleGroup }
                            .forEach { group ->
                                val selected = group in state.secondaryMuscleGroups
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        val next = when {
                                            selected -> state.secondaryMuscleGroups - group
                                            state.secondaryMuscleGroups.size < 3 -> state.secondaryMuscleGroups + group
                                            else -> state.secondaryMuscleGroups
                                        }
                                        onStateChange(state.copy(secondaryMuscleGroups = next))
                                    },
                                    label = { Text(group.displayName) }
                                )
                            }
                    }
                    Text(
                        text = stringResource(id = R.string.exercises_secondary_muscle_groups_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    OutlinedTextField(
                        value = state.category.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(id = R.string.exercises_category_label)) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { categoryExpanded = true }
                    )
                    DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        ExerciseCategory.entries.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.displayName) },
                                onClick = {
                                    onStateChange(state.copy(category = category))
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = state.notes,
                    onValueChange = { onStateChange(state.copy(notes = it)) },
                    label = { Text(stringResource(id = R.string.exercises_notes_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = state.name.isNotBlank()
            ) {
                Text(stringResource(id = R.string.common_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.common_cancel))
            }
        }
    )
}
