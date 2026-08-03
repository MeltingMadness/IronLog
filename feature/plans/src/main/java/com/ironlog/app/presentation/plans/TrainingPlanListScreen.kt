package com.ironlog.app.presentation.plans

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.core.designsystem.R
import com.ironlog.app.presentation.common.IronLogScreenScaffold
import com.ironlog.app.presentation.common.IronLogSurfaceCard
import com.ironlog.app.presentation.common.IronLogSurfaceTone
import com.ironlog.app.presentation.theme.ButtonSize
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.app.presentation.theme.semantic
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrainingPlanListScreen(
    onCreatePlan: () -> Unit,
    onEditPlan: (Long) -> Unit,
    onStartWorkout: (Long, Long) -> Unit,
    onOpenMetaPlans: () -> Unit,
    viewModel: TrainingPlanListViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var deletePlanId by remember { mutableStateOf<Long?>(null) }
    var deletePlanName by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val dims = ironLogDimens

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
                title = { Text(stringResource(id = R.string.plans_title)) },
                actions = {
                    IconButton(onClick = onOpenMetaPlans) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(id = R.string.plans_open_meta_plans_cd)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreatePlan) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.plans_add_cd)
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.plans.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = dims.spacingLg),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IronLogSurfaceCard(
                        modifier = Modifier.fillMaxWidth(),
                        tone = IronLogSurfaceTone.ELEVATED
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(dims.spacingLg),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(id = R.string.plans_empty_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(dims.spacingXs))
                            Text(
                                text = stringResource(id = R.string.plans_empty_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(dims.spacingMd),
                    verticalArrangement = Arrangement.spacedBy(dims.spacingSm)
                ) {
                    items(state.plans, key = { it.plan.id }) { item ->
                        SwipeToDeletePlanCard(
                            item = item,
                            onStart = {
                                viewModel.startPlanWorkout(item.plan) { sessionId, planId ->
                                    onStartWorkout(sessionId, planId)
                                }
                            },
                            onClick = { onEditPlan(item.plan.id) },
                            onDelete = {
                                deletePlanId = item.plan.id
                                deletePlanName = item.plan.name
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(dims.spacingXl)) }
                }
            }
        }

        deletePlanId?.let { id ->
            AlertDialog(
                onDismissRequest = { deletePlanId = null },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                title = { Text(stringResource(id = R.string.plans_delete_title)) },
                text = { Text(stringResource(id = R.string.plans_delete_text, deletePlanName)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deletePlan(id)
                        deletePlanId = null
                    }) {
                        Text(
                            text = stringResource(id = R.string.common_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletePlanId = null }) {
                        Text(stringResource(id = R.string.common_cancel))
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeletePlanCard(
    item: PlanListItem,
    onStart: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val progress = dismissState.progress
            if (dismissState.targetValue == SwipeToDismissBoxValue.Settled && dismissState.currentValue == SwipeToDismissBoxValue.Settled) {
                Box(modifier = Modifier.fillMaxSize())
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = progress), shape = MaterialTheme.shapes.large)
                        .padding(end = ironLogDimens.spacingLg),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(id = R.string.common_delete),
                        tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = progress)
                    )
                }
            }
        }
    ) {
        PlanCard(item = item, onStart = onStart, onClick = onClick, onLongClick = onDelete)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlanCard(
    item: PlanListItem,
    onStart: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dims = ironLogDimens

    IronLogSurfaceCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        tone = IronLogSurfaceTone.ACCENT
    ) {
        Column(modifier = Modifier.padding(dims.spacingLg)) {
            Text(
                text = item.plan.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(dims.spacingXs))
            
            Text(
                text = pluralStringResource(
                    id = R.plurals.plans_exercise_count,
                    count = item.plan.exercises.size,
                    item.plan.exercises.size
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.semantic.violet,
                fontWeight = FontWeight.SemiBold
            )

            if (item.exerciseNames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(dims.spacingSm))
                Text(
                    text = item.exerciseNames.joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(dims.spacingLg))
            
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonSize.height)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(dims.spacingXs))
                Text(
                    text = stringResource(id = R.string.dashboard_start_workout),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

