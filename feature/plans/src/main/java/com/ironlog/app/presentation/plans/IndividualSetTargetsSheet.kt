package com.ironlog.app.presentation.plans

import com.ironlog.feature.plans.R as UxR
import androidx.compose.ui.res.stringResource
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
import com.ironlog.app.presentation.theme.IronLogInteractiveColors
import com.ironlog.app.presentation.theme.semanticText

@Composable
internal fun plannedSetLabel(kind: String): String = stringResource(
    when (kind) {
        "WARMUP" -> UxR.string.plan_set_warmup
        "BACKOFF" -> UxR.string.plan_set_backoff
        else -> UxR.string.plan_set_work
    }
)
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
    val invalidTargetsMessage = stringResource(UxR.string.plan_targets_invalid)
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(20.dp)) {
            Text(stringResource(UxR.string.plan_targets_title), style = MaterialTheme.typography.headlineSmall)
            Text(item.exercise.name, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(UxR.string.plan_targets_set_type), Modifier.weight(1.3f)); Text(WeightFormatting.unitLabel(unit), Modifier.weight(1f)); Text(stringResource(UxR.string.plan_targets_reps_header), Modifier.weight(1f))
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEachIndexed { index, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        var expanded by remember { mutableStateOf(false) }
                        Box(Modifier.weight(1.3f)) {
                            TextButton(onClick = { expanded = true }, colors = IronLogInteractiveColors.textButton()) { Text(stringResource(UxR.string.plan_target_index_kind, index + 1, plannedSetLabel(row.kind))) }
                            DropdownMenu(expanded, { expanded = false }) {
                                listOf("NORMAL", "WARMUP", "BACKOFF").forEach { kind ->
                                    DropdownMenuItem(text = { Text(plannedSetLabel(kind)) }, onClick = { rows = rows.toMutableList().also { it[index] = row.copy(kind = kind) }; expanded = false })
                                }
                                DropdownMenuItem(text = { Text(stringResource(UxR.string.plan_targets_remove)) }, onClick = { rows = rows.filterIndexed { i, _ -> i != index }; expanded = false })
                            }
                        }
                        OutlinedTextField(row.weight, { value -> rows = rows.toMutableList().also { it[index] = row.copy(weight = value) } }, Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), label = { Text(WeightFormatting.unitLabel(unit)) })
                        OutlinedTextField(row.reps, { value -> rows = rows.toMutableList().also { it[index] = row.copy(reps = value) } }, Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text(stringResource(UxR.string.plan_targets_reps_short)) })
                    }
                }
                Row {
                    TextButton(onClick = { rows = rows + SetDraft("NORMAL", rows.lastOrNull()?.weight ?: "0", rows.lastOrNull()?.reps ?: "10") }, enabled = rows.size < 100, colors = IronLogInteractiveColors.textButton()) { Text(stringResource(UxR.string.plan_targets_add_work)) }
                    TextButton(onClick = { rows = listOf(SetDraft("WARMUP", "0", "10")) + rows }, enabled = rows.size < 100, colors = IronLogInteractiveColors.textButton()) { Text(stringResource(UxR.string.plan_targets_add_warmup)) }
                }
                Text(stringResource(UxR.string.plan_targets_progression_hint), style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.semanticText.danger) }
            }
            Button(onClick = {
                val parsed = rows.mapNotNull { row ->
                    val reps = row.reps.toIntOrNull()
                    val weight = row.weight.replace(',', '.').toDoubleOrNull()
                    if (reps == null || weight == null) null else PlannedSet(row.kind, reps, WeightFormatting.convertToKg(weight, unit))
                }
                if (parsed.size != rows.size || parsed.isEmpty() || !PlannedSets.valid(parsed)) error = invalidTargetsMessage
                else onApply(parsed)
            }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(stringResource(UxR.string.plan_targets_apply)) }
        }
    }
}
