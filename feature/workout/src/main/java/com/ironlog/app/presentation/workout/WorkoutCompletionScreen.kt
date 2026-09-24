package com.ironlog.app.presentation.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.ironlog.app.domain.model.*
import com.ironlog.app.domain.repository.TrainingPlanRepository
import com.ironlog.app.domain.util.WeightFormatting
import com.ironlog.shared.plans.PlannedSet
import com.ironlog.shared.plans.PlannedSets
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import org.koin.compose.koinInject

internal fun WorkoutPlanTarget.loggingSlots(): List<PlannedSet> = setTargets.ifEmpty {
    List(target.sets.coerceIn(0, 100)) { PlannedSet(reps = target.reps, weightKg = target.weightKg) }
}
internal fun ExerciseWithSets.openSlotCount(): Int {
    val slots = planTarget?.loggingSlots() ?: return 0
    return PlannedSets.matchedIndices(slots, sets.filter { it.reps > 0 }.map { it.setType.name }).count { it == null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutFinishSheet(rows: List<ExerciseWithSets>, busy: Boolean, error: String?, onContinue: () -> Unit, onFinish: () -> Unit) {
    val remaining = rows.sumOf { it.openSlotCount() }
    val planned = rows.sumOf { it.planTarget?.loggingSlots()?.size ?: 0 }
    val confirmed = rows.sumOf { row -> row.sets.count { it.reps > 0 } }
    ModalBottomSheet(onDismissRequest = { if (!busy) onContinue() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Training beenden?", style = MaterialTheme.typography.headlineSmall)
            Text(when {
                remaining > 0 -> "Noch $remaining geplante Sätze offen. ${planned - remaining} von $planned absolviert."
                confirmed == 0 -> "Du hast noch keinen Satz bestätigt. Die leere Einheit wird verworfen."
                else -> "$confirmed bestätigte Sätze werden gespeichert."
            })
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.filter { it.openSlotCount() > 0 }.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(row.exercise.name, Modifier.weight(1f)); Text("${row.openSlotCount()} offen")
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onContinue, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Weitertrainieren") }
            OutlinedButton(onClick = onFinish, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (busy) "Speichert …" else if (remaining > 0) "Trotzdem beenden" else "Training beenden") }
            Text("Nur absolvierte Sätze zählen zum Ergebnis.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutCompletionScreen(session: WorkoutSession, rows: List<ExerciseWithSets>, unitSystem: UnitSystem,
    finishState: WorkoutFinishState, onClose: () -> Unit, onDetails: () -> Unit, onPlanEditor: () -> Unit,
    onProgression: () -> Unit, onRetryProgression: () -> Unit) {
    var showPlanChanges by rememberSaveable(session.id) { mutableStateOf(false) }
    var planApplied by rememberSaveable(session.id) { mutableStateOf(false) }
    val sets = rows.flatMap { it.sets }.filter { it.reps > 0 }.distinctBy { it.id }
    val remaining = rows.sumOf { it.openSlotCount() }
    Scaffold(topBar = { TopAppBar(title = { Text("Training gespeichert") }, actions = { TextButton(onClose) { Text("Fertig") } }) },
        bottomBar = { Button(onClick = onDetails, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).heightIn(min = 48.dp)) { Text("Trainingsdetails öffnen") } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (remaining > 0) "Teiltraining gespeichert" else "Training geschafft", style = MaterialTheme.typography.headlineMedium)
            Text(if (remaining > 0) "Vorzeitig beendet · ${sets.size} bestätigte Sätze" else session.name)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryMetric("Dauer", "${session.durationSeconds / 60} min", Modifier.weight(1f))
                SummaryMetric("Sätze", sets.size.toString(), Modifier.weight(1f))
            }
            SummaryMetric("Volumen", "${formatTargetWeight(sets.sumOf { it.weightKg * it.reps }, unitSystem)}", Modifier.fillMaxWidth())
            rows.filter { it.sets.any { set -> set.reps > 0 } }.forEach { row ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(row.exercise.name, style = MaterialTheme.typography.titleMedium)
                    row.sets.filter { it.reps > 0 }.forEach { set -> Text("Satz ${set.setNumber} · ${formatTargetWeight(set.weightKg, unitSystem)} × ${set.reps}") }
                } }
            }
            if (session.planId != null && rows.any { it.planTarget != null }) {
                OutlinedButton(onClick = { showPlanChanges = true }, enabled = !planApplied, modifier = Modifier.fillMaxWidth()) {
                    Text(if (planApplied) "Satzwerte übernommen" else "Planänderungen prüfen")
                }
            }
            when (finishState) {
                is WorkoutFinishState.ReviewReady -> if (!planApplied) TextButton(onClick = onProgression) { Text("Progressionsvorschläge prüfen") }
                is WorkoutFinishState.Generating -> Text("Progressionsvorschläge werden vorbereitet …", style = MaterialTheme.typography.bodySmall)
                is WorkoutFinishState.GenerationFailed -> TextButton(onClick = onRetryProgression) { Text("Progressionsvorschläge erneut laden") }
                else -> Unit
            }
        }
    }
    if (showPlanChanges) {
        WorkoutPlanChangesSheet(session.id, rows, unitSystem, onDismiss = { showPlanChanges = false },
            onEditor = { showPlanChanges = false; onPlanEditor() }, onApplied = { planApplied = true; showPlanChanges = false })
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier) {
    Card(modifier) { Column(Modifier.padding(16.dp)) { Text(label, style = MaterialTheme.typography.labelMedium); Text(value, style = MaterialTheme.typography.headlineSmall) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutPlanChangesSheet(sessionId: Long, rows: List<ExerciseWithSets>, unitSystem: UnitSystem,
    onDismiss: () -> Unit, onEditor: () -> Unit, onApplied: () -> Unit, repository: TrainingPlanRepository = koinInject()) {
    var choice by rememberSaveable { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Planänderungen", style = MaterialTheme.typography.headlineSmall)
            Text("Das Training ist gespeichert. Was soll für das nächste Training gelten?")
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.filter { it.planTarget != null }.forEach { row ->
                    val slots = (row.originalPlanTarget ?: row.planTarget)!!.loggingSlots()
                    val recorded = row.sets.filter { it.reps > 0 }
                    PlannedSets.matchedIndices(slots, recorded.map { it.setType.name }).forEachIndexed { index, match ->
                        val today = match?.let { recorded[it] }
                        if (today != null && (today.reps != slots[index].reps || today.weightKg != slots[index].weightKg)) {
                            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                                Text("${row.exercise.name} · Satz ${index + 1}", style = MaterialTheme.typography.titleSmall)
                                Text("Plan  ${formatTargetWeight(slots[index].weightKg, unitSystem)} × ${slots[index].reps}")
                                Text("Heute  ${formatTargetWeight(today.weightKg, unitSystem)} × ${today.reps}", color = MaterialTheme.colorScheme.primary)
                            } }
                        }
                    }
                }
                val options = listOf("Nur dieses Training" to "Plan unverändert lassen", "Satzwerte übernehmen" to "Nur ausgeführte Sätze aktualisieren · individuelle Vorgaben, manuelle Progression", "Plan im Editor anpassen" to "Übungen und Vorgaben selbst bearbeiten")
                options.forEachIndexed { index, option ->
                    Surface(Modifier.fillMaxWidth().clickable(enabled = !busy) { choice = index },
                        shape = MaterialTheme.shapes.medium,
                        color = if (choice == index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, if (choice == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = choice == index, onClick = null)
                            Column(Modifier.padding(start = 12.dp)) { Text(option.first, style = MaterialTheme.typography.titleSmall); Text(option.second, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                Text("Offene Übungen und Sätze bleiben im Plan.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            Button(onClick = {
                when(choice) {
                    0 -> onDismiss()
                    2 -> onEditor()
                    else -> scope.launch {
                        busy = true
                        try { repository.applyPerformedSetTargets(sessionId); onApplied() }
                        catch (e: Exception) { if (e is CancellationException) throw e; error = e.message ?: "Plan konnte nicht geändert werden" }
                        finally { busy = false }
                    }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (busy) "Speichert …" else listOf("Plan unverändert lassen", "Satzwerte übernehmen", "Planeditor öffnen")[choice])
            }
        }
    }
}
