package com.ironlog.app.presentation.plans

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.core.designsystem.R
import com.ironlog.feature.plans.R as PlansR
import com.ironlog.app.presentation.common.IronLogScreenScaffold
import com.ironlog.app.presentation.common.IronLogSurfaceCard
import com.ironlog.app.presentation.common.IronLogSurfaceTone
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.app.presentation.theme.semantic
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetaPlanEditorScreen(
    metaPlanId: Long?,
    onBack: () -> Unit,
    viewModel: MetaPlanEditorViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val dims = ironLogDimens

    fun requestBack() {
        if (state.isSaving) return
        if (state.hasUnsavedChanges && !state.isSaved) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    // Consume back while saving so the editor cannot disappear before the
    // repository operation has completed.
    BackHandler(onBack = ::requestBack)

    LaunchedEffect(metaPlanId) {
        viewModel.initialize(metaPlanId)
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onBack()
    }

    LaunchedEffect(state.error) {
        state.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    IronLogScreenScaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                title = {
                    Text(
                        text = if (state.metaPlanId != null) {
                            stringResource(id = R.string.meta_plan_editor_title_edit)
                        } else {
                            stringResource(id = R.string.meta_plan_editor_title_new)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = ::requestBack, enabled = !state.isSaving) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.nav_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::saveMetaPlan,
                        enabled = !state.isSaving && !state.isLoading
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(id = R.string.meta_plan_editor_save_cd)
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = dims.spacingMd),
            verticalArrangement = Arrangement.spacedBy(dims.spacingSm)
        ) {
            item {
                Spacer(modifier = Modifier.height(dims.spacing2))
                com.ironlog.app.presentation.common.IronLogTextField(
                    value = state.name,
                    onValueChange = viewModel::updateName,
                    label = { Text(stringResource(id = R.string.meta_plan_editor_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                Text(
                    text = stringResource(id = R.string.meta_plan_editor_available_subplans),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            itemsIndexed(state.availablePlans, key = { index, plan -> "available-${plan.id}-$index" }) { _, plan ->
                val selected = plan.id in state.selectedPlanIds
                IronLogSurfaceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.addPlan(plan.id) },
                    tone = if (selected) IronLogSurfaceTone.ELEVATED else IronLogSurfaceTone.MUTED,
                    alpha = if (selected) 0.78f else 0.68f,
                    border = if (selected) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                    } else {
                        null
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dims.spacingSm, vertical = dims.spacingXs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = plan.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.addPlan(plan.id) },
                            enabled = !state.isSaving && !state.isSaved
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(id = R.string.meta_plan_editor_add_subplan_cd)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(dims.spacingXs))
                Text(
                    text = stringResource(id = R.string.meta_plan_editor_subplan_order),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (state.selectedPlanIds.isEmpty()) {
                item {
                    Text(
                        text = stringResource(id = R.string.meta_plan_editor_subplan_order_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                itemsIndexed(state.selectedPlanIds, key = { index, id -> "selected-$id-$index" }) { index, planId ->
                    val planName = state.availablePlans
                        .firstOrNull { it.id == planId }
                        ?.name
                        ?: stringResource(id = R.string.common_unknown)
                    SelectedSubPlanRow(
                        index = index,
                        planName = planName,
                        isFirst = index == 0,
                        isLast = index == state.selectedPlanIds.lastIndex,
                        onMoveUp = { viewModel.moveSelectedPlanUp(index) },
                        onMoveDown = { viewModel.moveSelectedPlanDown(index) },
                        onRemove = { viewModel.removeSelectedPlanAt(index) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(dims.spacingXl)) }
        }

        if (showDiscardDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                title = { Text(stringResource(id = PlansR.string.meta_plan_editor_discard_title)) },
                text = { Text(stringResource(id = PlansR.string.meta_plan_editor_discard_text)) },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            showDiscardDialog = false
                            onBack()
                        }
                    ) {
                        Text(stringResource(id = PlansR.string.meta_plan_editor_discard_confirm))
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showDiscardDialog = false }) {
                        Text(stringResource(id = PlansR.string.meta_plan_editor_discard_cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun SelectedSubPlanRow(
    index: Int,
    planName: String,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val dims = ironLogDimens
    IronLogSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        tone = IronLogSurfaceTone.COLORED,
        semanticColor = MaterialTheme.semantic.violet,
        alpha = 0.68f
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dims.spacingSm, vertical = dims.spacingXs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.plan_editor_exercise_indexed, index + 1, planName),
                modifier = Modifier.weight(1f)
            )
            Row {
                IconButton(
                    onClick = onMoveUp,
                    enabled = !isFirst,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropUp,
                        contentDescription = stringResource(id = R.string.plan_editor_move_up_cd),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = !isLast,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(id = R.string.plan_editor_move_down_cd),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(id = R.string.common_delete),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}


