package com.ironlog.app.presentation.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.core.designsystem.R
import com.ironlog.app.domain.model.AppPreferences
import com.ironlog.app.domain.model.IntensitySystem
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.domain.util.DateFormatting
import com.ironlog.app.presentation.common.HapticFeedbackHelper
import com.ironlog.app.presentation.common.IronLogScreenScaffold
import com.ironlog.app.presentation.common.IronLogSurfaceCard
import com.ironlog.app.presentation.common.IronLogSurfaceTone
import com.ironlog.app.presentation.common.LoadingScreen
import com.ironlog.app.presentation.common.SetInputRow
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.spring
import com.ironlog.app.presentation.common.RestTimer
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

    val exerciseGroups = remember(state.exercisesWithSets) {
        buildExerciseRenderGroups(state.exercisesWithSets)
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dims.spacingMd, vertical = dims.spacingXs),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        WorkoutTimer(startTime = session.startTime)
                    }

                    AnimatedVisibility(
                        visible = state.restTimerStartTime != null,
                        enter = fadeIn() + expandVertically(animationSpec = spring()),
                        exit = fadeOut() + shrinkVertically(animationSpec = spring())
                    ) {
                        state.restTimerStartTime?.let { startTime ->
                            RestTimer(
                                startTime = startTime,
                                onDismiss = viewModel::dismissRestTimer,
                                modifier = Modifier.padding(bottom = dims.spacingMd)
                            )
                        }
                    }
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
                                onUpdateSet = viewModel::updateSet,
                                onDeleteSet = viewModel::deleteSet,
                                haptic = haptic
                            )
                        }
                    }
                }
            }
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
    IronLogSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        tone = IronLogSurfaceTone.ELEVATED,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
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
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = exerciseNames,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
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
    onUpdateSet: (Long, Int, Double, String) -> Unit,
    onDeleteSet: (Long) -> Unit,
    haptic: HapticFeedbackHelper
) {
    val dims = ironLogDimens
    val planTarget = exerciseWithSets.planTarget
    val previousSession = exerciseWithSets.previousSession
    val showHistoryToggle = previousSession != null
    val previousWeightHint = previousSession?.lastWorkSetWeightKg?.let(::formatWeightPlaceholder)
    var showPreviousSession by remember(exerciseWithSets.exercise.id) { mutableStateOf(false) }
    val loggedSets = exerciseWithSets.sets.filter { it.reps > 0 }
    val completedWorkSets = loggedSets.count { !it.isWarmup }
    val targetSetCount = planTarget?.targetSets ?: 0

    IronLogSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        tone = IronLogSurfaceTone.MUTED,
        alpha = 0.68f
    ) {
        Column(modifier = Modifier.padding(dims.spacingMd)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (showHistoryToggle) {
                            Modifier.clickable { showPreviousSession = !showPreviousSession }
                        } else {
                            Modifier
                        }
                    )
                    .padding(vertical = dims.spacingXs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showHistoryToggle) {
                    Box(
                        modifier = Modifier.size(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (showPreviousSession) Icons.Default.Remove else Icons.Default.Add,
                            contentDescription = if (showPreviousSession) {
                                stringResource(id = R.string.workout_previous_hide_cd)
                            } else {
                                stringResource(id = R.string.workout_previous_show_cd)
                            },
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(28.dp))
                }
                Text(
                    text = exerciseWithSets.exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = dims.spacingXs)
                )
            }

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
                        LoggedSetRow(set = matchingSet, intensitySystem = intensitySystem, onUpdateSet = onUpdateSet, onDeleteSet = onDeleteSet, haptic = haptic)
                    } else {
                        PendingSetRow(
                            setNumber = setIndex,
                            repsPlaceholder = if (planTarget.targetReps > 0) planTarget.targetReps.toString() else null,
                            defaultWeight = "",
                            weightPlaceholder = previousWeightHint,
                            intensitySystem = intensitySystem,
                            onLog = { reps, weight, intensity -> onLogSet(reps, weight, false, intensity) }
                        )
                    }
                }

                loggedSets.filter { !it.isWarmup }.drop(targetSetCount).forEach { set ->
                    LoggedSetRow(set = set, intensitySystem = intensitySystem, onUpdateSet = onUpdateSet, onDeleteSet = onDeleteSet, haptic = haptic)
                }

                loggedSets.filter { it.isWarmup }.forEach { set ->
                    LoggedSetRow(set = set, intensitySystem = intensitySystem, onUpdateSet = onUpdateSet, onDeleteSet = onDeleteSet, haptic = haptic)
                }

                Spacer(modifier = Modifier.height(dims.spacingXs))

                var showExtraInput by remember { mutableStateOf(false) }
                AnimatedVisibility(visible = showExtraInput) {
                    ExtraSetInput(
                        planTarget = planTarget,
                        defaultWarmupFlag = defaultWarmupFlag,
                        intensitySystem = intensitySystem,
                        weightPlaceholder = previousWeightHint,
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
                    LoggedSetRow(set = set, intensitySystem = intensitySystem, onUpdateSet = onUpdateSet, onDeleteSet = onDeleteSet, haptic = haptic)
                }

                Spacer(modifier = Modifier.height(dims.spacingXs))

                ExtraSetInput(
                    planTarget = null,
                    defaultWarmupFlag = defaultWarmupFlag,
                    intensitySystem = intensitySystem,
                    weightPlaceholder = previousWeightHint,
                    onLogSet = onLogSet,
                    haptic = haptic
                )
            }

            AnimatedVisibility(
                visible = showPreviousSession && previousSession != null,
                enter = fadeIn() + expandVertically(animationSpec = spring()),
                exit = fadeOut() + shrinkVertically(animationSpec = spring())
            ) {
                previousSession?.let {
                    PreviousSessionMiniHistory(
                        previousSession = it,
                        intensitySystem = intensitySystem,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = dims.spacingSm)
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviousSessionMiniHistory(
    previousSession: PreviousExerciseSessionUi,
    intensitySystem: IntensitySystem,
    modifier: Modifier = Modifier
) {
    val dims = ironLogDimens
    val dateLabel = previousSession.sessionStart.format(DateFormatting.DATE_SHORT)

    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                shape = MaterialTheme.shapes.small
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = dims.spacingSm, vertical = dims.spacingXs),
        verticalArrangement = Arrangement.spacedBy(dims.spacing2)
    ) {
        Text(
            text = stringResource(id = R.string.workout_previous_session_title, dateLabel),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dims.spacing2),
            verticalArrangement = Arrangement.spacedBy(dims.spacingXs)
        ) {
            previousSession.sets.forEach { set ->
                PreviousSessionSetRow(
                    set = set,
                    intensitySystem = intensitySystem
                )
            }
        }
    }
}

@Composable
private fun PreviousSessionSetRow(
    set: com.ironlog.app.domain.model.WorkoutSet,
    intensitySystem: IntensitySystem,
    modifier: Modifier = Modifier
) {
    val dims = ironLogDimens
    val tracksIntensity = intensitySystem != IntensitySystem.OFF
    val setLabel = if (set.isWarmup) "W${set.setNumber}" else set.setNumber.toString()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(dims.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = setLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.width(20.dp)
        )
        
        val weightText = if (set.weightKg % 1 == 0.0) set.weightKg.toInt().toString() else set.weightKg.toString()
        
        LoggedSetBox(
            value = weightText,
            suffix = stringResource(id = R.string.common_unit_kg),
            isWarmup = set.isWarmup,
            modifier = Modifier.weight(1.2f).alpha(0.7f)
        )
        
        LoggedSetBox(
            value = set.reps.toString(),
            suffix = stringResource(id = R.string.common_reps_short),
            isWarmup = set.isWarmup,
            modifier = Modifier.weight(1f).alpha(0.7f)
        )
        
        if (tracksIntensity) {
            val intensityText = formatIntensity(set.rpe, intensitySystem)
            
            LoggedSetBox(
                value = intensityText,
                suffix = intensitySystem.displayName,
                isWarmup = set.isWarmup,
                modifier = Modifier.weight(1f).alpha(0.7f)
            )
        }
        
        // Placeholder for the delete button to maintain perfect alignment with LoggedSetRow
        Spacer(modifier = Modifier.size(40.dp))
    }
}

private fun formatWeightPlaceholder(weightKg: Double): String {
    return if (weightKg % 1.0 == 0.0) weightKg.toInt().toString() else String.format("%.1f", weightKg)
}

@Composable
private fun LoggedSetBox(
    value: String,
    suffix: String,
    isWarmup: Boolean,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isWarmup) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Box(
        modifier = modifier
            .height(40.dp)
            .background(color = containerColor, shape = MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value,
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isWarmup) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    fontStyle = if (isWarmup) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            if (suffix.isNotEmpty()) {
                Text(
                    text = suffix,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun LoggedSetRow(
    set: com.ironlog.app.domain.model.WorkoutSet,
    intensitySystem: com.ironlog.app.domain.model.IntensitySystem,
    onUpdateSet: (Long, Int, Double, String) -> Unit,
    onDeleteSet: (Long) -> Unit,
    haptic: com.ironlog.app.presentation.common.HapticFeedbackHelper
) {
    val dims = ironLogDimens
    val tracksIntensity = intensitySystem != com.ironlog.app.domain.model.IntensitySystem.OFF
    var isEditing by remember { mutableStateOf(false) }

    if (isEditing) {
        val weightText = if (set.weightKg % 1 == 0.0) set.weightKg.toInt().toString() else set.weightKg.toString()
        var repsInput by remember { mutableStateOf(TextFieldValue(set.reps.toString(), TextRange(set.reps.toString().length))) }
        var weightInput by remember { mutableStateOf(TextFieldValue(weightText, TextRange(weightText.length))) }
        val intensityInitial = formatIntensity(set.rpe, intensitySystem)
        var intensityInput by remember { mutableStateOf(TextFieldValue(intensityInitial, TextRange(intensityInitial.length))) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dims.spacingXs),
            horizontalArrangement = Arrangement.spacedBy(dims.spacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = set.setNumber.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(20.dp)
            )
            
            com.ironlog.app.presentation.common.CompactTextField(
                value = weightInput,
                onValueChange = { weightInput = it },
                suffix = stringResource(id = R.string.common_unit_kg),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1.2f)
            )
            
            com.ironlog.app.presentation.common.CompactTextField(
                value = repsInput,
                onValueChange = { repsInput = it },
                suffix = stringResource(id = R.string.common_reps_short),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = if (tracksIntensity) ImeAction.Next else ImeAction.Done),
                modifier = Modifier.weight(1f)
            )
            
            if (tracksIntensity) {
                com.ironlog.app.presentation.common.CompactTextField(
                    value = intensityInput,
                    onValueChange = { intensityInput = it },
                    suffix = intensitySystem.displayName,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f)
                )
            }
            
            IconButton(
                onClick = {
                    val r = repsInput.text.toIntOrNull() ?: set.reps
                    val w = weightInput.text.toDoubleOrNull() ?: set.weightKg
                    onUpdateSet(set.id, r, w, intensityInput.text)
                    isEditing = false
                    haptic.confirm()
                },
                modifier = Modifier.size(40.dp),
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(id = R.string.common_log),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dims.spacingXs)
                .clickable {
                    isEditing = true
                    haptic.confirm()
                },
            horizontalArrangement = Arrangement.spacedBy(dims.spacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = set.setNumber.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(20.dp)
            )
            
            val weightText = if (set.weightKg % 1 == 0.0) set.weightKg.toInt().toString() else set.weightKg.toString()
            
            LoggedSetBox(
                value = weightText,
                suffix = stringResource(id = R.string.common_unit_kg),
                isWarmup = set.isWarmup,
                modifier = Modifier.weight(1.2f)
            )
            
            LoggedSetBox(
                value = set.reps.toString(),
                suffix = stringResource(id = R.string.common_reps_short),
                isWarmup = set.isWarmup,
                modifier = Modifier.weight(1f)
            )
            
            if (tracksIntensity) {
                val intensityText = formatIntensity(set.rpe, intensitySystem)
                
                LoggedSetBox(
                    value = intensityText,
                    suffix = intensitySystem.displayName,
                    isWarmup = set.isWarmup,
                    modifier = Modifier.weight(1f)
                )
            }
            
            IconButton(
                onClick = { haptic.reject(); onDeleteSet(set.id) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(id = R.string.workout_delete_set_cd),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PendingSetRow(
    setNumber: Int,
    repsPlaceholder: String? = null,
    defaultWeight: String,
    weightPlaceholder: String? = null,
    intensitySystem: IntensitySystem,
    onLog: (Int, Double, String) -> Unit
) {
    val dims = ironLogDimens
    val tracksIntensity = intensitySystem != IntensitySystem.OFF
    var repsInput by remember { mutableStateOf(TextFieldValue("", TextRange.Zero)) }
    var weightInput by remember { mutableStateOf(TextFieldValue(defaultWeight, TextRange(defaultWeight.length))) }
    var intensityInput by remember { mutableStateOf(TextFieldValue("", TextRange.Zero)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dims.spacingXs),
        horizontalArrangement = Arrangement.spacedBy(dims.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = setNumber.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp)
        )
        
        com.ironlog.app.presentation.common.CompactTextField(
            value = weightInput,
            onValueChange = { weightInput = it },
            suffix = stringResource(id = R.string.common_unit_kg),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            placeholderText = weightPlaceholder ?: "-",
            modifier = Modifier.weight(1.2f)
        )
        
        com.ironlog.app.presentation.common.CompactTextField(
            value = repsInput,
            onValueChange = { repsInput = it },
            suffix = stringResource(id = R.string.common_reps_short),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = if (tracksIntensity) ImeAction.Next else ImeAction.Done),
            placeholderText = repsPlaceholder ?: "-",
            modifier = Modifier.weight(1f)
        )
        
        if (tracksIntensity) {
            com.ironlog.app.presentation.common.CompactTextField(
                value = intensityInput,
                onValueChange = { intensityInput = it },
                suffix = intensitySystem.displayName,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f)
            )
        }
        
        IconButton(
            onClick = {
                val reps = repsInput.text.toIntOrNull() ?: repsPlaceholder?.toIntOrNull()
                val weight = weightInput.text.toDoubleOrNull() ?: weightPlaceholder?.replace(",", ".")?.toDoubleOrNull()
                if (reps != null && reps > 0 && weight != null && weight >= 0) {
                    onLog(reps, weight, if (tracksIntensity) intensityInput.text else "")
                }
            },
            modifier = Modifier.size(40.dp),
            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(id = R.string.common_log),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ExtraSetInput(
    planTarget: PlanTarget?,
    defaultWarmupFlag: Boolean,
    intensitySystem: IntensitySystem,
    weightPlaceholder: String? = null,
    onLogSet: (Int, Double, Boolean, String) -> Unit,
    haptic: com.ironlog.app.presentation.common.HapticFeedbackHelper
) {
    val dims = ironLogDimens
    val tracksIntensity = intensitySystem != IntensitySystem.OFF
    val repsPlaceholder = planTarget?.let {
        if (it.targetReps > 0) it.targetReps.toString() else null
    }
    var repsInput by remember { mutableStateOf(TextFieldValue("", TextRange.Zero)) }
    var weightInput by remember { mutableStateOf(TextFieldValue("", TextRange.Zero)) }
    var intensityInput by remember { mutableStateOf(TextFieldValue("", TextRange.Zero)) }
    var isWarmup by remember { mutableStateOf(defaultWarmupFlag) }

    Column(modifier = Modifier.padding(top = dims.spacingXs)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = dims.spacingXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = isWarmup,
                onClick = { isWarmup = !isWarmup },
                label = { Text(stringResource(id = R.string.workout_warmup_chip), style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(28.dp)
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
            weightPlaceholder = weightPlaceholder,
            repsPlaceholder = repsPlaceholder,
            showIntensityField = tracksIntensity,
            onLog = {
                val reps = repsInput.text.toIntOrNull() ?: repsPlaceholder?.toIntOrNull()
                val weight = weightInput.text.toDoubleOrNull() ?: weightPlaceholder?.replace(",", ".")?.toDoubleOrNull()
                if (reps != null && reps > 0 && weight != null && weight >= 0) {
                    onLogSet(reps, weight, isWarmup, if (tracksIntensity) intensityInput.text else "")
                    haptic.confirm()
                    repsInput = TextFieldValue("", TextRange.Zero)
                    weightInput = TextFieldValue("", TextRange.Zero)
                    intensityInput = TextFieldValue("", TextRange.Zero)
                }
            }
        )
    }
}

private fun formatIntensity(rpe: Double?, intensitySystem: IntensitySystem): String {
    if (rpe == null || intensitySystem == IntensitySystem.OFF) return ""
    val displayValue = if (intensitySystem == IntensitySystem.RIR) {
        10.0 - rpe
    } else {
        rpe
    }
    return if (displayValue % 1.0 == 0.0) displayValue.toInt().toString() else displayValue.toString()
}




