package com.ironlog.app.presentation.workout

import android.annotation.SuppressLint
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.core.designsystem.R
import com.ironlog.app.domain.model.AppPreferences
import com.ironlog.app.domain.model.IntensitySystem
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.domain.util.DateFormatting
import com.ironlog.app.domain.util.WeightFormatting
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
import com.ironlog.app.presentation.theme.ButtonSize
import com.ironlog.app.presentation.theme.IconSize
import com.ironlog.app.presentation.theme.Radius
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.app.presentation.theme.semantic
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.util.Locale

private data class ExerciseRenderGroup(
    val key: String,
    val supersetGroupId: Int?,
    val exercises: List<ExerciseWithSets>
)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("LocalContextGetResourceValueCall")
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
    val activeSession = (state.sessionPhase as? ActiveWorkoutSessionPhase.Active)?.session
    var workoutEndNavigated by remember { mutableStateOf(false) }

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

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = error.message,
            actionLabel = error.retry?.let { context.getString(R.string.common_retry) },
            withDismissAction = true,
            duration = SnackbarDuration.Long
        )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.retryLastError()
            else -> viewModel.clearError()
        }
    }

    LaunchedEffect(state.sessionPhase, state.workoutFinished) {
        val session = (state.sessionPhase as? ActiveWorkoutSessionPhase.Active)?.session
        val shouldNavigate = state.workoutFinished || session?.endTime != null
        if (shouldNavigate && !workoutEndNavigated) {
            workoutEndNavigated = true
            onWorkoutFinished()
        }
    }

    IronLogScreenScaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                title = {
                    val name = activeSession?.name?.takeIf { it.isNotBlank() }
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
        when (state.sessionPhase) {
            ActiveWorkoutSessionPhase.Loading -> {
                LoadingScreen(modifier = Modifier.padding(padding))
                return@IronLogScreenScaffold
            }
            ActiveWorkoutSessionPhase.Missing -> {
                MissingSessionContent(
                    onBack = { onWorkoutFinished() },
                    modifier = Modifier.padding(padding)
                )
                return@IronLogScreenScaffold
            }
            is ActiveWorkoutSessionPhase.Active -> Unit
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            activeSession?.let { session ->
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
                        visible = state.restTimers.isNotEmpty(),
                        enter = fadeIn() + expandVertically(animationSpec = spring()),
                        exit = fadeOut() + shrinkVertically(animationSpec = spring())
                    ) {
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(bottom = dims.spacingMd),
                            horizontalArrangement = Arrangement.Center,
                            verticalArrangement = Arrangement.spacedBy(dims.spacingSm)
                        ) {
                            state.restTimers.forEach { (exerciseId, startTime) ->
                                val exerciseWithSets = state.exercisesWithSets.find { it.exercise.id == exerciseId }
                                val group = exerciseGroups.find { it.exercises.any { ex -> ex.exercise.id == exerciseId } }
                                val indexInSuperset = group?.exercises?.indexOfFirst { it.exercise.id == exerciseId } ?: -1

                                RestTimer(
                                    startTime = startTime,
                                    onDismiss = { viewModel.dismissRestTimer(exerciseId) },
                                    titleText = exerciseWithSets?.exercise?.name,
                                    baseColor = supersetTintColor(group?.supersetGroupId, indexInSuperset),
                                    modifier = Modifier.padding(horizontal = dims.spacingXs)
                                )
                            }
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
                        group.exercises.forEachIndexed { indexInSuperset, exerciseWithSets ->
                            ExerciseCard(
                                exerciseWithSets = exerciseWithSets,
                                tintColor = supersetTintColor(group.supersetGroupId, indexInSuperset),
                                defaultWarmupFlag = preferences.defaultWarmupFlag,
                                intensitySystem = preferences.intensitySystem,
                                unitSystem = preferences.unitSystem,
                                isLogging = (state.logInFlightByExercise[exerciseWithSets.exercise.id] ?: 0) > 0,
                                logSuccessSubmissions = state.logSuccessSubmissions,
                                updateInFlightBySet = state.updateInFlightBySet,
                                updateSuccessCountBySet = state.updateSuccessCountBySet,
                                onLogSet = { reps, weight, isWarmup, intensity, submissionId ->
                                    viewModel.logSet(
                                        exerciseWithSets.exercise.id,
                                        reps,
                                        weight,
                                        isWarmup,
                                        intensity,
                                        submissionId
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
                text = {
                    val error = state.error
                    Column {
                        Text(stringResource(id = R.string.workout_finish_dialog_text))
                        if (error != null && error.retry is WorkoutRetryDescriptor.FinishWorkout) {
                            Text(
                                text = error.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = dims.spacingSm)
                            )
                            TextButton(
                                onClick = viewModel::retryLastError,
                                modifier = Modifier.padding(top = dims.spacingXs)
                            ) {
                                Text(stringResource(id = R.string.common_retry))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = viewModel::finishWorkout,
                        enabled = !state.finishInFlight
                    ) {
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
private fun MissingSessionContent(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = ironLogDimens
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dims.spacingLg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.workout_missing_session_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(id = R.string.workout_missing_session_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = dims.spacingSm)
        )
        TextButton(
            onClick = onBack,
            modifier = Modifier.padding(top = dims.spacingMd)
        ) {
            Text(stringResource(id = R.string.nav_back))
        }
    }
}

@Composable
private fun supersetTintColor(supersetGroupId: Int?, indexInSuperset: Int): Color? {
    if (supersetGroupId == null || indexInSuperset < 0) return null
    return when (indexInSuperset % 3) {
        0 -> MaterialTheme.semantic.violet
        1 -> MaterialTheme.semantic.sky
        else -> MaterialTheme.semantic.rose
    }
}

@Composable
private fun SupersetHeader(groupId: Int, exerciseCount: Int, exerciseNames: String) {
    val dims = ironLogDimens
    IronLogSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        tone = IronLogSurfaceTone.ACCENT,
        border = BorderStroke(1.dp, MaterialTheme.semantic.violet.copy(alpha = 0.28f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dims.spacingSm, vertical = dims.spacingXs),
            verticalArrangement = Arrangement.spacedBy(dims.spacing2)
        ) {
            Text(
                text = pluralStringResource(
                    id = R.plurals.workout_superset_header,
                    count = exerciseCount,
                    groupId,
                    exerciseCount
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.semantic.violet
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
    tintColor: Color?,
    defaultWarmupFlag: Boolean,
    intensitySystem: IntensitySystem,
    unitSystem: UnitSystem,
    isLogging: Boolean,
    logSuccessSubmissions: Set<Long>,
    updateInFlightBySet: Map<Long, Int>,
    updateSuccessCountBySet: Map<Long, Int>,
    onLogSet: (Int, Double, Boolean, String, Long) -> Unit,
    onUpdateSet: (Long, Int, Double, String) -> Unit,
    onDeleteSet: (Long) -> Unit,
    haptic: HapticFeedbackHelper
) {
    val dims = ironLogDimens
    val planTarget = exerciseWithSets.planTarget
    val previousSession = exerciseWithSets.previousSession
    val showHistoryToggle = previousSession != null
    val previousWeightHint = previousSession?.lastWorkSetWeightKg?.let { formatWeightValue(it, unitSystem) }
    var showPreviousSession by remember(exerciseWithSets.exercise.id) { mutableStateOf(false) }
    val loggedSets = exerciseWithSets.sets.filter { it.reps > 0 }
    val completedWorkSets = loggedSets.count { !it.isWarmup }
    val targetSetCount = planTarget?.targetSets ?: 0

    IronLogSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        tone = IronLogSurfaceTone.MUTED,
        border = tintColor?.let { BorderStroke(1.dp, it.copy(alpha = 0.3f)) },
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
                        modifier = Modifier.size(IconSize.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (showPreviousSession) Icons.Default.Remove else Icons.Default.Add,
                            contentDescription = if (showPreviousSession) {
                                stringResource(id = R.string.workout_previous_hide_cd)
                            } else {
                                stringResource(id = R.string.workout_previous_show_cd)
                            },
                            tint = tintColor ?: MaterialTheme.semantic.sky,
                            modifier = Modifier.size(IconSize.sm)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(IconSize.lg))
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
                        formatTargetWeight(planTarget.targetWeightKg, unitSystem)
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
                    color = tintColor ?: MaterialTheme.colorScheme.primary
                )
                if (completedWorkSets >= planTarget.targetSets) {
                    Text(
                        text = stringResource(id = R.string.workout_target_completed),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.semantic.success
                    )
                }
            }

            Spacer(modifier = Modifier.height(dims.spacingXs))

            if (planTarget != null && targetSetCount > 0) {
                for (setIndex in 1..targetSetCount) {
                    val matchingSet = loggedSets.filter { !it.isWarmup }.getOrNull(setIndex - 1)
                    if (matchingSet != null) {
                        LoggedSetRow(
                            set = matchingSet,
                            intensitySystem = intensitySystem,
                            unitSystem = unitSystem,
                            isUpdating = (updateInFlightBySet[matchingSet.id] ?: 0) > 0,
                            updateSuccessCount = updateSuccessCountBySet[matchingSet.id] ?: 0,
                            onUpdateSet = onUpdateSet,
                            onDeleteSet = onDeleteSet,
                            haptic = haptic
                        )
                    } else {
                        PendingSetRow(
                            setNumber = setIndex,
                            repsPlaceholder = if (planTarget.targetReps > 0) planTarget.targetReps.toString() else null,
                            defaultWeight = "",
                            weightPlaceholder = previousWeightHint,
                            intensitySystem = intensitySystem,
                            unitSystem = unitSystem,
                            locked = isLogging,
                            completedSubmissions = logSuccessSubmissions,
                            onLog = { reps, weight, intensity, submissionId ->
                                onLogSet(reps, weight, false, intensity, submissionId)
                            }
                        )
                    }
                }

                loggedSets.filter { !it.isWarmup }.drop(targetSetCount).forEach { set ->
                    LoggedSetRow(
                        set = set,
                        intensitySystem = intensitySystem,
                        unitSystem = unitSystem,
                        isUpdating = (updateInFlightBySet[set.id] ?: 0) > 0,
                        updateSuccessCount = updateSuccessCountBySet[set.id] ?: 0,
                        onUpdateSet = onUpdateSet,
                        onDeleteSet = onDeleteSet,
                        haptic = haptic
                    )
                }

                loggedSets.filter { it.isWarmup }.forEach { set ->
                    LoggedSetRow(
                        set = set,
                        intensitySystem = intensitySystem,
                        unitSystem = unitSystem,
                        isUpdating = (updateInFlightBySet[set.id] ?: 0) > 0,
                        updateSuccessCount = updateSuccessCountBySet[set.id] ?: 0,
                        onUpdateSet = onUpdateSet,
                        onDeleteSet = onDeleteSet,
                        haptic = haptic
                    )
                }

                Spacer(modifier = Modifier.height(dims.spacingXs))

                var showExtraInput by remember { mutableStateOf(false) }
                AnimatedVisibility(visible = showExtraInput) {
                    ExtraSetInput(
                        planTarget = planTarget,
                        defaultWarmupFlag = defaultWarmupFlag,
                        intensitySystem = intensitySystem,
                        unitSystem = unitSystem,
                        weightPlaceholder = previousWeightHint,
                        locked = isLogging,
                        logSuccessSubmissions = logSuccessSubmissions,
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
                    LoggedSetRow(
                        set = set,
                        intensitySystem = intensitySystem,
                        unitSystem = unitSystem,
                        isUpdating = (updateInFlightBySet[set.id] ?: 0) > 0,
                        updateSuccessCount = updateSuccessCountBySet[set.id] ?: 0,
                        onUpdateSet = onUpdateSet,
                        onDeleteSet = onDeleteSet,
                        haptic = haptic
                    )
                }

                Spacer(modifier = Modifier.height(dims.spacingXs))

                ExtraSetInput(
                    planTarget = null,
                    defaultWarmupFlag = defaultWarmupFlag,
                    intensitySystem = intensitySystem,
                    unitSystem = unitSystem,
                    weightPlaceholder = previousWeightHint,
                    locked = isLogging,
                    logSuccessSubmissions = logSuccessSubmissions,
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
                        unitSystem = unitSystem,
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
    unitSystem: UnitSystem,
    modifier: Modifier = Modifier
) {
    val dims = ironLogDimens
    val dateLabel = previousSession.sessionStart.format(DateFormatting.DATE_SHORT)

    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.semantic.skyLight.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.small
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.semantic.sky.copy(alpha = 0.28f),
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
                    intensitySystem = intensitySystem,
                    unitSystem = unitSystem
                )
            }
        }
    }
}

@Composable
private fun PreviousSessionSetRow(
    set: com.ironlog.app.domain.model.WorkoutSet,
    intensitySystem: IntensitySystem,
    unitSystem: UnitSystem,
    modifier: Modifier = Modifier
) {
    val dims = ironLogDimens
    val tracksIntensity = intensitySystem != IntensitySystem.OFF
    val setLabel = if (set.isWarmup) "W${set.setNumber}" else set.setNumber.toString()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = dims.spacing2),
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
        
        val weightText = formatWeightValue(set.weightKg, unitSystem)
        
        LoggedSetBox(
            value = weightText,
            suffix = WeightFormatting.unitLabel(unitSystem),
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
            val accentColor = rpeColor(set.rpe)

            LoggedSetBox(
                value = intensityText,
                suffix = intensitySystem.displayName,
                isWarmup = set.isWarmup,
                modifier = Modifier.weight(1f).alpha(0.7f),
                overrideContainerColor = accentColor?.copy(alpha = 0.15f),
                overrideContentColor = accentColor
            )
        }
        
        // Placeholder for the delete button to maintain perfect alignment with LoggedSetRow
        Spacer(modifier = Modifier.size(40.dp))
    }
}

/** Formats a weight stored in kg as a plain number in the user's preferred unit system. */
private fun formatWeightValue(weightKg: Double, unitSystem: UnitSystem): String {
    val displayValue = WeightFormatting.convertToDisplay(weightKg, unitSystem)
    return if (displayValue % 1.0 == 0.0) {
        displayValue.toInt().toString()
    } else {
        String.format(Locale.ROOT, "%.1f", displayValue)
    }
}

/** Formats a plan target weight stored in kg for display in the user's preferred unit system, e.g. "100.0 kg" or "220.5 lb". */
fun formatTargetWeight(weightKg: Double, unitSystem: UnitSystem): String {
    val displayValue = WeightFormatting.convertToDisplay(weightKg, unitSystem)
    return String.format(Locale.ROOT, "%.1f %s", displayValue, WeightFormatting.unitLabel(unitSystem))
}

@Composable
private fun LoggedSetBox(
    value: String,
    suffix: String,
    isWarmup: Boolean,
    modifier: Modifier = Modifier,
    overrideContainerColor: Color? = null,
    overrideContentColor: Color? = null
) {
    val dims = ironLogDimens
    val containerColor = overrideContainerColor ?: if (isWarmup) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    }

    Box(
        modifier = modifier
            .height(ButtonSize.iconButton)
            .background(color = containerColor, shape = RoundedCornerShape(Radius.sm)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = dims.spacingSm),
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
                    color = overrideContentColor ?: if (isWarmup) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    fontStyle = if (isWarmup) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            if (suffix.isNotEmpty()) {
                Text(
                    text = suffix,
                    style = MaterialTheme.typography.bodyMedium,
                    color = (overrideContentColor ?: MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.9f),
                    modifier = Modifier.padding(start = dims.spacingXs)
                )
            }
        }
    }
}

@Composable
private fun LoggedSetRow(
    set: com.ironlog.app.domain.model.WorkoutSet,
    intensitySystem: com.ironlog.app.domain.model.IntensitySystem,
    unitSystem: UnitSystem,
    isUpdating: Boolean,
    updateSuccessCount: Int,
    onUpdateSet: (Long, Int, Double, String) -> Unit,
    onDeleteSet: (Long) -> Unit,
    haptic: com.ironlog.app.presentation.common.HapticFeedbackHelper
) {
    val dims = ironLogDimens
    val tracksIntensity = intensitySystem != com.ironlog.app.domain.model.IntensitySystem.OFF
    var isEditing by remember(set.id) { mutableStateOf(false) }
    val weightText = remember(set.id, set.weightKg, unitSystem) {
        formatWeightValue(set.weightKg, unitSystem)
    }
    val intensityText = remember(set.id, set.rpe, intensitySystem) {
        formatIntensity(set.rpe, intensitySystem)
    }
    var repsInput by remember(set.id) { mutableStateOf(TextFieldValue("", TextRange.Zero)) }
    var weightInput by remember(set.id) { mutableStateOf(TextFieldValue("", TextRange.Zero)) }
    var intensityInput by remember(set.id) { mutableStateOf(TextFieldValue("", TextRange.Zero)) }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            val repsText = set.reps.toString()
            repsInput = TextFieldValue(repsText, TextRange(repsText.length))
            weightInput = TextFieldValue(weightText, TextRange(weightText.length))
            intensityInput = TextFieldValue(intensityText, TextRange(intensityText.length))
        }
    }

    LaunchedEffect(updateSuccessCount) {
        if (updateSuccessCount > 0 && isEditing) {
            isEditing = false
            haptic.confirm()
        }
    }

    if (isEditing) {
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
                suffix = WeightFormatting.unitLabel(unitSystem),
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
                    val enteredWeight = parseDecimal(weightInput.text)
                    val w = enteredWeight?.let { WeightFormatting.convertToKg(it, unitSystem) } ?: set.weightKg
                    onUpdateSet(set.id, r, w, intensityInput.text)
                },
                enabled = !isUpdating,
                modifier = Modifier.size(ButtonSize.iconButton),
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(id = R.string.common_log),
                    modifier = Modifier.size(IconSize.sm)
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
            
            LoggedSetBox(
                value = weightText,
                suffix = WeightFormatting.unitLabel(unitSystem),
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
                val accentColor = rpeColor(set.rpe)

                LoggedSetBox(
                    value = intensityText,
                    suffix = intensitySystem.displayName,
                    isWarmup = set.isWarmup,
                    modifier = Modifier.weight(1f),
                    overrideContainerColor = accentColor?.copy(alpha = 0.15f),
                    overrideContentColor = accentColor
                )
            }
            
            IconButton(
                onClick = { haptic.reject(); onDeleteSet(set.id) },
                modifier = Modifier.size(ButtonSize.iconButton)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(id = R.string.workout_delete_set_cd),
                    tint = MaterialTheme.semantic.danger,
                    modifier = Modifier.size(IconSize.sm)
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
    unitSystem: UnitSystem,
    locked: Boolean,
    completedSubmissions: Set<Long>,
    onLog: (Int, Double, String, Long) -> Unit
) {
    val dims = ironLogDimens
    val tracksIntensity = intensitySystem != IntensitySystem.OFF
    var repsInput by remember { mutableStateOf(TextFieldValue("", TextRange.Zero)) }
    var weightInput by remember { mutableStateOf(TextFieldValue(defaultWeight, TextRange(defaultWeight.length))) }
    var intensityInput by remember { mutableStateOf(TextFieldValue("", TextRange.Zero)) }
    var activeSubmissionId by remember(setNumber) { mutableStateOf<Long?>(null) }

    LaunchedEffect(activeSubmissionId, completedSubmissions) {
        val submissionId = activeSubmissionId ?: return@LaunchedEffect
        if (submissionId in completedSubmissions) {
            repsInput = TextFieldValue("", TextRange.Zero)
            weightInput = TextFieldValue("", TextRange.Zero)
            intensityInput = TextFieldValue("", TextRange.Zero)
            activeSubmissionId = null
        }
    }

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
            suffix = WeightFormatting.unitLabel(unitSystem),
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
                val enteredWeight = parseDecimal(weightInput.text) ?: weightPlaceholder?.let(::parseDecimal)
                val weight = enteredWeight?.let { WeightFormatting.convertToKg(it, unitSystem) }
                if (reps != null && reps > 0 && weight != null && weight >= 0) {
                    val submissionId = nextSubmissionId()
                    activeSubmissionId = submissionId
                    onLog(
                        reps,
                        weight,
                        if (tracksIntensity) intensityInput.text else "",
                        submissionId
                    )
                }
            },
            enabled = !locked,
            modifier = Modifier.size(ButtonSize.iconButton),
            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(id = R.string.common_log),
                modifier = Modifier.size(IconSize.sm)
            )
        }
    }
}

@Composable
private fun ExtraSetInput(
    planTarget: PlanTarget?,
    defaultWarmupFlag: Boolean,
    intensitySystem: IntensitySystem,
    unitSystem: UnitSystem,
    weightPlaceholder: String? = null,
    locked: Boolean,
    logSuccessSubmissions: Set<Long>,
    onLogSet: (Int, Double, Boolean, String, Long) -> Unit,
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
    var activeSubmissionId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(activeSubmissionId, logSuccessSubmissions) {
        val submissionId = activeSubmissionId ?: return@LaunchedEffect
        if (submissionId in logSuccessSubmissions) {
            repsInput = TextFieldValue("", TextRange.Zero)
            weightInput = TextFieldValue("", TextRange.Zero)
            intensityInput = TextFieldValue("", TextRange.Zero)
            activeSubmissionId = null
            haptic.confirm()
        }
    }

    Column(modifier = Modifier.padding(top = dims.spacingXs)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = dims.spacingXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = isWarmup,
                onClick = { isWarmup = !isWarmup },
                enabled = !locked,
                label = { Text(stringResource(id = R.string.workout_warmup_chip), style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(ButtonSize.heightXs)
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
            weightSuffix = WeightFormatting.unitLabel(unitSystem),
            logEnabled = !locked,
            onLog = {
                val reps = repsInput.text.toIntOrNull() ?: repsPlaceholder?.toIntOrNull()
                val enteredWeight = parseDecimal(weightInput.text) ?: weightPlaceholder?.let(::parseDecimal)
                val weight = enteredWeight?.let { WeightFormatting.convertToKg(it, unitSystem) }
                if (reps != null && reps > 0 && weight != null && weight >= 0) {
                    val submissionId = nextSubmissionId()
                    activeSubmissionId = submissionId
                    onLogSet(
                        reps,
                        weight,
                        isWarmup,
                        if (tracksIntensity) intensityInput.text else "",
                        submissionId
                    )
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

@Composable
private fun rpeColor(rpe: Double?): Color? {
    if (rpe == null) return null
    return when {
        rpe <= 7.0 -> MaterialTheme.semantic.success   // Grün
        rpe <= 8.0 -> MaterialTheme.semantic.warning    // Amber
        rpe <= 9.0 -> MaterialTheme.semantic.rose.copy(alpha = 0.85f) // Orange-Rose
        else       -> MaterialTheme.semantic.danger     // Rot für RPE 10
    }
}




