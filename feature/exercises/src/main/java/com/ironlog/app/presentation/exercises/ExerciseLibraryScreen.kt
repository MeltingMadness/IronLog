package com.ironlog.app.presentation.exercises

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.ironlog.core.designsystem.R
import com.ironlog.app.domain.model.ExerciseCategory
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.presentation.common.EmptyStateScreen
import com.ironlog.app.presentation.common.LoadingScreen
import com.ironlog.app.presentation.theme.ironLogDimens
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExerciseLibraryScreen(
    onExerciseClick: (Long) -> Unit,
    viewModel: ExerciseLibraryViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dims = ironLogDimens

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(id = R.string.exercises_title)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::onShowAddDialog) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.exercises_add_cd)
                )
            }
        }
    ) { padding ->
        if (state.isLoading) {
            LoadingScreen(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
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

        if (state.showAddDialog) {
            AddExerciseDialog(
                onDismiss = viewModel::onDismissAddDialog,
                onConfirm = { name, group, category ->
                    viewModel.addCustomExercise(name, group, category)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SwipeToDeleteExerciseItem(
    exercise: com.ironlog.app.domain.model.Exercise,
    onClick: () -> Unit,
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(end = dims.spacingLg),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(id = R.string.common_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        ListItem(
            headlineContent = { Text(exercise.name) },
            supportingContent = {
                Text("${exercise.primaryMuscleGroup.displayName} • ${exercise.category.displayName}")
            },
            trailingContent = {
                Text(
                    text = stringResource(id = R.string.exercises_custom_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
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
private fun AddExerciseDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, MuscleGroup, ExerciseCategory) -> Unit
) {
    val dims = ironLogDimens
    var name by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf(MuscleGroup.BRUST) }
    var selectedCategory by remember { mutableStateOf(ExerciseCategory.LANGHANTEL) }
    var groupExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.exercises_new_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(dims.spacingSm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(id = R.string.exercises_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = groupExpanded,
                    onExpandedChange = { groupExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedGroup.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(id = R.string.exercises_muscle_group_label)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = groupExpanded,
                        onDismissRequest = { groupExpanded = false }
                    ) {
                        MuscleGroup.entries.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.displayName) },
                                onClick = {
                                    selectedGroup = group
                                    groupExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(id = R.string.exercises_category_label)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) onConfirm(name, selectedGroup, selectedCategory)
                },
                enabled = name.isNotBlank()
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
