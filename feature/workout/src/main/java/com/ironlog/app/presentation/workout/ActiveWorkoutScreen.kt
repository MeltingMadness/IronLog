package com.ironlog.app.presentation.workout

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.core.designsystem.R
import com.ironlog.app.domain.util.WeightFormatting
import com.ironlog.app.domain.model.AppPreferences
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.presentation.common.IronLogScreenScaffold
import com.ironlog.app.presentation.common.LoadingScreen
import com.ironlog.shared.readinessdata.SetIntention
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

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun ActiveWorkoutScreen(
    onWorkoutFinished: () -> Unit,
    onProgressionReview: (Long) -> Unit,
    onWorkoutDetails: (Long) -> Unit = {},
    onPlanEditor: (Long) -> Unit = {},
    viewModel: ActiveWorkoutViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val appPreferencesRepository: AppPreferencesRepository = koinInject()
    val preferences by appPreferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = AppPreferences()
    )
    val snackbarHostState = remember { SnackbarHostState() }
    // Rekordmeldungen erscheinen oben, damit sie den Log-Button unten nie verdecken.
    val recordSnackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val dims = ironLogDimens
    val haptic = rememberHapticFeedback()
    val activeSession = (state.sessionPhase as? ActiveWorkoutSessionPhase.Active)?.session

    val exerciseGroups = remember(state.exercisesWithSets) {
        buildExerciseRenderGroups(state.exercisesWithSets)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            // Each message gets its own coroutine: a snackbar suspends until it is dismissed,
            // and an undo offer must not wait behind a queued record message.
            launch {
                when (event) {
                    is WorkoutEvent.NewRecords -> {
                        haptic.confirm()
                        val types = event.types.joinToString(", ") { it.displayName }
                        recordSnackbarHostState.showSnackbar(
                            message = if (event.types.size == 1) {
                                context.getString(R.string.workout_new_record_message, event.exerciseName, types)
                            } else {
                                context.getString(R.string.workout_new_records_message, event.exerciseName, types)
                            }
                        )
                    }
                    is WorkoutEvent.SetDeleted -> {
                        val result = recordSnackbarHostState.showSnackbar(
                            message = context.getString(R.string.workout_set_deleted, event.setNumber),
                            actionLabel = context.getString(R.string.common_undo),
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteSet()
                    }
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

    LaunchedEffect(state.finishState) {
        when (val finishState = state.finishState) {
            is WorkoutFinishState.ReviewReady -> Unit
            WorkoutFinishState.CompletedWithoutReview -> if (activeSession == null) onWorkoutFinished()
            else -> Unit
        }
    }

    val completionRecords by viewModel.completionRecords.collectAsStateWithLifecycle()
    LaunchedEffect(activeSession?.endTime) {
        if (activeSession?.endTime != null) viewModel.loadCompletionRecords()
    }
    if (activeSession?.endTime != null) {
        WorkoutCompletionScreen(
            session = activeSession,
            rows = state.exercisesWithSets,
            records = completionRecords,
            unitSystem = preferences.unitSystem,
            finishState = state.finishState,
            onClose = onWorkoutFinished,
            onDetails = { onWorkoutDetails(activeSession.id) },
            onPlanEditor = { activeSession.planId?.let(onPlanEditor) },
            onProgression = { onProgressionReview(activeSession.id) },
            onRetryProgression = viewModel::retryProgressionGeneration
        )
        return
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
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                activeSession?.let { session ->
                    val plannedSetCount = state.exercisesWithSets.sumOf { it.planTarget?.loggingSlots()?.size ?: 0 }
                    val openPlannedSetCount = state.exercisesWithSets.sumOf { it.openSlotCount() }
                    val loggedSetCount = state.exercisesWithSets.sumOf { it.sets.count { set -> set.reps > 0 } }
                    val loggedVolumeKg = state.exercisesWithSets.sumOf { row -> row.sets.filter { it.reps > 0 }.sumOf { it.weightKg * it.reps } }
                    WorkoutHeader(
                        startTime = session.startTime,
                        loggedSetCount = loggedSetCount,
                        plannedSetCount = plannedSetCount,
                        completedPlannedSetCount = plannedSetCount - openPlannedSetCount,
                        volumeText = WeightFormatting.formatVolume(loggedVolumeKg, preferences.unitSystem)
                    ) {
                        state.restTimers.forEach { (exerciseKey, timer) ->
                            val exerciseWithSets = state.exercisesWithSets.find { it.key == exerciseKey }
                            val group = exerciseGroups.find { it.exercises.any { ex -> ex.key == exerciseKey } }
                            val indexInSuperset = group?.exercises?.indexOfFirst { it.key == exerciseKey } ?: -1

                            RestTimer(
                                startTime = timer.startTime,
                                durationSeconds = timer.durationSeconds.toLong(),
                                onDismiss = { viewModel.dismissRestTimer(exerciseKey) },
                                onComplete = { viewModel.dismissRestTimer(exerciseKey) },
                                titleText = exerciseWithSets?.exercise?.name,
                                baseColor = supersetTintColor(group?.supersetGroupId, indexInSuperset)
                            )
                        }
                    }
                }

                // Focus: the first exercise (and its superset group) with open plan sets is
                // expanded; finished and upcoming planned exercises are collapsed. Ad-hoc
                // exercises have no known end and stay expanded. A tap on the card header
                // overrides the default for that exercise.
                val focusGroupIndex = exerciseGroups.indexOfFirst { group ->
                    group.exercises.any { it.planTarget != null && it.openSlotCount() > 0 }
                }
                var expansionOverrides by rememberSaveable { mutableStateOf(mapOf<String, Boolean>()) }
                val listState = rememberLazyListState()
                LaunchedEffect(focusGroupIndex) {
                    if (focusGroupIndex > 0) listState.animateScrollToItem(focusGroupIndex)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(dims.spacingSm),
                    contentPadding = PaddingValues(dims.spacingMd)
                ) {
                    itemsIndexed(exerciseGroups, key = { _, group -> group.key }) { groupIndex, group ->
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
                                val expansionKey = exerciseWithSets.key.stableListKey()
                                val defaultExpanded = exerciseWithSets.planTarget == null || groupIndex == focusGroupIndex
                                val expanded = expansionOverrides[expansionKey] ?: defaultExpanded
                                ExerciseCard(
                                    exerciseWithSets = exerciseWithSets,
                                    expanded = expanded,
                                    onToggleExpanded = {
                                        expansionOverrides = expansionOverrides + (expansionKey to !expanded)
                                    },
                                    nextSetRecommendation = state.nextSetRecommendations[exerciseWithSets.key],
                                    tintColor = supersetTintColor(group.supersetGroupId, indexInSuperset),
                                    defaultWarmupFlag = preferences.defaultWarmupFlag,
                                    intensitySystem = preferences.intensitySystem,
                                    unitSystem = preferences.unitSystem,
                                    plateCalculatorEnabled = preferences.plateCalculatorEnabled,
                                    availablePlates = preferences.availablePlates,
                                    barbellWeightKg = preferences.barbellWeightKg,
                                    isLogging = (state.logInFlightByExercise[exerciseWithSets.key] ?: 0) > 0,
                                    logSuccessSubmissions = state.logSuccessSubmissions,
                                    updateInFlightBySet = state.updateInFlightBySet,
                                    updateSuccessCountBySet = state.updateSuccessCountBySet,
                                    setIntentions = state.setIntentions,
                                    setIntentionsLoaded = state.setIntentionsLoaded,
                                    setIntentionsFailed = state.setIntentionsFailed,
                                    onLogSet = { reps, weight, setType, intensity, submissionId, intention ->
                                        viewModel.logSet(
                                            key = exerciseWithSets.key,
                                            exerciseId = exerciseWithSets.exercise.id,
                                            reps = reps,
                                            weightKg = weight,
                                            setType = setType,
                                            intensity = intensity,
                                            submissionId = submissionId,
                                            // A brand-new set has no stored answer to preserve:
                                            // "no deliberate choice" becomes the explicit UNKNOWN,
                                            // which stores no record.
                                            intention = intention ?: SetIntention.UNKNOWN
                                        )
                                    },
                                    onUpdateSet = viewModel::updateSet,
                                    onDeleteSet = viewModel::deleteSet,
                                    haptic = haptic
                                )
                            }
                        }
                    }
                    item {
                TextButton(
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

                    }
                }
            }
            SnackbarHost(
                hostState = recordSnackbarHostState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        if (state.showExercisePicker) {
            ExercisePickerSheet(
                onDismiss = viewModel::dismissExercisePicker,
                onExerciseSelected = { exercise ->
                    viewModel.addExercise(exercise)
                    viewModel.dismissExercisePicker()
                },
                onCreationError = viewModel::reportPickerError
            )
        }

        if (state.showFinishDialog) {
            WorkoutFinishSheet(
                rows = state.exercisesWithSets,
                busy = state.finishState != WorkoutFinishState.Idle || state.logInFlightByExercise.values.any { it > 0 } || state.updateInFlightBySet.values.any { it > 0 },
                error = state.error?.message,
                onContinue = viewModel::dismissFinishDialog,
                onFinish = viewModel::finishWorkout
            )
        }

        if (state.finishState is WorkoutFinishState.GenerationFailed) {
            AlertDialog(
                onDismissRequest = {},
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                ),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                title = { Text(stringResource(id = R.string.progression_review_title)) },
                text = {
                    Text(stringResource(id = R.string.progression_review_message_action_failed))
                },
                confirmButton = {
                    TextButton(onClick = viewModel::retryProgressionGeneration) {
                        Text(stringResource(id = R.string.common_retry))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onWorkoutFinished) {
                        Text(stringResource(id = R.string.common_later))
                    }
                }
            )
        }
    }
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
