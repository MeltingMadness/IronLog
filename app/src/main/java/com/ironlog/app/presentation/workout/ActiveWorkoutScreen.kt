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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.presentation.common.SetInputRow
import com.ironlog.app.presentation.common.WorkoutTimer
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
                title = { Text("Training") },
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
                        onLogSet = { reps, weight ->
                            viewModel.logSet(exerciseWithSets.exercise.id, reps, weight)
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
    onLogSet: (Int, Double) -> Unit,
    onDeleteSet: (Long) -> Unit
) {
    var repsInput by remember { mutableStateOf("") }
    var weightInput by remember { mutableStateOf("") }

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

            Spacer(modifier = Modifier.height(8.dp))

            // Logged sets
            exerciseWithSets.sets.filter { it.reps > 0 }.forEach { set ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${set.setNumber}. ${set.weightKg} kg × ${set.reps}",
                        style = MaterialTheme.typography.bodyMedium
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

            Spacer(modifier = Modifier.height(8.dp))

            // Input row
            SetInputRow(
                reps = repsInput,
                onRepsChange = { repsInput = it },
                weight = weightInput,
                onWeightChange = { weightInput = it },
                onLog = {
                    val reps = repsInput.toIntOrNull()
                    val weight = weightInput.toDoubleOrNull()
                    if (reps != null && reps > 0 && weight != null && weight >= 0) {
                        onLogSet(reps, weight)
                        repsInput = ""
                        weightInput = ""
                    }
                }
            )
        }
    }
}
