package com.ironlog.app.presentation.workout

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.presentation.common.SetInputRow
import com.ironlog.app.presentation.common.WorkoutTimer
import com.ironlog.app.presentation.workout.PlanTarget
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    onWorkoutFinished: () -> Unit,
    viewModel: ActiveWorkoutViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WorkoutEvent.NewRecord -> {
                    snackbarHostState.showSnackbar(
                        "Neuer Rekord! ${event.exerciseName} - ${event.type.displayName}"
                    )
                }
            }
        }
    }

    // Navigate back when workout is finished
    LaunchedEffect(state.session?.endTime) {
        if (state.session?.endTime != null) {
            onWorkoutFinished()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val name = state.session?.name?.takeIf { it.isNotBlank() } ?: "Training"
                    Text(name)
                },
                actions = {
                    TextButton(onClick = viewModel::showFinishDialog) {
                        Text("Beenden", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Timer
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

            // Add exercise button
            Button(
                onClick = viewModel::showExercisePicker,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("  Übung hinzufügen", modifier = Modifier.padding(start = 4.dp))
            }

            // Exercises with sets
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                items(state.exercisesWithSets, key = { it.exercise.id }) { exerciseWithSets ->
                    ExerciseCard(
                        exerciseWithSets = exerciseWithSets,
                        onLogSet = { reps, weight, isWarmup ->
                            viewModel.logSet(exerciseWithSets.exercise.id, reps, weight, isWarmup)
                        },
                        onDeleteSet = viewModel::deleteSet
                    )
                }
            }
        }

        // Exercise picker
        if (state.showExercisePicker) {
            ExercisePickerSheet(
                onDismiss = viewModel::dismissExercisePicker,
                onExerciseSelected = { exercise ->
                    viewModel.addExercise(exercise)
                    viewModel.dismissExercisePicker()
                }
            )
        }

        // Finish dialog
        if (state.showFinishDialog) {
            AlertDialog(
                onDismissRequest = viewModel::dismissFinishDialog,
                title = { Text("Training beenden?") },
                text = { Text("Möchtest du das Training wirklich beenden?") },
                confirmButton = {
                    TextButton(onClick = viewModel::finishWorkout) {
                        Text("Beenden")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissFinishDialog) {
                        Text("Weiter trainieren")
                    }
                }
            )
        }
    }
}

@Composable
private fun ExerciseCard(
    exerciseWithSets: ExerciseWithSets,
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
            // Exercise name
            Text(
                text = exerciseWithSets.exercise.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Plan target summary
            if (planTarget != null) {
                val targetText = buildString {
                    append("Ziel: ${planTarget.targetSets} × ${planTarget.targetReps} Wdh")
                    if (planTarget.targetWeightKg > 0) {
                        append(" @ ${planTarget.targetWeightKg} kg")
                    }
                }
                Text(
                    text = targetText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (completedWorkSets >= planTarget.targetSets) {
                    Text(
                        text = "✓ Alle Sätze geschafft!",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (planTarget != null && targetSetCount > 0) {
                // ── Plan-based layout: show all target set slots ──
                for (setIndex in 1..targetSetCount) {
                    val matchingSet = loggedSets.filter { !it.isWarmup }
                        .getOrNull(setIndex - 1)

                    if (matchingSet != null) {
                        // Completed set row
                        LoggedSetRow(
                            set = matchingSet,
                            onDeleteSet = onDeleteSet
                        )
                    } else {
                        // Pending set input — pre-filled with target values
                        PendingSetRow(
                            setNumber = setIndex,
                            defaultReps = planTarget.targetReps.toString(),
                            defaultWeight = if (planTarget.targetWeightKg > 0)
                                planTarget.targetWeightKg.toString() else "",
                            onLog = { reps, weight -> onLogSet(reps, weight, false) }
                        )
                    }
                }

                // Extra sets beyond target (already logged)
                val extraSets = loggedSets.filter { !it.isWarmup }.drop(targetSetCount)
                extraSets.forEach { set ->
                    LoggedSetRow(set = set, onDeleteSet = onDeleteSet)
                }

                // Warmup sets
                loggedSets.filter { it.isWarmup }.forEach { set ->
                    LoggedSetRow(set = set, onDeleteSet = onDeleteSet)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Extra sets & warmup toggle — collapsed behind button
                var showExtraInput by remember { mutableStateOf(false) }
                AnimatedVisibility(visible = showExtraInput) {
                    ExtraSetInput(
                        planTarget = planTarget,
                        onLogSet = onLogSet
                    )
                }
                if (!showExtraInput) {
                    TextButton(onClick = { showExtraInput = true }) {
                        Text("+ Zusätzlichen Satz hinzufügen")
                    }
                }
            } else {
                // ── Free-form layout (no plan) ──
                loggedSets.forEach { set ->
                    LoggedSetRow(set = set, onDeleteSet = onDeleteSet)
                }

                Spacer(modifier = Modifier.height(8.dp))

                ExtraSetInput(
                    planTarget = null,
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
                "W ${set.setNumber}. ${set.weightKg} kg × ${set.reps}"
            } else {
                "${set.setNumber}. ${set.weightKg} kg × ${set.reps}"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = if (set.isWarmup) FontStyle.Italic else FontStyle.Normal,
            color = if (set.isWarmup) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = { onDeleteSet(set.id) }) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Löschen",
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
            text = "$setNumber.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp)
        )
        OutlinedTextField(
            value = weightInput,
            onValueChange = { weightInput = it },
            label = { Text("kg") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(80.dp),
            singleLine = true
        )
        OutlinedTextField(
            value = repsInput,
            onValueChange = { repsInput = it },
            label = { Text("Wdh") },
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
            Text("✓")
        }
    }
}

@Composable
private fun ExtraSetInput(
    planTarget: PlanTarget?,
    onLogSet: (Int, Double, Boolean) -> Unit
) {
    var repsInput by remember {
        mutableStateOf(planTarget?.let {
            if (it.targetReps > 0) it.targetReps.toString() else ""
        } ?: "")
    }
    var weightInput by remember {
        mutableStateOf(planTarget?.let {
            if (it.targetWeightKg > 0) it.targetWeightKg.toString() else ""
        } ?: "")
    }
    var isWarmup by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = isWarmup,
                onClick = { isWarmup = !isWarmup },
                label = { Text("Aufwärmsatz") }
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
