package com.ironlog.app.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.FitnessCenter

import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import com.ironlog.app.presentation.common.HistorySkeleton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.ironlog.core.designsystem.R
import com.ironlog.app.domain.model.AppPreferences
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.domain.util.DateFormatting
import com.ironlog.app.domain.util.WeightFormatting
import com.ironlog.app.presentation.common.EmptyStateScreen
import com.ironlog.app.presentation.common.IronLogScreenScaffold
import com.ironlog.app.presentation.common.IronLogSurfaceCard
import com.ironlog.app.presentation.common.IronLogSurfaceTone
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.app.presentation.theme.staggeredEntrance
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

internal enum class HistoryListContentState {
    Loading,
    Empty,
    Error,
    Content
}

internal fun resolveHistoryContentState(
    refreshLoadState: LoadState,
    itemCount: Int
): HistoryListContentState {
    if (itemCount > 0) return HistoryListContentState.Content
    return when (refreshLoadState) {
        is LoadState.Loading -> HistoryListContentState.Loading
        is LoadState.Error -> HistoryListContentState.Error
        is LoadState.NotLoading -> HistoryListContentState.Empty
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHistoryScreen(
    onWorkoutClick: (Long) -> Unit,
    viewModel: WorkoutHistoryViewModel = koinViewModel()
) {
    val pagedWorkouts = viewModel.pagedWorkouts.collectAsLazyPagingItems()
    var deleteSessionId by remember { mutableStateOf<Long?>(null) }
    val appPreferencesRepository: AppPreferencesRepository = koinInject()
    val preferences by appPreferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = AppPreferences()
    )
    val dims = ironLogDimens
    val snackbarHostState = remember { SnackbarHostState() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    IronLogScreenScaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                title = { Text(stringResource(id = R.string.history_title)) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (resolveHistoryContentState(pagedWorkouts.loadState.refresh, pagedWorkouts.itemCount)) {
            HistoryListContentState.Loading -> {
                HistorySkeleton(modifier = Modifier.padding(padding))
            }

            HistoryListContentState.Empty -> {
                EmptyStateScreen(
                    title = stringResource(id = R.string.history_empty_title),
                    subtitle = stringResource(id = R.string.history_empty_subtitle),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }

            HistoryListContentState.Error -> {
                EmptyStateScreen(
                    title = stringResource(id = R.string.history_error_title),
                    subtitle = stringResource(id = R.string.history_error_subtitle),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    action = {
                        TextButton(onClick = { pagedWorkouts.retry() }) {
                            Text(text = stringResource(id = R.string.common_retry))
                        }
                    }
                )
            }

            HistoryListContentState.Content -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(dims.spacingMd),
                    verticalArrangement = Arrangement.spacedBy(dims.spacingSm)
                ) {
                    items(
                        count = pagedWorkouts.itemCount,
                        key = pagedWorkouts.itemKey { it.session.id }
                    ) { index ->
                        val item = pagedWorkouts[index]
                        if (item != null) {
                            val entranceModifier = if (index < 8) Modifier.staggeredEntrance(index) else Modifier
                            SwipeToDeleteCard(
                                item = item,
                                unitSystem = preferences.unitSystem,
                                onClick = { onWorkoutClick(item.session.id) },
                                onDelete = { deleteSessionId = item.session.id },
                                modifier = entranceModifier
                            )
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(dims.spacingXl))
                    }
                }
            }
        }

        deleteSessionId?.let { id ->
            AlertDialog(
                onDismissRequest = { deleteSessionId = null },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                title = { Text(stringResource(id = R.string.history_delete_dialog_title)) },
                text = { Text(stringResource(id = R.string.history_delete_dialog_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteSession(id)
                        deleteSessionId = null
                    }) {
                        Text(
                            text = stringResource(id = R.string.common_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteSessionId = null }) {
                        Text(stringResource(id = R.string.common_cancel))
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteCard(
    item: WorkoutHistoryItem,
    unitSystem: UnitSystem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        modifier = modifier,
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            if (dismissState.targetValue == SwipeToDismissBoxValue.Settled && dismissState.currentValue == SwipeToDismissBoxValue.Settled) {
                Box(modifier = Modifier.fillMaxSize())
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large)
                        .padding(end = ironLogDimens.spacingLg),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(id = R.string.history_delete_cd),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    ) {
        WorkoutCard(item = item, unitSystem = unitSystem, onClick = onClick)
    }
}

@Composable
private fun WorkoutCard(
    item: WorkoutHistoryItem,
    unitSystem: UnitSystem,
    onClick: () -> Unit
) {
    val dims = ironLogDimens

    IronLogSurfaceCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tone = IronLogSurfaceTone.ELEVATED
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.spacingLg)
        ) {
            val title = if (item.session.name.isNotBlank()) item.session.name else stringResource(id = R.string.history_title)
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(dims.spacingXs))
            
            Text(
                text = item.session.startTime.format(DateFormatting.DATE_FULL),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(dims.spacingSm))
            
            val durationMin = item.session.durationSeconds / 60
            val statsList = mutableListOf<String>()
            statsList.add("$durationMin min")
            statsList.add("${item.exerciseCount} Übungen")
            if (item.totalVolume > 0) {
                statsList.add(WeightFormatting.formatVolume(item.totalVolume, unitSystem))
            }
            
            Text(
                text = statsList.joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(dims.spacingLg))
            
            androidx.compose.material3.Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = null)
                Spacer(modifier = Modifier.width(dims.spacingXs))
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

