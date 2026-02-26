package com.ironlog.app.presentation.plans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.core.designsystem.R
import com.ironlog.app.presentation.common.IronLogScreenScaffold
import com.ironlog.app.presentation.common.IronLogSurfaceCard
import com.ironlog.app.presentation.common.IronLogSurfaceTone
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.app.presentation.workout.ExercisePickerSheet
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditorScreen(
    onBack: () -> Unit,
    viewModel: PlanEditorViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val dims = ironLogDimens

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onBack()
    }

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
                title = {
                    Text(
                        if (viewModel.isEditMode) {
                            stringResource(id = R.string.plan_editor_title_edit)
                        } else {
                            stringResource(id = R.string.plan_editor_title_new)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.nav_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::savePlan) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(id = R.string.plan_editor_save_cd)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = dims.spacingMd),
            verticalArrangement = Arrangement.spacedBy(dims.spacingSm)
        ) {
            item {
                Spacer(modifier = Modifier.height(dims.spacing2))
                com.ironlog.app.presentation.common.IronLogTextField(
                    value = state.planName,
                    onValueChange = viewModel::updatePlanName,
                    label = { Text(stringResource(id = R.string.plan_editor_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                TextButton(
                    onClick = viewModel::showExercisePicker,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(id = R.string.plan_editor_add_exercise))
                }
            }

            itemsIndexed(state.exercises) { index, item ->
                PlanExerciseCard(
                    item = item,
                    index = index,
                    isFirst = index == 0,
                    isLast = index == state.exercises.size - 1,
                    onGroupWithPrevious = { viewModel.groupWithPrevious(index) },
                    onUngroup = { viewModel.ungroup(index) },
                    onMoveUp = { viewModel.moveUp(index) },
                    onMoveDown = { viewModel.moveDown(index) },
                    onRemove = { viewModel.removeExercise(index) },
                    onSetsChange = { viewModel.updateTargetSets(index, it) },
                    onRepsChange = { viewModel.updateTargetReps(index, it) },
                    onWeightChange = { viewModel.updateTargetWeight(index, it) }
                )
            }

            item { Spacer(modifier = Modifier.height(dims.spacingXl)) }
        }

        if (state.showExercisePicker) {
            ExercisePickerSheet(
                onDismiss = viewModel::dismissExercisePicker,
                onExerciseSelected = { exercise ->
                    viewModel.addExercise(exercise)
                },
                allowCreateCustomExercise = true,
                onCreationError = viewModel::onPickerError
            )
        }
    }
}

@Composable
private fun PlanExerciseCard(
    item: PlanExerciseUi,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onGroupWithPrevious: () -> Unit,
    onUngroup: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onSetsChange: (Int) -> Unit,
    onRepsChange: (Int) -> Unit,
    onWeightChange: (Double) -> Unit
) {
    val dims = ironLogDimens
    val supersetGroupId = item.planExercise.supersetGroupId

    IronLogSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        tone = IronLogSurfaceTone.MUTED,
        alpha = 0.68f,
        border = if (supersetGroupId != null) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        } else {
            null
        }
    ) {
        Column(modifier = Modifier.padding(dims.spacingSm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.plan_editor_exercise_indexed, index + 1, item.exercise.name),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Row {
                    IconButton(onClick = onMoveUp, enabled = !isFirst) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropUp,
                            contentDescription = stringResource(id = R.string.plan_editor_move_up_cd)
                        )
                    }
                    IconButton(onClick = onMoveDown, enabled = !isLast) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(id = R.string.plan_editor_move_down_cd)
                        )
                    }
                    IconButton(onClick = onRemove) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(id = R.string.plan_editor_remove_cd),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(dims.spacing2))

            SupersetStatusBadge(supersetGroupId = supersetGroupId)

            Spacer(modifier = Modifier.height(dims.spacing2))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dims.spacingXs)
            ) {
                TextButton(
                    onClick = onGroupWithPrevious,
                    enabled = !isFirst
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(dims.spacing2))
                    Text(stringResource(id = R.string.plan_editor_group_with_previous))
                }
                if (supersetGroupId != null) {
                    TextButton(onClick = onUngroup) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(dims.spacing2))
                        Text(stringResource(id = R.string.plan_editor_ungroup))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dims.spacingXs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.ironlog.app.presentation.common.IronLogTextField(
                    value = if (item.planExercise.targetSets > 0) item.planExercise.targetSets.toString() else "",
                    onValueChange = { it.toIntOrNull()?.let(onSetsChange) },
                    label = { Text(stringResource(id = R.string.plan_editor_sets_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(80.dp),
                    singleLine = true
                )
                com.ironlog.app.presentation.common.IronLogTextField(
                    value = if (item.planExercise.targetReps > 0) item.planExercise.targetReps.toString() else "",
                    onValueChange = { it.toIntOrNull()?.let(onRepsChange) },
                    label = { Text(stringResource(id = R.string.common_reps_short)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(80.dp),
                    singleLine = true
                )
                com.ironlog.app.presentation.common.IronLogTextField(
                    value = if (item.planExercise.targetWeightKg > 0) item.planExercise.targetWeightKg.toString() else "",
                    onValueChange = { it.toDoubleOrNull()?.let(onWeightChange) },
                    label = { Text(stringResource(id = R.string.common_unit_kg)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(90.dp),
                    singleLine = true
                )
            }
        }
    }
}

@Composable
private fun SupersetStatusBadge(supersetGroupId: Int?) {
    val dims = ironLogDimens
    val isGrouped = supersetGroupId != null
    Surface(
        color = if (isGrouped) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (isGrouped) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = MaterialTheme.shapes.large
    ) {
        Text(
            text = if (supersetGroupId == null) {
                stringResource(id = R.string.plan_editor_superset_none)
            } else {
                stringResource(id = R.string.plan_editor_superset_label, supersetGroupId)
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = dims.spacingSm, vertical = dims.spacing2)
        )
    }
}


