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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.core.designsystem.R
import com.ironlog.feature.workout.R as WorkoutR
import com.ironlog.app.domain.model.AppPreferences
import com.ironlog.app.domain.model.IntensitySystem
import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.SetType
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.model.WorkoutPlanTarget
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.domain.util.DateFormatting
import com.ironlog.app.domain.util.WeightFormatting
import com.ironlog.app.presentation.common.HapticFeedbackHelper
import com.ironlog.app.presentation.common.IronLogScreenScaffold
import com.ironlog.app.presentation.common.IronLogSurfaceCard
import com.ironlog.app.presentation.common.IronLogSurfaceTone
import com.ironlog.app.presentation.common.LoadingScreen
import com.ironlog.app.presentation.common.PlateVisualizer
import com.ironlog.app.presentation.common.SetInputRow
import com.ironlog.shared.readinessdata.SetIntention
import kotlin.math.roundToInt
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.sp
import com.ironlog.app.presentation.theme.AthleticHero
import com.ironlog.app.presentation.theme.AthleticNumber
import com.ironlog.app.presentation.theme.AthleticLabel
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
    val context = LocalContext.current
    val dims = ironLogDimens
    val haptic = rememberHapticFeedback()
    val activeSession = (state.sessionPhase as? ActiveWorkoutSessionPhase.Active)?.session

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

    LaunchedEffect(state.finishState) {
        when (val finishState = state.finishState) {
            is WorkoutFinishState.ReviewReady -> Unit
            WorkoutFinishState.CompletedWithoutReview -> if (activeSession == null) onWorkoutFinished()
            else -> Unit
        }
    }

    if (activeSession?.endTime != null) {
        WorkoutCompletionScreen(
            session = activeSession,
            rows = state.exercisesWithSets,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        Spacer(Modifier.width(16.dp))
                        Text("${state.exercisesWithSets.sumOf { it.sets.count { set -> set.reps > 0 } }} Sätze · ${formatTargetWeight(state.exercisesWithSets.sumOf { row -> row.sets.filter { it.reps > 0 }.sumOf { it.weightKg * it.reps } }, preferences.unitSystem)}", style = MaterialTheme.typography.bodySmall)
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
                                    baseColor = supersetTintColor(group?.supersetGroupId, indexInSuperset),
                                    modifier = Modifier.padding(horizontal = dims.spacingXs)
                                )
                            }
                        }
                    }
                }
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

private fun buildExerciseRenderGroups(exercises: List<ExerciseWithSets>): List<ExerciseRenderGroup> {
    if (exercises.isEmpty()) return emptyList()

    val groups = mutableListOf<ExerciseRenderGroup>()
    var cursor = 0
    while (cursor < exercises.size) {
        val groupId = exercises[cursor].supersetGroupId
        if (groupId == null) {
            val item = exercises[cursor]
            groups += ExerciseRenderGroup(
                key = "single-${item.key.stableListKey()}",
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
                key = "single-${item.key.stableListKey()}",
                supersetGroupId = null,
                exercises = listOf(item)
            )
        } else {
            groups += ExerciseRenderGroup(
                key = "superset-$groupId-${run.joinToString("-") { it.key.stableListKey() }}",
                supersetGroupId = groupId,
                exercises = run
            )
        }

        cursor = endExclusive
    }

    return groups
}

private fun WorkoutExerciseKey.stableListKey(): String = when (this) {
    is WorkoutExerciseKey.Planned -> "planned-$snapshotId"
    is WorkoutExerciseKey.AdHoc -> "adhoc-$exerciseId"
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
    nextSetRecommendation: NextSetRecommendationUi?,
    tintColor: Color?,
    defaultWarmupFlag: Boolean,
    intensitySystem: IntensitySystem,
    unitSystem: UnitSystem,
    plateCalculatorEnabled: Boolean,
    availablePlates: List<Double>,
    barbellWeightKg: Double,
    isLogging: Boolean,
    logSuccessSubmissions: Set<Long>,
    updateInFlightBySet: Map<Long, Int>,
    updateSuccessCountBySet: Map<Long, Int>,
    setIntentions: Map<Long, SetIntention>,
    setIntentionsLoaded: Boolean,
    setIntentionsFailed: Boolean,
    onLogSet: (Int, Double, SetType, String, Long, SetIntention?) -> Unit,
    onUpdateSet: (Long, Int, Double, String, SetIntention?) -> Unit,
    onDeleteSet: (Long) -> Unit,
    haptic: HapticFeedbackHelper
) {
    val dims = ironLogDimens
    val planTarget = exerciseWithSets.planTarget
    val previousSession = exerciseWithSets.previousSession
    val rowIntensitySystem = if (
        intensitySystem == IntensitySystem.OFF && planTarget?.config is ProgressionConfig.RpeRir
    ) {
        IntensitySystem.RPE
    } else {
        intensitySystem
    }
    val showHistoryToggle = previousSession != null
    val previousWeightHint = previousSession?.lastWorkSetWeightKg?.let { formatWeightValue(it, unitSystem) }
    // RPE_RIR plan targets show what to enter into the intensity field; the
    // stored RPE is 10 - RIR when the RIR scale is active, so the hint mirrors
    // the displayed scale. A missing value would otherwise surface as
    // RPE_MISSING with no indication that the field needs input.
    val intensityPlaceholder = (planTarget?.config as? ProgressionConfig.RpeRir)?.let { config ->
        val hint = when (rowIntensitySystem) {
            IntensitySystem.RIR -> 10.0 - config.targetRpe
            else -> config.targetRpe
        }
        formatRpeValue(hint)
    }
    var showPreviousSession by remember(exerciseWithSets.key) { mutableStateOf(false) }
    val loggedSets = exerciseWithSets.sets.filter { it.reps > 0 }
    val completedWorkSets = loggedSets.count { it.setType == SetType.NORMAL }
    val slots = planTarget?.loggingSlots().orEmpty()
    val targetSetCount = slots.size

    IronLogSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        tone = IronLogSurfaceTone.MUTED,
        border = tintColor?.let { BorderStroke(1.dp, it.copy(alpha = 0.3f)) },
        alpha = 1f
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
                val targetText = if (planTarget.target.weightKg > 0) {
                    stringResource(
                        id = WorkoutR.string.workout_audit_plan_target_with_weight,
                        planTarget.target.sets,
                        planTarget.target.reps,
                        formatTargetWeight(planTarget.target.weightKg, unitSystem)
                    )
                } else {
                    stringResource(
                        id = WorkoutR.string.workout_audit_plan_target_no_weight,
                        planTarget.target.sets,
                        planTarget.target.reps
                    )
                }
                Text(
                    text = targetText,
                    style = MaterialTheme.typography.bodySmall,
                    color = tintColor ?: MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(
                        id = R.string.workout_progression_scheme,
                        progressionSchemeLabel(planTarget.config)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (previousSession?.lastWorkSetReachedTarget == true) {
                    Text(
                        text = stringResource(id = R.string.workout_previous_last_set_target_reached),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.semantic.success
                    )
                }
                if (completedWorkSets >= planTarget.target.sets) {
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
                val matched = com.ironlog.shared.plans.PlannedSets.matchedIndices(slots, loggedSets.map { it.setType.name })
                val nextSlotIndex = matched.indexOfFirst { it == null }

                for (setIndex in 1..targetSetCount) {
                    val slot = slots[setIndex - 1]
                    val matchingSet = matched[setIndex - 1]?.let { loggedSets[it] }
                    if (matchingSet != null) {
                        LoggedSetRow(
                            set = matchingSet,
                            intention = setIntentions[matchingSet.id],
                            intentionFailed = setIntentionsFailed,
                            intensitySystem = rowIntensitySystem,
                            unitSystem = unitSystem,
                            plateCalculatorEnabled = plateCalculatorEnabled,
                            availablePlates = availablePlates,
                            barbellWeightKg = barbellWeightKg,
                            isUpdating = (updateInFlightBySet[matchingSet.id] ?: 0) > 0,
                            updateSuccessCount = updateSuccessCountBySet[matchingSet.id] ?: 0,
                            onUpdateSet = onUpdateSet,
                            onDeleteSet = onDeleteSet,
                            haptic = haptic
                        )
                    } else {
                        val isNextToLog = setIndex - 1 == nextSlotIndex
                        if (isNextToLog) {
                            val coachHint = nextSetRecommendation?.recommendedWeightKg?.let {
                                stringResource(
                                    WorkoutR.string.workout_audit_coach_suggestion,
                                    "${formatWeightValue(it, unitSystem)} ${WeightFormatting.unitLabel(unitSystem)}"
                                )
                            }
                            ActiveSetCockpitCard(
                                setNumber = setIndex,
                                setType = if (slot.kind == "WARMUP") SetType.WARMUP else SetType.NORMAL,
                                isExtraOrAdHoc = false,
                                isEditMode = false,
                                coachHint = coachHint,
                                valueSource = if (planTarget.setTargets.isEmpty() && loggedSets.any { it.setType == SetType.NORMAL }) "Werte aus dem letzten Satz übernommen · editierbar" else "Werte aus dem Plan · editierbar",
                                coachRecommendation = nextSetRecommendation,
                                defaultWeight = formatWeightValue(if (planTarget.setTargets.isEmpty()) loggedSets.lastOrNull { it.setType == SetType.NORMAL }?.weightKg ?: slot.weightKg else slot.weightKg, unitSystem),
                                weightPlaceholder = targetWeightHint(planTarget, unitSystem, previousWeightHint),
                                defaultReps = (if (planTarget.setTargets.isEmpty()) loggedSets.lastOrNull { it.setType == SetType.NORMAL }?.reps ?: slot.reps else slot.reps).toString(),
                                repsPlaceholder = if (planTarget.target.reps > 0) planTarget.target.reps.toString() else null,
                                defaultIntensity = "",
                                intensityPlaceholder = intensityPlaceholder,
                                intensitySystem = rowIntensitySystem,
                                unitSystem = unitSystem,
                                locked = isLogging,
                                completedSubmissions = logSuccessSubmissions,
                                plateCalculatorEnabled = plateCalculatorEnabled,
                                availablePlates = availablePlates,
                                barbellWeightKg = barbellWeightKg,
                                haptic = haptic,
                                intentionFailed = setIntentionsFailed,
                                onLog = { reps, weight, setType, intensity, submissionId, intention ->
                                    onLogSet(reps, weight, setType, intensity, submissionId, intention)
                                }
                            )
                        } else {
                            PlannedSetPreviewRow(
                                setNumber = setIndex,
                                targetWeight = formatWeightValue(slot.weightKg, unitSystem),
                                targetReps = slot.reps.toString(),
                                unitSystem = unitSystem
                            )
                        }
                    }
                }

                loggedSets.filterIndexed { index, _ -> index !in matched.filterNotNull() }.forEach { set ->
                    LoggedSetRow(
                        set = set,
                        intention = setIntentions[set.id],
                        intentionFailed = setIntentionsFailed,
                        intensitySystem = rowIntensitySystem,
                        unitSystem = unitSystem,
                        plateCalculatorEnabled = plateCalculatorEnabled,
                        availablePlates = availablePlates,
                        barbellWeightKg = barbellWeightKg,
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
                    val nextExtraSetNumber = loggedSets.size + 1
                    ActiveSetCockpitCard(
                        setNumber = nextExtraSetNumber,
                        setType = if (defaultWarmupFlag) SetType.WARMUP else SetType.NORMAL,
                        isExtraOrAdHoc = true,
                        isEditMode = false,
                        coachHint = null,
                        defaultWeight = loggedSets.lastOrNull()?.let { formatWeightValue(it.weightKg, unitSystem) } ?: targetWeightHint(planTarget, unitSystem, previousWeightHint).orEmpty(),
                        weightPlaceholder = targetWeightHint(planTarget, unitSystem, previousWeightHint),
                        defaultReps = (loggedSets.lastOrNull()?.reps ?: planTarget.target.reps).toString(),
                        repsPlaceholder = if (planTarget.target.reps > 0) planTarget.target.reps.toString() else null,
                        defaultIntensity = "",
                        intensityPlaceholder = intensityPlaceholder,
                        intensitySystem = rowIntensitySystem,
                        unitSystem = unitSystem,
                        locked = isLogging,
                        completedSubmissions = logSuccessSubmissions,
                        plateCalculatorEnabled = plateCalculatorEnabled,
                        availablePlates = availablePlates,
                        barbellWeightKg = barbellWeightKg,
                        haptic = haptic,
                        intentionFailed = setIntentionsFailed,
                        onLog = { reps, weight, setType, intensity, submissionId, intention ->
                            onLogSet(reps, weight, setType, intensity, submissionId, intention)
                        },
                        onCancelEdit = { showExtraInput = false }
                    )
                }
                if (!showExtraInput) {
                    TextButton(onClick = { showExtraInput = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(id = R.string.workout_add_extra_set))
                    }
                }
            } else {
                loggedSets.forEach { set ->
                    LoggedSetRow(
                        set = set,
                        intention = setIntentions[set.id],
                        intentionFailed = setIntentionsFailed,
                        intensitySystem = rowIntensitySystem,
                        unitSystem = unitSystem,
                        plateCalculatorEnabled = plateCalculatorEnabled,
                        availablePlates = availablePlates,
                        barbellWeightKg = barbellWeightKg,
                        isUpdating = (updateInFlightBySet[set.id] ?: 0) > 0,
                        updateSuccessCount = updateSuccessCountBySet[set.id] ?: 0,
                        onUpdateSet = onUpdateSet,
                        onDeleteSet = onDeleteSet,
                        haptic = haptic
                    )
                }

                Spacer(modifier = Modifier.height(dims.spacingXs))

                val nextSetNumber = loggedSets.size + 1
                ActiveSetCockpitCard(
                    setNumber = nextSetNumber,
                    setType = if (defaultWarmupFlag) SetType.WARMUP else SetType.NORMAL,
                    isExtraOrAdHoc = true,
                    isEditMode = false,
                    coachHint = null,
                    defaultWeight = loggedSets.lastOrNull()?.let { formatWeightValue(it.weightKg, unitSystem) } ?: previousWeightHint.orEmpty(),
                    weightPlaceholder = previousWeightHint,
                    defaultReps = loggedSets.lastOrNull()?.reps?.toString().orEmpty(),
                    repsPlaceholder = null,
                    defaultIntensity = "",
                    intensityPlaceholder = intensityPlaceholder,
                    intensitySystem = rowIntensitySystem,
                    unitSystem = unitSystem,
                    locked = isLogging,
                    completedSubmissions = logSuccessSubmissions,
                    plateCalculatorEnabled = plateCalculatorEnabled,
                    availablePlates = availablePlates,
                    barbellWeightKg = barbellWeightKg,
                    haptic = haptic,
                    intentionFailed = setIntentionsFailed,
                    onLog = { reps, weight, setType, intensity, submissionId, intention ->
                        onLogSet(reps, weight, setType, intensity, submissionId, intention)
                    }
                )
            }

            nextSetRecommendation?.let { recommendation ->
                NextSetRecommendationChips(
                    recommendation = recommendation,
                    unitSystem = unitSystem,
                    modifier = Modifier.padding(top = dims.spacingXs)
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
                        intensitySystem = rowIntensitySystem,
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
            text = stringResource(id = WorkoutR.string.workout_audit_previous_session_title, dateLabel),
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
    val setLabel = setTypeLabel(set.setNumber, set.setType)

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

private fun formatRpeValue(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.ROOT, "%.1f", value)
    }

/**
 * Weight hint for plan-based workout rows: the current plan target weight
 * first (the plan is the source of truth after a progression step), falling
 * back to the last trained weight when the plan carries no weight target.
 * With actual-weight-based progression both sources yield valid suggestions.
 */
internal fun targetWeightHint(
    planTarget: WorkoutPlanTarget?,
    unitSystem: UnitSystem,
    previousWeightHint: String?
): String? = planTarget
    ?.takeIf { it.target.weightKg > 0 }
    ?.let { formatWeightValue(it.target.weightKg, unitSystem) }
    ?: previousWeightHint

@Composable
private fun NextSetRecommendationChips(
    recommendation: NextSetRecommendationUi,
    unitSystem: UnitSystem,
    modifier: Modifier = Modifier
) {
    val dims = ironLogDimens
    val weightText = formatWeightValue(recommendation.recommendedWeightKg, unitSystem)
    val delta = recommendation.recommendedWeightKg - recommendation.lastWeightKg
    val loadText = if (kotlin.math.abs(delta) < 0.05) {
        stringResource(id = R.string.workout_autoregulation_next_set, weightText)
    } else {
        stringResource(
            id = R.string.workout_autoregulation_next_set_delta,
            weightText,
            WeightFormatting.formatWeightDelta(delta, unitSystem)
        )
    }
    val accent = if (recommendation.isOvershoot) {
        MaterialTheme.semantic.danger
    } else {
        MaterialTheme.semantic.sky
    }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dims.spacingXs),
        verticalArrangement = Arrangement.spacedBy(dims.spacingXs)
    ) {
        recommendation.targetRpe?.let { target ->
            RecommendationPill(
                text = stringResource(id = R.string.workout_autoregulation_target_rpe, formatRpeValue(target)),
                color = MaterialTheme.semantic.sky
            )
        }
        if (recommendation.isOvershoot) {
            RecommendationPill(
                text = stringResource(
                    id = R.string.workout_autoregulation_overshoot,
                    formatRpeValue(recommendation.lastRpe)
                ),
                color = MaterialTheme.semantic.danger
            )
        }
        RecommendationPill(
            text = stringResource(WorkoutR.string.workout_audit_coach_suggestion, loadText),
            color = accent
        )
        recommendation.backoffWeightKg?.let { backoff ->
            RecommendationPill(
                text = stringResource(
                    id = R.string.workout_autoregulation_backoff_set,
                    formatWeightValue(backoff, unitSystem)
                ),
                color = MaterialTheme.semantic.danger
            )
        }
    }
}

@Composable
private fun RecommendationPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Formats a plan target weight stored in kg for display in the user's preferred unit system, e.g. "100.0 kg" or "220.5 lb". */
fun formatTargetWeight(weightKg: Double, unitSystem: UnitSystem): String {
    val displayValue = WeightFormatting.convertToDisplay(weightKg, unitSystem)
    return String.format(Locale.ROOT, "%.1f %s", displayValue, WeightFormatting.unitLabel(unitSystem))
}

private fun setTypeLabel(setNumber: Int, setType: SetType): String = when (setType) {
    SetType.NORMAL -> setNumber.toString()
    SetType.WARMUP -> "W$setNumber"
    SetType.DROP_SET -> "D$setNumber"
    SetType.FAILURE -> "F$setNumber"
}

private fun SetType.labelRes(): Int = when (this) {
    SetType.NORMAL -> R.string.workout_set_type_normal
    SetType.WARMUP -> R.string.workout_warmup_chip
    SetType.DROP_SET -> R.string.workout_set_type_drop_set
    SetType.FAILURE -> R.string.workout_set_type_failure
}

private fun SetIntention.labelRes(): Int = when (this) {
    SetIntention.UNKNOWN -> R.string.workout_set_intention_unknown
    SetIntention.PLANNED_FAILURE -> R.string.workout_set_intention_planned_failure
    SetIntention.UNEXPECTED_TARGET_MISS -> R.string.workout_set_intention_unexpected_miss
}

/**
 * Tri-state selector for a set's intention, stacked vertically so the long German labels
 * always wrap instead of being clipped. The selection is nullable: `null` means "nothing
 * stored or not read yet" and highlights no chip, while [SetIntention.UNKNOWN] is an
 * explicit "no answer" choice that clears any stored record.
 */
@Composable
private fun SetIntentionChipRow(
    selected: SetIntention?,
    onSelect: (SetIntention) -> Unit
) {
    val options = listOf(
        SetIntention.UNKNOWN,
        SetIntention.PLANNED_FAILURE,
        SetIntention.UNEXPECTED_TARGET_MISS
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                onClick = { onSelect(option) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
                border = BorderStroke(
                    1.dp,
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(option.labelRes()),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
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
private fun CockpitBigNumberBox(
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholderText: String,
    onStepMinus: () -> Unit,
    onStepPlus: () -> Unit,
    minusStepText: String,
    plusStepText: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    imeAction: ImeAction = ImeAction.Next
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = AthleticLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = AthleticHero.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) {
                        if (value.text.isEmpty()) {
                            Text(
                                text = placeholderText,
                                style = AthleticHero.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    onClick = onStepMinus,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = minusStepText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    onClick = onStepPlus,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = plusStepText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannedSetPreviewRow(
    setNumber: Int,
    targetWeight: String?,
    targetReps: String?,
    unitSystem: UnitSystem,
    modifier: Modifier = Modifier
) {
    val dims = ironLogDimens
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = dims.spacingXs),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = setNumber.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val targetDesc = buildString {
                if (targetWeight != null) {
                    append(targetWeight)
                    append(" ")
                    append(WeightFormatting.unitLabel(unitSystem))
                }
                if (targetReps != null) {
                    if (isNotEmpty()) append(" × ")
                    append(targetReps)
                    append(" ")
                    append(stringResource(R.string.common_reps_short))
                }
            }

            Text(
                text = stringResource(R.string.workout_planned_set_preview, setNumber, targetDesc.ifEmpty { "-" }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ActiveSetCockpitCard(
    setNumber: Int,
    setType: SetType = SetType.NORMAL,
    onSetTypeChange: ((SetType) -> Unit)? = null,
    isExtraOrAdHoc: Boolean = false,
    isEditMode: Boolean = false,
    coachHint: String? = null,
    valueSource: String? = null,
    coachRecommendation: NextSetRecommendationUi? = null,
    defaultWeight: String = "",
    weightPlaceholder: String? = null,
    defaultReps: String = "",
    repsPlaceholder: String? = null,
    defaultIntensity: String = "",
    intensityPlaceholder: String? = null,
    /**
     * The stored answer to prefill, or `null` when nothing is stored or the readiness
     * channel has not been read. `null` is never rendered as "Nicht angegeben", so an
     * unread channel cannot masquerade as a deliberate answer.
     */
    defaultIntention: SetIntention? = null,
    intentionLoaded: Boolean = false,
    intentionFailed: Boolean = false,
    intensitySystem: IntensitySystem,
    unitSystem: UnitSystem,
    locked: Boolean = false,
    completedSubmissions: Set<Long> = emptySet(),
    plateCalculatorEnabled: Boolean,
    availablePlates: List<Double>,
    barbellWeightKg: Double,
    haptic: com.ironlog.app.presentation.common.HapticFeedbackHelper,
    onLog: (Int, Double, SetType, String, Long, SetIntention?) -> Unit,
    onCancelEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dims = ironLogDimens
    var showDetails by remember(setNumber) { mutableStateOf(isEditMode) }
    val tracksIntensity = intensitySystem != IntensitySystem.OFF
    val weightStep = if (unitSystem == UnitSystem.IMPERIAL) 5.0 else 2.5
    val weightStepText = if (weightStep % 1.0 == 0.0) weightStep.toInt().toString() else weightStep.toString()
    val weightSuffix = WeightFormatting.unitLabel(unitSystem)

    var currentSetType by remember(setNumber, setType) { mutableStateOf(setType) }
    var currentIntention by remember(setNumber) { mutableStateOf(defaultIntention) }
    var intentionDirty by remember(setNumber) { mutableStateOf(false) }
    // Adopt a late-arriving stored answer only while the user has not chosen one.
    LaunchedEffect(defaultIntention) {
        if (!intentionDirty) currentIntention = defaultIntention
    }
    var weightInput by remember(setNumber, defaultWeight) {
        mutableStateOf(TextFieldValue(defaultWeight, TextRange(defaultWeight.length)))
    }
    var repsInput by remember(setNumber, defaultReps) {
        mutableStateOf(TextFieldValue(defaultReps, TextRange(defaultReps.length)))
    }
    var intensityInput by remember(setNumber, defaultIntensity) {
        mutableStateOf(TextFieldValue(defaultIntensity, TextRange(defaultIntensity.length)))
    }
    var activeSubmissionId by remember(setNumber) { mutableStateOf<Long?>(null) }

    LaunchedEffect(activeSubmissionId, completedSubmissions) {
        val submissionId = activeSubmissionId ?: return@LaunchedEffect
        if (submissionId in completedSubmissions) {
            if (!isEditMode) {
                weightInput = TextFieldValue("", TextRange.Zero)
                repsInput = TextFieldValue("", TextRange.Zero)
                intensityInput = TextFieldValue("", TextRange.Zero)
            }
            activeSubmissionId = null
            haptic.confirm()
        }
    }

    val adjustWeight: (Double) -> Unit = { delta ->
        val current = parseDecimal(weightInput.text) ?: weightPlaceholder?.let(::parseDecimal) ?: 0.0
        val next = maxOf(0.0, ((current + delta) * 100.0).roundToInt() / 100.0)
        val text = if (next % 1.0 == 0.0) next.toInt().toString() else next.toString()
        weightInput = TextFieldValue(text, TextRange(text.length))
        haptic.tick()
    }

    val adjustReps: (Int) -> Unit = { delta ->
        val current = repsInput.text.toIntOrNull() ?: repsPlaceholder?.toIntOrNull() ?: 0
        val next = maxOf(0, current + delta)
        val text = if (next > 0) next.toString() else ""
        repsInput = TextFieldValue(text, TextRange(text.length))
        haptic.tick()
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = dims.spacingXs),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Set tag + Coach badge
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tagText = when {
                    isEditMode -> stringResource(R.string.workout_active_set_edit_badge, setNumber)
                    currentSetType == SetType.WARMUP ->
                        stringResource(WorkoutR.string.workout_audit_warmup_set, setNumber)
                    currentSetType == SetType.DROP_SET ->
                        stringResource(WorkoutR.string.workout_audit_drop_set, setNumber)
                    currentSetType == SetType.FAILURE ->
                        stringResource(WorkoutR.string.workout_audit_failure_set, setNumber)
                    else -> stringResource(R.string.workout_active_set_badge, setNumber)
                }
                Text(
                    text = tagText,
                    style = AthleticLabel,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold
                )

                if (coachHint != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = coachHint,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            coachRecommendation?.let { recommendation ->
                                val suggestedWeight = formatWeightValue(
                                    recommendation.recommendedWeightKg,
                                    unitSystem
                                )
                                TextButton(
                                    onClick = {
                                        val nextValue = TextFieldValue(
                                            suggestedWeight,
                                            TextRange(suggestedWeight.length)
                                        )
                                        // Applying a coach suggestion is an explicit action. It
                                        // updates only the current draft and never overwrites a
                                        // saved or in-flight set by itself.
                                        weightInput = nextValue
                                        haptic.tick()
                                    },
                                    enabled = !locked && activeSubmissionId == null,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = stringResource(WorkoutR.string.workout_audit_apply_coach_weight),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            valueSource?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = weightInput, onValueChange = { weightInput = it },
                    label = { Text(weightSuffix) }, singleLine = true,
                    modifier = Modifier.weight(1f), enabled = !locked,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                )
                androidx.compose.material3.OutlinedTextField(
                    value = repsInput, onValueChange = { repsInput = it },
                    label = { Text("Wdh.") }, singleLine = true,
                    modifier = Modifier.weight(1f), enabled = !locked,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
                )
            }
            TextButton(onClick = { showDetails = !showDetails }) {
                Text(if (showDetails) "Details schließen" else "RPE / RIR · Satztyp · Absicht · Scheiben")
            }
            if (showDetails) {
            // Set Type Selector (if Extra or Ad-Hoc)
            if (isExtraOrAdHoc) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SetType.entries.forEach { type ->
                        val selected = currentSetType == type
                        Surface(
                            onClick = {
                                currentSetType = type
                                onSetTypeChange?.invoke(type)
                                haptic.tick()
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(id = type.labelRes()),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Intensity Selector Row (RPE / RIR Chips)
            if (tracksIntensity) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = intensitySystem.displayName.uppercase(),
                            style = AthleticLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        if (intensityInput.text.isNotEmpty()) {
                            val rpeVal = intensityInput.text.toDoubleOrNull()
                            val accent = rpeColor(rpeVal) ?: MaterialTheme.colorScheme.primary
                            Text(
                                text = "${intensitySystem.displayName} ${intensityInput.text}",
                                style = AthleticLabel,
                                color = accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (intensitySystem == IntensitySystem.RPE) {
                        val rpeChips = listOf(7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            rpeChips.forEach { rpe ->
                                val rpeStr = if (rpe % 1.0 == 0.0) rpe.toInt().toString() else rpe.toString()
                                val isSelected = intensityInput.text == rpeStr
                                val chipColor = rpeColor(rpe) ?: MaterialTheme.colorScheme.primary

                                Surface(
                                    onClick = {
                                        intensityInput = if (isSelected) {
                                            TextFieldValue("", TextRange.Zero)
                                        } else {
                                            TextFieldValue(rpeStr, TextRange(rpeStr.length))
                                        }
                                        haptic.tick()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) chipColor else chipColor.copy(alpha = 0.12f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) chipColor else chipColor.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = rpeStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else chipColor
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val rirChips = listOf(0, 1, 2, 3, 4)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rirChips.forEach { rir ->
                                val rirStr = rir.toString()
                                val isSelected = intensityInput.text == rirStr
                                Surface(
                                    onClick = {
                                        intensityInput = if (isSelected) {
                                            TextFieldValue("", TextRange.Zero)
                                        } else {
                                            TextFieldValue(rirStr, TextRange(rirStr.length))
                                        }
                                        haptic.tick()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = stringResource(
                                                WorkoutR.string.workout_audit_rir_value,
                                                rirStr
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Embedded Plate Visualizer (zero disruption when disabled)
            if (plateCalculatorEnabled) {
                val parsedW = parseDecimal(weightInput.text) ?: weightPlaceholder?.let(::parseDecimal) ?: 0.0
                val targetWeightKg = WeightFormatting.convertToKg(parsedW, unitSystem)
                val sideWeight = maxOf(0.0, (targetWeightKg - barbellWeightKg) / 2.0)
                val sideWeightDisplay = String.format(
                    Locale.ROOT,
                    "%.2f %s",
                    WeightFormatting.convertToDisplay(sideWeight, unitSystem),
                    WeightFormatting.unitLabel(unitSystem)
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.workout_barbell_loading_side, sideWeightDisplay).uppercase(),
                                style = AthleticLabel,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        PlateVisualizer(
                            targetWeightKg = targetWeightKg,
                            barbellWeightKg = barbellWeightKg,
                            availablePlates = availablePlates,
                            unitSystem = unitSystem,
                            enabled = true
                        )
                    }
                }
            }

            // Log / Update Action Button
            // Set intention: an answer to *why* the set ended, independent of the set
            // type above. UNKNOWN is the default and stores no record.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.workout_set_intention_label).uppercase(),
                        style = AthleticLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Text(
                        text = stringResource(R.string.workout_set_intention_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(start = dims.spacingSm)
                    )
                }
                SetIntentionChipRow(
                    selected = currentIntention,
                    onSelect = { intention ->
                        currentIntention = intention
                        intentionDirty = true
                        haptic.tick()
                    }
                )
                if (intentionFailed) {
                    Text(
                        text = stringResource(R.string.workout_set_intention_load_failed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            }

            val enteredWeight = if (weightInput.text.isBlank()) null else parseDecimal(weightInput.text)
            val enteredReps = repsInput.text.toIntOrNull()
            val canLog = !locked && activeSubmissionId == null &&
                enteredReps != null && enteredReps > 0 &&
                enteredWeight != null && enteredWeight.isFinite() && enteredWeight >= 0

            Button(
                onClick = {
                    val reps = repsInput.text.toIntOrNull() ?: repsPlaceholder?.toIntOrNull()
                    val weightVal = parseDecimal(weightInput.text) ?: weightPlaceholder?.let(::parseDecimal)
                    val weightKg = weightVal?.let { WeightFormatting.convertToKg(it, unitSystem) }
                    if (reps != null && reps > 0 && weightKg != null && weightKg >= 0 && weightKg.isFinite()) {
                        val submissionId = nextSubmissionId()
                        activeSubmissionId = submissionId
                        haptic.confirm()
                        onLog(
                            reps,
                            weightKg,
                            currentSetType,
                            if (intensitySystem != IntensitySystem.OFF) intensityInput.text else "",
                            submissionId,
                            // null = the user made no deliberate choice, so an edit keeps
                            // the stored answer instead of erasing it.
                            if (intentionDirty) currentIntention else null
                        )
                    }
                },
                enabled = canLog,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val btnText = when {
                    isEditMode -> stringResource(R.string.workout_update_set_button, setNumber)
                    enteredWeight != null && enteredReps != null -> {
                        val wStr = if (enteredWeight % 1.0 == 0.0) enteredWeight.toInt().toString() else enteredWeight.toString()
                        stringResource(R.string.workout_log_set_button, setNumber, wStr, weightSuffix, enteredReps)
                    }
                    else -> stringResource(R.string.workout_log_set_button_short, setNumber)
                }
                Text(
                    text = btnText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isEditMode && onCancelEdit != null) {
                TextButton(
                    onClick = onCancelEdit,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(id = R.string.common_cancel))
                }
            }
        }
    }
}

@Composable
private fun LoggedSetRow(
    set: com.ironlog.app.domain.model.WorkoutSet,
    /**
     * Stored answer, or `null` when nothing is stored / the readiness channel was not read.
     * `null` leaves the edit sheet without a preselected chip, while [SetIntention.UNKNOWN]
     * is an explicit "no answer" that is likewise never rendered as data.
     */
    intention: SetIntention?,
    intentionFailed: Boolean = false,
    intensitySystem: com.ironlog.app.domain.model.IntensitySystem,
    unitSystem: UnitSystem,
    plateCalculatorEnabled: Boolean,
    availablePlates: List<Double>,
    barbellWeightKg: Double,
    isUpdating: Boolean,
    updateSuccessCount: Int,
    onUpdateSet: (Long, Int, Double, String, SetIntention?) -> Unit,
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

    LaunchedEffect(updateSuccessCount) {
        if (updateSuccessCount > 0 && isEditing) {
            isEditing = false
            haptic.confirm()
        }
    }

    if (isEditing) {
        ActiveSetCockpitCard(
            setNumber = set.setNumber,
            setType = set.setType,
            isExtraOrAdHoc = false,
            isEditMode = true,
            defaultWeight = weightText,
            weightPlaceholder = weightText,
            defaultReps = set.reps.toString(),
            repsPlaceholder = set.reps.toString(),
            defaultIntensity = intensityText,
            intensityPlaceholder = intensityText,
            defaultIntention = intention,
            intentionFailed = intentionFailed,
            intensitySystem = intensitySystem,
            unitSystem = unitSystem,
            locked = isUpdating,
            plateCalculatorEnabled = plateCalculatorEnabled,
            availablePlates = availablePlates,
            barbellWeightKg = barbellWeightKg,
            haptic = haptic,
            onLog = { reps, weightKg, _, intensityStr, _, chosenIntention ->
                onUpdateSet(set.id, reps, weightKg, intensityStr, chosenIntention)
            },
            onCancelEdit = { isEditing = false }
        )
    } else {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dims.spacingXs)
                .clickable {
                    isEditing = true
                    haptic.confirm()
                },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Completed set number badge
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            color = MaterialTheme.semantic.success.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = setTypeLabel(set.setNumber, set.setType),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.semantic.success
                    )
                }

                // Set Weight and Reps
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "$weightText ${WeightFormatting.unitLabel(unitSystem)} × ${set.reps} ${stringResource(R.string.common_reps_short)}",
                            style = AthleticNumber,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (set.isWarmup) {
                            Text(
                                text = "(${stringResource(R.string.workout_warmup_chip)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }

                    // Only a known answer is shown; a set without a record stays UNKNOWN
                    // instead of displaying "Nicht angegeben" as if it were data.
                    if (intention != null && intention != SetIntention.UNKNOWN) {
                        val intentionLabel = stringResource(intention.labelRes())
                        val intentionDescription = stringResource(
                            R.string.workout_set_intention_chip_cd,
                            intentionLabel
                        )
                        Text(
                            text = intentionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontStyle = FontStyle.Italic,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics {
                                contentDescription = intentionDescription
                            }
                        )
                    }
                }

                // RPE chip
                if (tracksIntensity && intensityText.isNotEmpty()) {
                    val accentColor = rpeColor(set.rpe) ?: MaterialTheme.colorScheme.primary
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accentColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "${intensitySystem.displayName} $intensityText",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Success checkmark icon
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.semantic.success,
                    modifier = Modifier.size(18.dp)
                )

                // Delete button
                IconButton(
                    onClick = {
                        haptic.reject()
                        onDeleteSet(set.id)
                    },
                    modifier = Modifier.size(ButtonSize.iconButton)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(id = R.string.workout_delete_set_cd),
                        tint = MaterialTheme.semantic.danger.copy(alpha = 0.75f),
                        modifier = Modifier.size(IconSize.sm)
                    )
                }
            }
        }
    }
}

@Composable
private fun progressionSchemeLabel(config: ProgressionConfig): String = when (config) {
    is ProgressionConfig.Manual -> stringResource(R.string.workout_progression_manual)
    is ProgressionConfig.Linear -> stringResource(R.string.workout_progression_linear)
    is ProgressionConfig.DoubleProgression -> stringResource(R.string.workout_progression_double)
    is ProgressionConfig.TotalReps -> stringResource(R.string.workout_progression_total_reps)
    is ProgressionConfig.RpeRir -> stringResource(R.string.workout_progression_rpe_rir)
    is ProgressionConfig.Invalid -> stringResource(R.string.workout_progression_invalid)
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
