package com.ironlog.app.presentation.plans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
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
import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.ProgressionScheme
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.util.WeightFormatting
import com.ironlog.app.presentation.common.EmptyStateScreen
import com.ironlog.app.presentation.common.IronLogScreenScaffold
import com.ironlog.app.presentation.common.IronLogSurfaceCard
import com.ironlog.app.presentation.common.IronLogSurfaceTone
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.app.presentation.workout.ExercisePickerSheet
import com.ironlog.app.presentation.workout.parseDecimal
import org.koin.androidx.compose.koinViewModel
import java.math.BigDecimal

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
        if (state.notFound) {
            EmptyStateScreen(
                title = stringResource(id = R.string.plan_editor_not_found_title),
                subtitle = stringResource(id = R.string.plan_editor_not_found_subtitle),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                action = {
                    TextButton(onClick = onBack) {
                        Text(text = stringResource(id = R.string.nav_back))
                    }
                }
            )
            return@IronLogScreenScaffold
        }

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
                    onWeightChange = { viewModel.updateTargetWeightDisplay(index, it) },
                    unitSystem = state.unitSystem,
                    onOpenProgression = { viewModel.openProgressionEditor(index) }
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

        state.progressionEditor?.let { draft ->
            ProgressionEditorSheet(
                draft = draft,
                onSchemeSelected = viewModel::selectProgressionScheme,
                onFieldChanged = viewModel::updateProgressionField,
                onDismiss = viewModel::dismissProgressionEditor,
                onApply = viewModel::saveProgressionEditor
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
    onWeightChange: (Double) -> Unit,
    unitSystem: UnitSystem,
    onOpenProgression: () -> Unit
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
                    IconButton(
                        onClick = onMoveUp,
                        enabled = !isFirst,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropUp,
                            contentDescription = stringResource(id = R.string.plan_editor_move_up_cd),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = !isLast,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(id = R.string.plan_editor_move_down_cd),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(id = R.string.plan_editor_remove_cd),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
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
                    TextButton(
                        onClick = onUngroup,
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null
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
                    value = if (item.planExercise.targetWeightKg > 0) {
                        editableNumber(
                            WeightFormatting.convertToDisplay(
                                item.planExercise.targetWeightKg,
                                unitSystem
                            )
                        )
                    } else {
                        ""
                    },
                    onValueChange = { parseDecimal(it)?.let(onWeightChange) },
                    label = {
                        Text(
                            stringResource(
                                id = R.string.plan_editor_weight_label,
                                WeightFormatting.unitLabel(unitSystem)
                            )
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(90.dp),
                    singleLine = true
                )
            }

            TextButton(
                onClick = onOpenProgression,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(progressionSummary(item.planExercise.progressionConfig))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressionEditorSheet(
    draft: ProgressionEditorUi,
    onSchemeSelected: (ProgressionScheme) -> Unit,
    onFieldChanged: (ProgressionField, String) -> Unit,
    onDismiss: () -> Unit,
    onApply: () -> Unit
) {
    val dims = ironLogDimens
    val schemes = listOf(
        ProgressionScheme.MANUAL,
        ProgressionScheme.LINEAR,
        ProgressionScheme.DOUBLE,
        ProgressionScheme.TOTAL_REPS,
        ProgressionScheme.RPE_RIR
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dims.spacingMd, vertical = dims.spacingSm),
            verticalArrangement = Arrangement.spacedBy(dims.spacingSm)
        ) {
            Text(
                text = stringResource(id = R.string.plan_editor_progression_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            schemes.forEach { scheme ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSchemeSelected(scheme) }
                        .padding(vertical = dims.spacing2),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = draft.scheme == scheme,
                        onClick = { onSchemeSelected(scheme) }
                    )
                    Spacer(modifier = Modifier.width(dims.spacingXs))
                    Text(progressionSchemeName(scheme))
                }
            }

            if (draft.scheme != ProgressionScheme.MANUAL) {
                ProgressionInput(
                    value = draft.step,
                    onValueChange = { onFieldChanged(ProgressionField.STEP, it) },
                    label = stringResource(
                        id = R.string.plan_editor_progression_step,
                        WeightFormatting.unitLabel(draft.unitSystem)
                    ),
                    field = ProgressionField.STEP,
                    errors = draft.errors,
                    keyboardType = KeyboardType.Decimal
                )

                when (draft.scheme) {
                    ProgressionScheme.DOUBLE -> {
                        ProgressionInput(
                            value = draft.minReps,
                            onValueChange = { onFieldChanged(ProgressionField.MIN_REPS, it) },
                            label = stringResource(id = R.string.plan_editor_progression_min_reps),
                            field = ProgressionField.MIN_REPS,
                            errors = draft.errors,
                            keyboardType = KeyboardType.Number
                        )
                        ProgressionInput(
                            value = draft.maxReps,
                            onValueChange = { onFieldChanged(ProgressionField.MAX_REPS, it) },
                            label = stringResource(id = R.string.plan_editor_progression_max_reps),
                            field = ProgressionField.MAX_REPS,
                            errors = draft.errors,
                            keyboardType = KeyboardType.Number
                        )
                    }
                    ProgressionScheme.TOTAL_REPS -> ProgressionInput(
                        value = draft.totalReps,
                        onValueChange = { onFieldChanged(ProgressionField.TOTAL_REPS, it) },
                        label = stringResource(id = R.string.plan_editor_progression_total_reps),
                        field = ProgressionField.TOTAL_REPS,
                        errors = draft.errors,
                        keyboardType = KeyboardType.Number
                    )
                    ProgressionScheme.RPE_RIR -> {
                        ProgressionInput(
                            value = draft.targetRpe,
                            onValueChange = { onFieldChanged(ProgressionField.TARGET_RPE, it) },
                            label = stringResource(id = R.string.plan_editor_progression_target_rpe),
                            field = ProgressionField.TARGET_RPE,
                            errors = draft.errors,
                            keyboardType = KeyboardType.Decimal
                        )
                        ProgressionInput(
                            value = draft.rpeTolerance,
                            onValueChange = { onFieldChanged(ProgressionField.RPE_TOLERANCE, it) },
                            label = stringResource(id = R.string.plan_editor_progression_rpe_tolerance),
                            field = ProgressionField.RPE_TOLERANCE,
                            errors = draft.errors,
                            keyboardType = KeyboardType.Decimal
                        )
                    }
                    ProgressionScheme.MANUAL,
                    ProgressionScheme.LINEAR -> Unit
                }

                ProgressionInput(
                    value = draft.stallThreshold,
                    onValueChange = { onFieldChanged(ProgressionField.STALL_THRESHOLD, it) },
                    label = stringResource(id = R.string.plan_editor_progression_stall_threshold),
                    field = ProgressionField.STALL_THRESHOLD,
                    errors = draft.errors,
                    keyboardType = KeyboardType.Number
                )
                ProgressionInput(
                    value = draft.backoffPercent,
                    onValueChange = { onFieldChanged(ProgressionField.BACKOFF_PERCENT, it) },
                    label = stringResource(id = R.string.plan_editor_progression_backoff_percent),
                    field = ProgressionField.BACKOFF_PERCENT,
                    errors = draft.errors,
                    keyboardType = KeyboardType.Decimal
                )
            }

            Text(
                text = progressionPreview(draft),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.common_cancel))
                }
                Spacer(modifier = Modifier.width(dims.spacingXs))
                Button(onClick = onApply) {
                    Text(stringResource(id = R.string.plan_editor_progression_apply))
                }
            }
            Spacer(modifier = Modifier.height(dims.spacingSm))
        }
    }
}

@Composable
private fun ProgressionInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    field: ProgressionField,
    errors: Map<ProgressionField, String>,
    keyboardType: KeyboardType
) {
    val dims = ironLogDimens
    Column(modifier = Modifier.fillMaxWidth()) {
        com.ironlog.app.presentation.common.IronLogTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        if (errors.containsKey(field)) {
            Spacer(modifier = Modifier.height(dims.spacing2))
            Text(
                text = progressionError(field),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun progressionSummary(config: ProgressionConfig): String {
    val status = when (config) {
        is ProgressionConfig.Manual -> stringResource(id = R.string.plan_editor_progression_off)
        is ProgressionConfig.Invalid -> stringResource(id = R.string.plan_editor_progression_invalid)
        else -> progressionSchemeName(config.scheme)
    }
    return stringResource(id = R.string.plan_editor_progression_summary, status)
}

@Composable
private fun progressionSchemeName(scheme: ProgressionScheme): String = stringResource(
    id = when (scheme) {
        ProgressionScheme.MANUAL -> R.string.plan_editor_progression_scheme_manual
        ProgressionScheme.LINEAR -> R.string.plan_editor_progression_scheme_linear
        ProgressionScheme.DOUBLE -> R.string.plan_editor_progression_scheme_double
        ProgressionScheme.TOTAL_REPS -> R.string.plan_editor_progression_scheme_total_reps
        ProgressionScheme.RPE_RIR -> R.string.plan_editor_progression_scheme_rpe_rir
    }
)

@Composable
private fun progressionPreview(draft: ProgressionEditorUi): String {
    val unit = WeightFormatting.unitLabel(draft.unitSystem)
    return when (draft.scheme) {
        ProgressionScheme.MANUAL -> stringResource(id = R.string.plan_editor_progression_preview_manual)
        ProgressionScheme.LINEAR -> stringResource(
            id = R.string.plan_editor_progression_preview_linear,
            draft.step,
            unit
        )
        ProgressionScheme.DOUBLE -> stringResource(
            id = R.string.plan_editor_progression_preview_double,
            draft.minReps,
            draft.maxReps,
            draft.step,
            unit
        )
        ProgressionScheme.TOTAL_REPS -> stringResource(
            id = R.string.plan_editor_progression_preview_total_reps,
            draft.totalReps,
            draft.step,
            unit
        )
        ProgressionScheme.RPE_RIR -> stringResource(
            id = R.string.plan_editor_progression_preview_rpe_rir,
            draft.targetRpe,
            draft.rpeTolerance,
            draft.step,
            unit
        )
    }
}

@Composable
private fun progressionError(field: ProgressionField): String = stringResource(
    id = when (field) {
        ProgressionField.STEP -> R.string.plan_editor_progression_error_step
        ProgressionField.MIN_REPS -> R.string.plan_editor_progression_error_min_reps
        ProgressionField.MAX_REPS -> R.string.plan_editor_progression_error_max_reps
        ProgressionField.TOTAL_REPS -> R.string.plan_editor_progression_error_total_reps
        ProgressionField.TARGET_RPE -> R.string.plan_editor_progression_error_target_rpe
        ProgressionField.RPE_TOLERANCE -> R.string.plan_editor_progression_error_rpe_tolerance
        ProgressionField.STALL_THRESHOLD -> R.string.plan_editor_progression_error_stall_threshold
        ProgressionField.BACKOFF_PERCENT -> R.string.plan_editor_progression_error_backoff_percent
    }
)

private fun editableNumber(value: Double): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

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

