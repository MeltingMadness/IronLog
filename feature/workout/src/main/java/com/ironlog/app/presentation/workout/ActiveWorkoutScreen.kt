package com.ironlog.app.presentation.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.core.designsystem.R
import com.ironlog.app.domain.model.AppPreferences
import com.ironlog.app.domain.model.IntensitySystem
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.presentation.common.HapticFeedbackHelper
import com.ironlog.app.presentation.common.IronLogScreenScaffold
import com.ironlog.app.presentation.common.IronLogSurfaceCard
import com.ironlog.app.presentation.common.IronLogSurfaceTone
import com.ironlog.app.presentation.common.LoadingScreen
import com.ironlog.app.presentation.common.SetInputRow
import com.ironlog.app.presentation.common.WorkoutTimer
import com.ironlog.app.presentation.common.rememberHapticFeedback
import com.ironlog.app.presentation.theme.ironLogDimens
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private data class ExerciseRenderGroup(
    val key: String,
    val supersetGroupId: Int?,
    val exercises: List<ExerciseWithSets>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    onWorkoutFinished: () -> Unit,
    viewModel: ActiveWorkoutViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val appPreferencesRepository: AppPreferencesRepository = koinInject()
    val preferences by appPreferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = AppPreferences()
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val dims = ironLogDimens
    val haptic = rememberHapticFeedback()

    var quickSelectedExerciseId by remember { mutableStateOf<Long?>(null) }
    val exerciseGroups = remember(state.exercisesWithSets) {
        buildExerciseRenderGroups(state.exercisesWithSets)
    }

    LaunchedEffect(state.exercisesWithSets) {
        val currentIds = state.exercisesWithSets.map { it.exercise.id }.toSet()
        if (quickSelectedExerciseId == null || quickSelectedExerciseId !in currentIds) {
            quickSelectedExerciseId = state.exercisesWithSets.firstOrNull()?.exercise?.id
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WorkoutEvent.NewRecord -> {
                    haptic.confirm()
                    snackbarHostState.showSnackbar(
                        message = context.getString(
                            R.string.workout_new_record_message,
                            event.exerciseName,
                            event.type.displayName
                        )
                    )
                }
            }
        }
    }

    LaunchedEffect(state.session?.endTime) {
        if (state.session?.endTime != null) {
            onWorkoutFinished()
        }
    }

    IronLogScreenScaffold(
        topBar = {
            TopAppBar(
                title = {
                    val name = state.session?.name?.takeIf { it.isNotBlank() }
                        ?: stringResource(id = R.string.workout_title_default)
                    Text(name)
                },
                actions = {
                    TextButton(onClick = viewModel::showFinishDialog) {
                        Text(
                            text = stringResource(id = R.string.workout_finish_action),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        if (state.session == null && state.error == null) {
            LoadingScreen(modifier = Modifier.padding(padding))
            return@IronLogScreenScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            state.session?.let { session ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dims.spacingMd, vertical = dims.spacingXs),
                    horizontalArrangement = Arrangement.Center
                ) {
                    WorkoutTimer(startTime = session.startTime)
                }
            }

            Button(
                onClick = viewModel::showExercisePicker,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dims.spacingMd, vertical = dims.spacingXs)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(
                    text = stringResource(id = R.string.workout_add_exercise),
                    modifier = Modifier.padding(start = dims.spacingXs)
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(dims.spacingSm),
                contentPadding = PaddingValues(dims.spacingMd)
            ) {
                items(exerciseGroups, key = { it.key }) { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(dims.spacingXs)) {
                        group.supersetGroupId?.let { supersetGroupId ->
                            SupersetHeader(
                                groupId = supersetGroupId,
                                exerciseCount = group.exercises.size,
                                exerciseNames = group.exercises.joinToString(separator = " • ") {
                                    it.exercise.name
                                }
                            )
                        }
                        group.exercises.forEach { exerciseWithSets ->
                            ExerciseCard(
                                exerciseWithSets = exerciseWithSets,
                                defaultWarmupFlag = preferences.defaultWarmupFlag,
                                intensitySystem = preferences.intensitySystem,
                                onLogSet = { reps, weight, isWarmup, intensity ->
                                    viewModel.logSet(
                                        exerciseWithSets.exercise.id,
                                        reps,
                                        weight,
                                        isWarmup,
                                        intensity
                                    )
                                },
                                onDeleteSet = viewModel::deleteSet,
                                haptic = haptic
                            )
                        }
                    }
                }
            }

            QuickLogComposer(
                exercisesWithSets = state.exercisesWithSets,
                selectedExerciseId = quickSelectedExerciseId,
                onSelectExercise = { quickSelectedExerciseId = it },
                defaultWarmupFlag = preferences.defaultWarmupFlag,
                intensitySystem = preferences.intensitySystem,
                onLogSet = { exerciseId, reps, weight, isWarmup, intensity ->
                    viewModel.logSet(exerciseId, reps, weight, isWarmup, intensity)
                },
                haptic = haptic
            )
        }

        if (state.showExercisePicker) {
            ExercisePickerSheet(
                onDismiss = viewModel::dismissExercisePicker,
                onExerciseSelected = { exercise ->
                    viewModel.addExercise(exercise)
                    viewModel.dismissExercisePicker()
                }
            )
        }

        if (state.showFinishDialog) {
            AlertDialog(
                onDismissRequest = viewModel::dismissFinishDialog,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                title = { Text(stringResource(id = R.string.workout_finish_dialog_title)) },
                text = { Text(stringResource(id = R.string.workout_finish_dialog_text)) },
                confirmButton = {
                    TextButton(onClick = viewModel::finishWorkout) {
                        Text(stringResource(id = R.string.workout_finish_dialog_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissFinishDialog) {
                        Text(stringResource(id = R.string.workout_finish_dialog_cancel))
                    }
                }
            )
        }
    }
}

private fun buildExerciseRenderGroups(exercises: List<ExerciseWithSets>): List<ExerciseRenderGroup> {
    if (exercises.isEmpty()) return emptyList()

    val groups = mutableListOf<ExerciseRenderGroup>()
    var cursor = 0
    while (cursor < exercises.size) {
        val groupId = exercises[cursor].supersetGroupId
        if (groupId == null) {
            val item = exercises[cursor]
            groups += ExerciseRenderGroup(
                key = "single-${item.exercise.id}-$cursor",
                supersetGroupId = null,
                exercises = listOf(item)
            )
            cursor++
            continue
        }

        var endExclusive = cursor + 1
        while (
            endExclusive < exercises.size &&
            exercises[endExclusive].supersetGroupId == groupId
        ) {
            endExclusive++
        }

        val run = exercises.subList(cursor, endExclusive)
        if (run.size < 2) {
            val item = exercises[cursor]
            groups += ExerciseRenderGroup(
                key = "single-${item.exercise.id}-$cursor",
                supersetGroupId = null,
                exercises = listOf(item)
            )
        } else {
            groups += ExerciseRenderGroup(
                key = "superset-$groupId-$cursor",
                supersetGroupId = groupId,
                exercises = run
            )
        }

        cursor = endExclusive
    }

    return groups
}

@Composable
private fun SupersetHeader(groupId: Int, exerciseCount: Int, exerciseNames: String) {
    val dims = ironLogDimens
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dims.spacingSm, vertical = dims.spacingXs),
            verticalArrangement = Arrangement.spacedBy(dims.spacing2)
        ) {
            Text(
                text = stringResource(id = R.string.workout_superset_header, groupId, exerciseCount),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = exerciseNames,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickLogComposer(
    exercisesWithSets: List<ExerciseWithSets>,
    selectedExerciseId: Long?,
    onSelectExercise: (Long) -> Unit,
    defaultWarmupFlag: Boolean,
    intensitySystem: IntensitySystem,
    onLogSet: (Long, Int, Double, Boolean, String) -> Unit,
    haptic: HapticFeedbackHelper
) {
    if (exercisesWithSets.isEmpty() || selectedExerciseId == null) return

    val dims = ironLogDimens
    val tracksIntensity = intensitySystem != IntensitySystem.OFF

    var repsInput by remember(selectedExerciseId) { mutableStateOf("") }
    var weightInput by remember(selectedExerciseId) { mutableStateOf("") }
    var intensityInput by remember(selectedExerciseId) { mutableStateOf("") }
    var isWarmup by remember { mutableStateOf(defaultWarmupFlag) }

    IronLogSurfaceCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dims.spacingMd, vertical = dims.spacingXs),
        tone = IronLogSurfaceTone.ELEVATED,
        alpha = 0.7f
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.spacingSm),
            verticalArrangement = Arrangement.spacedBy(dims.spacingXs)
        ) {
            Text(
                text = stringResource(id = R.string.workout_quick_log_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(dims.spacingXs)
            ) {
                exercisesWithSets.forEach { entry ->
                    FilterChip(
                        selected = selectedExerciseId == entry.exercise.id,
                        onClick = { onSelectExercise(entry.exercise.id) },
                        label = { Text(entry.exercise.name) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.spacingXs)
            ) {
                FilterChip(
                    selected = isWarmup,
                    onClick = { isWarmup = !isWarmup },
                    label = { Text(stringResource(id = R.string.workout_warmup_chip)) }
                )
            }

            SetInputRow(
                reps = repsInput,
                onRepsChange = { repsInput = it },
                weight = weightInput,
                onWeightChange = { weightInput = it },
                intensity = intensityInput,
                onIntensityChange = { intensityInput = it },
                intensityLabel = intensitySystem.displayName,
                showIntensityField = tracksIntensity,
                onLog = {
                    val reps = repsInput.toIntOrNull()
                    val weight = weightInput.toDoubleOrNull()
                    if (reps != null && reps > 0 && weight != null && weight >= 0) {
                        onLogSet(
                            selectedExerciseId,
                            reps,
                            weight,
                            isWarmup,
                            if (tracksIntensity) intensityInput else ""
                        )
                        haptic.confirm()
                        repsInput = ""
                        weightInput = ""
                        intensityInput = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ExerciseCard(
    exerciseWithSets: ExerciseWithSets,
    defaultWarmupFlag: Boolean,
    intensitySystem: IntensitySystem,
    onLogSet: (Int, Double, Boolean, String) -> Unit,
    onDeleteSet: (Long) -> Unit,
    haptic: HapticFeedbackHelper
) {
    val dims = ironLogDimens
    val planTarget = exerciseWithSets.planTarget
    val loggedSets = exerciseWithSets.sets.filter { it.reps > 0 }
    val completedWorkSets = loggedSets.count { !it.isWarmup }
    val targetSetCount = planTarget?.targetSets ?: 0

    IronLogSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        tone = IronLogSurfaceTone.MUTED,
        alpha = 0.68f
    ) {
        Column(modifier = Modifier.padding(dims.spacingMd)) {
            Text(
                text = exerciseWithSets.exercise.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (planTarget != null) {
                val targetText = if (planTarget.targetWeightKg > 0) {
                    stringResource(
                        id = R.string.workout_target_with_weight,
                        planTarget.targetSets,
                        planTarget.targetReps,
                        planTarget.targetWeightKg
                    )
                } else {
                    stringResource(
                        id = R.string.workout_target_no_weight,
                        planTarget.targetSets,
                        planTarget.targetReps
                    )
                }
                Text(
                    text = targetText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (completedWorkSets >= planTarget.targetSets) {
                    Text(
                        text = stringResource(id = R.string.workout_target_completed),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(dims.spacingXs))

            if (planTarget != null && targetSetCount > 0) {
                for (setIndex in 1..targetSetCount) {
                    val matchingSet = loggedSets.filter { !it.isWarmup }.getOrNull(setIndex - 1)
                    if (matchingSet != null) {
                        LoggedSetRow(set = matchingSet, onDeleteSet = onDeleteSet, haptic = haptic)
                    } else {
                        PendingSetRow(
                            setNumber = setIndex,
                            defaultReps = planTarget.targetReps.toString(),
                            defaultWeight = if (planTarget.targetWeightKg > 0) {
                                planTarget.targetWeightKg.toString()
                            } else {
                                ""
                            },
                            intensitySystem = intensitySystem,
                            onLog = { reps, weight, intensity -> onLogSet(reps, weight, false, intensity) }
                        )
                    }
                }

                loggedSets.filter { !it.isWarmup }.drop(targetSetCount).forEach { set ->
                    LoggedSetRow(set = set, onDeleteSet = onDeleteSet, haptic = haptic)
                }

                loggedSets.filter { it.isWarmup }.forEach { set ->
                    LoggedSetRow(set = set, onDeleteSet = onDeleteSet, haptic = haptic)
                }

                Spacer(modifier = Modifier.height(dims.spacingXs))

                var showExtraInput by remember { mutableStateOf(false) }
                AnimatedVisibility(visible = showExtraInput) {
                    ExtraSetInput(
                        planTarget = planTarget,
                        defaultWarmupFlag = defaultWarmupFlag,
                        intensitySystem = intensitySystem,
                        onLogSet = onLogSet,
                        haptic = haptic
                    )
                }
                if (!showExtraInput) {
                    TextButton(onClick = { showExtraInput = true }) {
                        Text(stringResource(id = R.string.workout_add_extra_set))
                    }
                }
            } else {
                loggedSets.forEach { set ->
                    LoggedSetRow(set = set, onDeleteSet = onDeleteSet, haptic = haptic)
                }

                Spacer(modifier = Modifier.height(dims.spacingXs))

                ExtraSetInput(
                    planTarget = null,
                    defaultWarmupFlag = defaultWarmupFlag,
                    intensitySystem = intensitySystem,
                    onLogSet = onLogSet,
                    haptic = haptic
                )
            }
        }
    }
}

@Composable
private fun LoggedSetRow(
    set: com.ironlog.app.domain.model.WorkoutSet,
    onDeleteSet: (Long) -> Unit,
    haptic: HapticFeedbackHelper
) {
    val dims = ironLogDimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dims.spacing2),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (set.isWarmup) {
                stringResource(id = R.string.workout_set_row_warmup, set.setNumber, set.weightKg, set.reps)
            } else {
                stringResource(id = R.string.workout_set_row_work, set.setNumber, set.weightKg, set.reps)
            },
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = if (set.isWarmup) FontStyle.Italic else FontStyle.Normal,
            color = if (set.isWarmup) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = { haptic.reject(); onDeleteSet(set.id) }) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(id = R.string.workout_delete_set_cd),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun PendingSetRow(
    setNumber: Int,
    defaultReps: String,
    defaultWeight: String,
    intensitySystem: IntensitySystem,
    onLog: (Int, Double, String) -> Unit
) {
    val dims = ironLogDimens
    val tracksIntensity = intensitySystem != IntensitySystem.OFF
    var repsInput by remember { mutableStateOf(defaultReps) }
    var weightInput by remember { mutableStateOf(defaultWeight) }
    var intensityInput by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dims.spacing2),
        horizontalArrangement = Arrangement.spacedBy(dims.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(id = R.string.workout_set_number, setNumber),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp)
        )
        OutlinedTextField(
            value = weightInput,
            onValueChange = { weightInput = it },
            label = { Text(stringResource(id = R.string.common_unit_kg)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1.1f),
            singleLine = true
        )
        OutlinedTextField(
            value = repsInput,
            onValueChange = { repsInput = it },
            label = { Text(stringResource(id = R.string.common_reps_short)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        if (tracksIntensity) {
            OutlinedTextField(
                value = intensityInput,
                onValueChange = { intensityInput = it },
                label = { Text(intensitySystem.displayName) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        IconButton(
            onClick = {
                val reps = repsInput.toIntOrNull()
                val weight = weightInput.toDoubleOrNull()
                if (reps != null && reps > 0 && weight != null && weight >= 0) {
                    onLog(reps, weight, if (tracksIntensity) intensityInput else "")
                }
            },
            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(id = R.string.common_log)
            )
        }
    }
}

@Composable
private fun ExtraSetInput(
    planTarget: PlanTarget?,
    defaultWarmupFlag: Boolean,
    intensitySystem: IntensitySystem,
    onLogSet: (Int, Double, Boolean, String) -> Unit,
    haptic: HapticFeedbackHelper
) {
    val dims = ironLogDimens
    val tracksIntensity = intensitySystem != IntensitySystem.OFF
    var repsInput by remember {
        mutableStateOf(
            planTarget?.let {
                if (it.targetReps > 0) it.targetReps.toString() else ""
            } ?: ""
        )
    }
    var weightInput by remember {
        mutableStateOf(
            planTarget?.let {
                if (it.targetWeightKg > 0) it.targetWeightKg.toString() else ""
            } ?: ""
        )
    }
    var intensityInput by remember { mutableStateOf("") }
    var isWarmup by remember { mutableStateOf(defaultWarmupFlag) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = isWarmup,
                onClick = { isWarmup = !isWarmup },
                label = { Text(stringResource(id = R.string.workout_warmup_chip)) }
            )
        }

        Spacer(modifier = Modifier.height(dims.spacing2))

        SetInputRow(
            reps = repsInput,
            onRepsChange = { repsInput = it },
            weight = weightInput,
            onWeightChange = { weightInput = it },
            intensity = intensityInput,
            onIntensityChange = { intensityInput = it },
            intensityLabel = intensitySystem.displayName,
            showIntensityField = tracksIntensity,
            onLog = {
                val reps = repsInput.toIntOrNull()
                val weight = weightInput.toDoubleOrNull()
                if (reps != null && reps > 0 && weight != null && weight >= 0) {
                    onLogSet(reps, weight, isWarmup, if (tracksIntensity) intensityInput else "")
                    haptic.confirm()
                    repsInput = ""
                    weightInput = ""
                    intensityInput = ""
                }
            }
        )
    }
}


