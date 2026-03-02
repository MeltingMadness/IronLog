package com.ironlog.app.presentation.exercises

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.ExerciseCategory
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.presentation.common.EmptyStateScreen
import com.ironlog.app.presentation.common.IronLogScreenScaffold
import com.ironlog.app.presentation.common.LoadingScreen
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.core.designsystem.R
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExerciseLibraryScreen(
    onExerciseClick: (Long) -> Unit,
    viewModel: ExerciseLibraryViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dims = ironLogDimens
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    IronLogScreenScaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                title = { Text(stringResource(id = R.string.exercises_title)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::onShowAddDialog) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.exercises_add_cd)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading) {
            LoadingScreen(modifier = Modifier.padding(padding))
            return@IronLogScreenScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            com.ironlog.app.presentation.common.IronLogTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                label = { Text(stringResource(id = R.string.exercises_search_label)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dims.spacingMd, vertical = dims.spacingXs),
                singleLine = true
            )

            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = dims.spacingMd),
                horizontalArrangement = Arrangement.spacedBy(dims.spacingXs)
            ) {
                FilterChip(
                    selected = state.selectedMuscleGroup == null,
                    onClick = { viewModel.onMuscleGroupSelected(null) },
                    label = { Text(stringResource(id = R.string.common_all)) }
                )
                MuscleGroup.entries.forEach { group ->
                    FilterChip(
                        selected = state.selectedMuscleGroup == group,
                        onClick = { viewModel.onMuscleGroupSelected(group) },
                        label = { Text(group.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(dims.spacingXs))

            if (state.exercises.isEmpty()) {
                EmptyStateScreen(
                    title = stringResource(id = R.string.exercises_empty),
                    subtitle = "",
                    icon = Icons.Default.SearchOff
                )
            } else {
                var deleteExerciseId by remember { mutableStateOf<Long?>(null) }
                var deleteExerciseName by remember { mutableStateOf("") }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.exercises, key = { it.id }) { exercise ->
                        if (exercise.isCustom) {
                            SwipeToDeleteExerciseItem(
                                exercise = exercise,
                                onClick = { onExerciseClick(exercise.id) },
                                onEdit = { viewModel.onShowEditDialog(exercise) },
                                onDelete = {
                                    deleteExerciseId = exercise.id
                                    deleteExerciseName = exercise.name
                                }
                            )
                        } else {
                            ListItem(
                                headlineContent = { Text(exercise.name) },
                                supportingContent = {
                                    Text("${exercise.primaryMuscleGroup.displayName} • ${exercise.category.displayName}")
                                },
                                modifier = Modifier.combinedClickable(
                                    onClick = { onExerciseClick(exercise.id) }
                                )
                            )
                        }
                    }
                }

                deleteExerciseId?.let { id ->
                    AlertDialog(
                        onDismissRequest = { deleteExerciseId = null },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        title = { Text(stringResource(id = R.string.exercises_delete_title)) },
                        text = {
                            Text(
                                text = stringResource(
                                    id = R.string.exercises_delete_text,
                                    deleteExerciseName
                                )
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.deleteCustomExercise(id)
                                deleteExerciseId = null
                            }) {
                                Text(
                                    text = stringResource(id = R.string.common_delete),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { deleteExerciseId = null }) {
                                Text(stringResource(id = R.string.common_cancel))
                            }
                        }
                    )
                }
            }
        }

        state.editor?.let { editor ->
            CustomExerciseDialog(
                initial = editor,
                onDismiss = viewModel::onDismissExerciseDialog,
                onConfirm = { name, primary, secondary, category, notes ->
                    viewModel.saveCustomExercise(
                        id = editor.id,
                        name = name,
                        primaryMuscleGroup = primary,
                        secondaryMuscleGroups = secondary,
                        category = category,
                        notes = notes
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SwipeToDeleteExerciseItem(
    exercise: Exercise,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dims = ironLogDimens
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val progress = dismissState.progress
            if (dismissState.targetValue == SwipeToDismissBoxValue.Settled && dismissState.currentValue == SwipeToDismissBoxValue.Settled) {
                Box(modifier = Modifier.fillMaxSize())
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = progress))
                        .padding(end = dims.spacingLg),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(id = R.string.common_delete),
                        tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = progress)
                    )
                }
            }
        }
    ) {
        ListItem(
            headlineContent = { Text(exercise.name) },
            supportingContent = {
                val notesText = exercise.notes.takeIf { it.isNotBlank() }
                if (notesText == null) {
                    Text("${exercise.primaryMuscleGroup.displayName} • ${exercise.category.displayName}")
                } else {
                    Text("${exercise.primaryMuscleGroup.displayName} • ${exercise.category.displayName} • $notesText")
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(id = R.string.exercises_custom_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(id = R.string.exercises_edit_cd)
                        )
                    }
                }
            },
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onDelete
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomExerciseDialog(
    initial: ExerciseEditorState,
    onDismiss: () -> Unit,
    onConfirm: (name: String, primary: MuscleGroup, secondary: List<MuscleGroup>, category: ExerciseCategory, notes: String) -> Unit
) {
    val dims = ironLogDimens
    var name by remember(initial.id, initial.name) { mutableStateOf(initial.name) }
    var selectedPrimary by remember(initial.id, initial.primaryMuscleGroup) {
        mutableStateOf(initial.primaryMuscleGroup)
    }
    var selectedSecondary by remember(initial.id, initial.secondaryMuscleGroups) {
        mutableStateOf(initial.secondaryMuscleGroups - initial.primaryMuscleGroup)
    }
    var selectedCategory by remember(initial.id, initial.category) { mutableStateOf(initial.category) }
    var notes by remember(initial.id, initial.notes) { mutableStateOf(initial.notes) }
    var groupExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        title = {
            Text(
                if (initial.isEditMode) {
                    stringResource(id = R.string.exercises_edit_title)
                } else {
                    stringResource(id = R.string.exercises_new_title)
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(dims.spacingSm)) {
                com.ironlog.app.presentation.common.IronLogTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(id = R.string.exercises_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Box {
                    com.ironlog.app.presentation.common.IronLogTextField(
                        value = selectedPrimary.displayName,
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
                                    selectedPrimary = group
                                    selectedSecondary = selectedSecondary - group
                                    groupExpanded = false
                                }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(dims.spacingXs)) {
                    Text(
                        text = stringResource(id = R.string.exercises_secondary_muscle_groups_label),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(dims.spacingXs)
                    ) {
                        MuscleGroup.entries
                            .filter { it != selectedPrimary }
                            .forEach { group ->
                                val isSelected = group in selectedSecondary
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedSecondary = when {
                                            isSelected -> selectedSecondary - group
                                            selectedSecondary.size < 3 -> selectedSecondary + group
                                            else -> selectedSecondary
                                        }
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
                    com.ironlog.app.presentation.common.IronLogTextField(
                        value = selectedCategory.displayName,
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
                                    selectedCategory = category
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                com.ironlog.app.presentation.common.IronLogTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(id = R.string.exercises_notes_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        name.trim(),
                        selectedPrimary,
                        selectedSecondary.toList(),
                        selectedCategory,
                        notes.trim()
                    )
                },
                enabled = name.isNotBlank()
            ) {
                Text(
                    if (initial.isEditMode) {
                        stringResource(id = R.string.exercises_save_cd)
                    } else {
                        stringResource(id = R.string.common_create)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.common_cancel))
            }
        }
    )
}


