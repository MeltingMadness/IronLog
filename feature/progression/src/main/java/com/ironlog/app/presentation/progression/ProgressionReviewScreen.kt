package com.ironlog.app.presentation.progression

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.domain.model.ProgressionScheme
import com.ironlog.app.domain.model.ProgressionSuggestionStatus
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.util.WeightFormatting
import com.ironlog.app.presentation.common.IronLogScreenScaffold
import com.ironlog.app.presentation.common.IronLogSurfaceCard
import com.ironlog.app.presentation.common.IronLogSurfaceTone
import com.ironlog.core.designsystem.R
import java.text.NumberFormat
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressionReviewScreen(
    onClose: () -> Unit,
    viewModel: ProgressionReviewViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingSuggestionId by rememberSaveable { mutableStateOf<Long?>(null) }
    val messageText = state.message?.let { progressionReviewMessageText(it) }

    LaunchedEffect(state.message, messageText) {
        val localizedMessage = messageText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(localizedMessage)
        viewModel.clearMessage()
    }

    IronLogScreenScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.progression_review_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.progression_review_close_cd)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val hasSafePending = state.items.any(ProgressionReviewItemUi::canDecide)
            if (hasSafePending) {
                item(key = "accept_all") {
                    Button(
                        onClick = viewModel::acceptAllSafe,
                        enabled = !state.isWorking,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.progression_review_accept_all))
                    }
                }
            }

            if (state.items.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.progression_review_empty),
                        modifier = Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.items, key = ProgressionReviewItemUi::id) { item ->
                    ProgressionReviewCard(
                        item = item,
                        unitSystem = state.unitSystem,
                        isWorking = state.isWorking,
                        onAccept = { viewModel.acceptOne(item.id) },
                        onEdit = {
                            viewModel.beginEdit(item.id)
                            editingSuggestionId = item.id
                        },
                        onReject = { viewModel.reject(item.id) }
                    )
                }
            }
        }
    }

    editingSuggestionId?.let { suggestionId ->
        val draft = state.edits[suggestionId]
        val item = state.items.firstOrNull { it.id == suggestionId && it.canDecide }
        if (draft != null && item != null) {
            ProgressionEditSheet(
                draft = draft,
                isWorking = state.isWorking,
                onUpdate = { sets, reps, weight ->
                    viewModel.updateEdit(suggestionId, sets, reps, weight)
                },
                onAccept = { viewModel.acceptOne(suggestionId) },
                onDismiss = {
                    viewModel.dismissEdit(suggestionId)
                    editingSuggestionId = null
                }
            )
        } else {
            LaunchedEffect(suggestionId) {
                editingSuggestionId = null
            }
        }
    }
}

@Composable
private fun ProgressionReviewCard(
    item: ProgressionReviewItemUi,
    unitSystem: UnitSystem,
    isWorking: Boolean,
    onAccept: () -> Unit,
    onEdit: () -> Unit,
    onReject: () -> Unit
) {
    IronLogSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        tone = if (item.canDecide) IronLogSurfaceTone.ACCENT else IronLogSurfaceTone.MUTED
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.progression_review_exercise, item.exerciseId),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(
                    R.string.progression_review_scheme,
                    progressionSchemeText(item.scheme)
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = ProgressionReasonText(item, unitSystem),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            TargetChanges(item = item, unitSystem = unitSystem)
            Spacer(Modifier.height(12.dp))
            EvidenceSets(sets = item.countedSets, unitSystem = unitSystem)
            Spacer(Modifier.height(12.dp))

            if (item.canDecide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(onClick = onAccept, enabled = !isWorking) {
                        Text(stringResource(R.string.progression_review_accept))
                    }
                    OutlinedButton(onClick = onEdit, enabled = !isWorking) {
                        Text(stringResource(R.string.progression_review_edit))
                    }
                    TextButton(onClick = onReject, enabled = !isWorking) {
                        Text(stringResource(R.string.progression_review_reject))
                    }
                }
            } else {
                Text(
                    text = progressionStatusText(item.status),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TargetChanges(item: ProgressionReviewItemUi, unitSystem: UnitSystem) {
    val proposed = item.proposed ?: return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (item.source.sets != proposed.sets) {
            Text(stringResource(R.string.progression_review_change_sets, item.source.sets, proposed.sets))
        }
        if (item.source.reps != proposed.reps) {
            Text(stringResource(R.string.progression_review_change_reps, item.source.reps, proposed.reps))
        }
        if (item.source.weightKg != proposed.weightKg) {
            Text(
                stringResource(
                    R.string.progression_review_change_weight,
                    WeightFormatting.formatWeight(item.source.weightKg, unitSystem),
                    WeightFormatting.formatWeight(proposed.weightKg, unitSystem)
                )
            )
        }
    }
}

@Composable
private fun EvidenceSets(sets: List<WorkoutSet>, unitSystem: UnitSystem) {
    val workSets = sets.filterNot(WorkoutSet::isWarmup)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(R.string.progression_review_evidence_title),
            style = MaterialTheme.typography.labelLarge
        )
        if (workSets.isEmpty()) {
            Text(
                text = stringResource(R.string.progression_review_evidence_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            workSets.forEach { set ->
                val rpe = set.rpe
                val text = if (rpe == null) {
                    stringResource(
                        R.string.progression_review_evidence_set,
                        set.setNumber,
                        WeightFormatting.formatWeight(set.weightKg, unitSystem),
                        set.reps
                    )
                } else {
                    stringResource(
                        R.string.progression_review_evidence_set_rpe,
                        set.setNumber,
                        WeightFormatting.formatWeight(set.weightKg, unitSystem),
                        set.reps,
                        formatNumber(rpe)
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressionEditSheet(
    draft: ProgressionEditDraft,
    isWorking: Boolean,
    onUpdate: (sets: String, reps: String, weight: String) -> Unit,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.progression_review_edit_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            EditField(
                value = draft.sets,
                onValueChange = { onUpdate(it, draft.reps, draft.weight) },
                label = stringResource(R.string.progression_review_edit_sets),
                error = draft.errors[ProgressionEditField.SETS]?.let {
                    progressionEditErrorText(it)
                },
                keyboardType = KeyboardType.Number
            )
            EditField(
                value = draft.reps,
                onValueChange = { onUpdate(draft.sets, it, draft.weight) },
                label = stringResource(R.string.progression_review_edit_reps),
                error = draft.errors[ProgressionEditField.REPS]?.let {
                    progressionEditErrorText(it)
                },
                keyboardType = KeyboardType.Number
            )
            EditField(
                value = draft.weight,
                onValueChange = { onUpdate(draft.sets, draft.reps, it) },
                label = stringResource(
                    R.string.progression_review_edit_weight,
                    WeightFormatting.unitLabel(draft.unitSystem)
                ),
                error = draft.errors[ProgressionEditField.WEIGHT]?.let {
                    progressionEditErrorText(it)
                },
                keyboardType = KeyboardType.Decimal
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss, enabled = !isWorking) {
                    Text(stringResource(R.string.common_cancel))
                }
                Button(onClick = onAccept, enabled = !isWorking) {
                    Text(stringResource(R.string.progression_review_accept))
                }
            }
        }
    }
}

@Composable
private fun EditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    keyboardType: KeyboardType
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

@Composable
private fun progressionSchemeText(scheme: ProgressionScheme): String = stringResource(
    when (scheme) {
        ProgressionScheme.MANUAL -> R.string.progression_review_scheme_manual
        ProgressionScheme.LINEAR -> R.string.progression_review_scheme_linear
        ProgressionScheme.DOUBLE -> R.string.progression_review_scheme_double
        ProgressionScheme.TOTAL_REPS -> R.string.progression_review_scheme_total_reps
        ProgressionScheme.RPE_RIR -> R.string.progression_review_scheme_rpe_rir
    }
)

@Composable
private fun progressionStatusText(status: ProgressionSuggestionStatus): String = stringResource(
    when (status) {
        ProgressionSuggestionStatus.PENDING -> R.string.progression_review_status_pending
        ProgressionSuggestionStatus.INFORMATIONAL -> R.string.progression_review_status_informational
        ProgressionSuggestionStatus.ACCEPTED -> R.string.progression_review_status_accepted
        ProgressionSuggestionStatus.REJECTED -> R.string.progression_review_status_rejected
        ProgressionSuggestionStatus.STALE -> R.string.progression_review_status_stale
    }
)

@Composable
private fun progressionReviewMessageText(message: ProgressionReviewMessage): String = stringResource(
    when (message) {
        ProgressionReviewMessage.STALE -> R.string.progression_review_message_stale
        ProgressionReviewMessage.INVALID -> R.string.progression_review_message_invalid
        ProgressionReviewMessage.ACTION_FAILED -> R.string.progression_review_message_action_failed
    }
)

@Composable
private fun progressionEditErrorText(error: ProgressionEditError): String = stringResource(
    when (error) {
        ProgressionEditError.INVALID_SETS -> R.string.progression_review_edit_error_sets
        ProgressionEditError.INVALID_REPS -> R.string.progression_review_edit_error_reps
        ProgressionEditError.INVALID_WEIGHT -> R.string.progression_review_edit_error_weight
    }
)

private fun formatNumber(value: Double): String = NumberFormat.getNumberInstance().apply {
    maximumFractionDigits = 1
    minimumFractionDigits = 0
    isGroupingUsed = false
}.format(value)
