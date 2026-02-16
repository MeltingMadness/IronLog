package com.ironlog.app.presentation.workout

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.R
import com.ironlog.app.domain.model.AppPreferences
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.presentation.common.SetInputRow
import com.ironlog.app.presentation.common.WorkoutTimer
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

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

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WorkoutEvent.NewRecord -> {
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

    Scaffold(
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            state.session?.let { session ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    WorkoutTimer(startTime = session.startTime)
                }
            }

            Button(
                onClick = viewModel::showExercisePicker,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(
                    text = stringResource(id = R.string.workout_add_exercise),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(state.exercisesWithSets, key = { it.exercise.id }) { exerciseWithSets ->
                    ExerciseCard(
                        exerciseWithSets = exerciseWithSets,
                        defaultWarmupFlag = preferences.defaultWarmupFlag,
                        onLogSet = { reps, weight, isWarmup ->
                            viewModel.logSet(exerciseWithSets.exercise.id, reps, weight, isWarmup)
                        },
                        onDeleteSet = viewModel::deleteSet
                    )
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

@Composable
private fun ExerciseCard(
    exerciseWithSets: ExerciseWithSets,
    defaultWarmupFlag: Boolean,
    onLogSet: (Int, Double, Boolean) -> Unit,
    onDeleteSet: (Long) -> Unit
) {
    val planTarget = exerciseWithSets.planTarget
    val loggedSets = exerciseWithSets.sets.filter { it.reps > 0 }
    val completedWorkSets = loggedSets.count { !it.isWarmup }
    val targetSetCount = planTarget?.targetSets ?: 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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

            Spacer(modifier = Modifier.height(8.dp))

            if (planTarget != null && targetSetCount > 0) {
                for (setIndex in 1..targetSetCount) {
                    val matchingSet = loggedSets.filter { !it.isWarmup }.getOrNull(setIndex - 1)
                    if (matchingSet != null) {
                        LoggedSetRow(set = matchingSet, onDeleteSet = onDeleteSet)
                    } else {
                        PendingSetRow(
                            setNumber = setIndex,
                            defaultReps = planTarget.targetReps.toString(),
                            defaultWeight = if (planTarget.targetWeightKg > 0) {
                                planTarget.targetWeightKg.toString()
                            } else {
                                ""
                            },
                            onLog = { reps, weight -> onLogSet(reps, weight, false) }
                        )
                    }
                }

                loggedSets.filter { !it.isWarmup }.drop(targetSetCount).forEach { set ->
                    LoggedSetRow(set = set, onDeleteSet = onDeleteSet)
                }

                loggedSets.filter { it.isWarmup }.forEach { set ->
                    LoggedSetRow(set = set, onDeleteSet = onDeleteSet)
                }

                Spacer(modifier = Modifier.height(8.dp))

                var showExtraInput by remember { mutableStateOf(false) }
                AnimatedVisibility(visible = showExtraInput) {
                    ExtraSetInput(
                        planTarget = planTarget,
                        defaultWarmupFlag = defaultWarmupFlag,
                        onLogSet = onLogSet
                    )
                }
                if (!showExtraInput) {
                    TextButton(onClick = { showExtraInput = true }) {
                        Text(stringResource(id = R.string.workout_add_extra_set))
                    }
                }
            } else {
                loggedSets.forEach { set ->
                    LoggedSetRow(set = set, onDeleteSet = onDeleteSet)
                }

                Spacer(modifier = Modifier.height(8.dp))

                ExtraSetInput(
                    planTarget = null,
                    defaultWarmupFlag = defaultWarmupFlag,
                    onLogSet = onLogSet
                )
            }
        }
    }
}

@Composable
private fun LoggedSetRow(
    set: com.ironlog.app.domain.model.WorkoutSet,
    onDeleteSet: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
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
        IconButton(onClick = { onDeleteSet(set.id) }) {
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
    onLog: (Int, Double) -> Unit
) {
    var repsInput by remember { mutableStateOf(defaultReps) }
    var weightInput by remember { mutableStateOf(defaultWeight) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            modifier = Modifier.width(80.dp),
            singleLine = true
        )
        OutlinedTextField(
            value = repsInput,
            onValueChange = { repsInput = it },
            label = { Text(stringResource(id = R.string.common_reps_short)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(72.dp),
            singleLine = true
        )
        Button(
            onClick = {
                val reps = repsInput.toIntOrNull()
                val weight = weightInput.toDoubleOrNull()
                if (reps != null && reps > 0 && weight != null && weight >= 0) {
                    onLog(reps, weight)
                }
            }
        ) {
            Text(stringResource(id = R.string.common_log))
        }
    }
}

@Composable
private fun ExtraSetInput(
    planTarget: PlanTarget?,
    defaultWarmupFlag: Boolean,
    onLogSet: (Int, Double, Boolean) -> Unit
) {
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

        Spacer(modifier = Modifier.height(4.dp))

        SetInputRow(
            reps = repsInput,
            onRepsChange = { repsInput = it },
            weight = weightInput,
            onWeightChange = { weightInput = it },
            onLog = {
                val reps = repsInput.toIntOrNull()
                val weight = weightInput.toDoubleOrNull()
                if (reps != null && reps > 0 && weight != null && weight >= 0) {
                    onLogSet(reps, weight, isWarmup)
                    repsInput = ""
                    weightInput = ""
                }
            }
        )
    }
}
