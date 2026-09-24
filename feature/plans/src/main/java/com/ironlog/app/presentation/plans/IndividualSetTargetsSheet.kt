package com.ironlog.app.presentation.plans

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ironlog.shared.plans.PlannedSet
import com.ironlog.shared.plans.PlannedSets
import com.ironlog.app.domain.util.WeightFormatting

internal fun plannedSetLabel(kind: String) = when(kind) { "WARMUP" -> "Aufwärmen"; "BACKOFF" -> "Backoff"; else -> "Arbeitssatz" }
private data class SetDraft(val kind: String, val weight: String, val reps: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IndividualSetTargetsSheet(item: PlanExerciseUi, onDismiss: () -> Unit, onApply: (List<PlannedSet>) -> Unit) {
    val unit = item.targetWeightInputUnit
    var rows by remember {
        val targets = item.planExercise.setTargets.ifEmpty {
            List((item.targetSetsInput.toIntOrNull() ?: 3).coerceIn(1, 100)) {
                PlannedSet(reps = item.targetRepsInput.toIntOrNull() ?: 10,
                    weightKg = item.targetWeightInput.replace(',', '.').toDoubleOrNull()?.let { WeightFormatting.convertToKg(it, unit) } ?: item.planExercise.targetWeightKg)
            }
        }
        mutableStateOf(targets.map { SetDraft(it.kind, WeightFormatting.convertToDisplay(it.weightKg, unit).toString(), it.reps.toString()) })
    }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(20.dp)) {
            Text("Satzvorgaben", style = MaterialTheme.typography.headlineSmall)
            Text(item.exercise.name, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SATZ / TYP", Modifier.weight(1.3f)); Text(WeightFormatting.unitLabel(unit), Modifier.weight(1f)); Text("WDH.", Modifier.weight(1f))
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEachIndexed { index, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        var expanded by remember { mutableStateOf(false) }
                        Box(Modifier.weight(1.3f)) {
                            TextButton(onClick = { expanded = true }) { Text("${index + 1} · ${plannedSetLabel(row.kind)}") }
                            DropdownMenu(expanded, { expanded = false }) {
                                listOf("NORMAL", "WARMUP", "BACKOFF").forEach { kind ->
                                    DropdownMenuItem(text = { Text(plannedSetLabel(kind)) }, onClick = { rows = rows.toMutableList().also { it[index] = row.copy(kind = kind) }; expanded = false })
                                }
                                DropdownMenuItem(text = { Text("Entfernen") }, onClick = { rows = rows.filterIndexed { i, _ -> i != index }; expanded = false })
                            }
                        }
                        OutlinedTextField(row.weight, { value -> rows = rows.toMutableList().also { it[index] = row.copy(weight = value) } }, Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), label = { Text(WeightFormatting.unitLabel(unit)) })
                        OutlinedTextField(row.reps, { value -> rows = rows.toMutableList().also { it[index] = row.copy(reps = value) } }, Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("Wdh.") })
                    }
                }
                Row {
                    TextButton(onClick = { rows = rows + SetDraft("NORMAL", rows.lastOrNull()?.weight ?: "0", rows.lastOrNull()?.reps ?: "10") }, enabled = rows.size < 100) { Text("+ Arbeitssatz") }
                    TextButton(onClick = { rows = listOf(SetDraft("WARMUP", "0", "10")) + rows }, enabled = rows.size < 100) { Text("+ Aufwärmsatz") }
                }
                Text("Progression: Manuell · Jede Zeile bleibt einzeln editierbar.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            Button(onClick = {
                val parsed = rows.mapNotNull { row ->
                    val reps = row.reps.toIntOrNull()
                    val weight = row.weight.replace(',', '.').toDoubleOrNull()
                    if (reps == null || weight == null) null else PlannedSet(row.kind, reps, WeightFormatting.convertToKg(weight, unit))
                }
                if (parsed.size != rows.size || parsed.isEmpty() || !PlannedSets.valid(parsed)) error = "Bitte gültige Werte und mindestens einen Arbeitssatz angeben."
                else onApply(parsed)
            }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Satzvorgaben übernehmen") }
        }
    }
}
