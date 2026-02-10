package com.ironlog.app.presentation.plans

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    // Navigate back after save
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onBack()
    }

    // Show errors
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditMode) "Plan bearbeiten" else "Neuer Plan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::savePlan) {
                        Icon(Icons.Default.Check, contentDescription = "Speichern")
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Plan name
            item {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = state.planName,
                    onValueChange = viewModel::updatePlanName,
                    label = { Text("Plan-Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Add exercise button
            item {
                TextButton(
                    onClick = viewModel::showExercisePicker,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("  Übung hinzufügen")
                }
            }

            // Exercise list
            itemsIndexed(state.exercises) { index, item ->
                PlanExerciseCard(
                    item = item,
                    index = index,
                    isFirst = index == 0,
                    isLast = index == state.exercises.size - 1,
                    onMoveUp = { viewModel.moveUp(index) },
                    onMoveDown = { viewModel.moveDown(index) },
                    onRemove = { viewModel.removeExercise(index) },
                    onSetsChange = { viewModel.updateTargetSets(index, it) },
                    onRepsChange = { viewModel.updateTargetReps(index, it) },
                    onWeightChange = { viewModel.updateTargetWeight(index, it) }
                )
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        // Exercise picker
        if (state.showExercisePicker) {
            ExercisePickerSheet(
                onDismiss = viewModel::dismissExercisePicker,
                onExerciseSelected = { exercise ->
                    viewModel.addExercise(exercise)
                }
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
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onSetsChange: (Int) -> Unit,
    onRepsChange: (Int) -> Unit,
    onWeightChange: (Double) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: name + order buttons + delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}. ${item.exercise.name}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Row {
                    IconButton(onClick = onMoveUp, enabled = !isFirst) {
                        Icon(
                            Icons.Default.ArrowDropUp,
                            contentDescription = "Nach oben"
                        )
                    }
                    IconButton(onClick = onMoveDown, enabled = !isLast) {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Nach unten"
                        )
                    }
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Entfernen",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Target values
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = if (item.planExercise.targetSets > 0) item.planExercise.targetSets.toString() else "",
                    onValueChange = { it.toIntOrNull()?.let(onSetsChange) },
                    label = { Text("Sätze") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(80.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = if (item.planExercise.targetReps > 0) item.planExercise.targetReps.toString() else "",
                    onValueChange = { it.toIntOrNull()?.let(onRepsChange) },
                    label = { Text("Wdh") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(80.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = if (item.planExercise.targetWeightKg > 0) item.planExercise.targetWeightKg.toString() else "",
                    onValueChange = { it.toDoubleOrNull()?.let(onWeightChange) },
                    label = { Text("kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(90.dp),
                    singleLine = true
                )
            }
        }
    }
}
