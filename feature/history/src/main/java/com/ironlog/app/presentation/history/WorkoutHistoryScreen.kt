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
import androidx.compose.material.icons.filled.FitnessCenter

import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            TopAppBar(title = { Text(stringResource(id = R.string.history_title)) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (resolveHistoryContentState(pagedWorkouts.loadState.refresh, pagedWorkouts.itemCount)) {
            HistoryListContentState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
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
                            SwipeToDeleteCard(
                                item = item,
                                unitSystem = preferences.unitSystem,
                                onClick = { onWorkoutClick(item.session.id) },
                                onDelete = { deleteSessionId = item.session.id }
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
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
            .clip(RoundedCornerShape(dims.radiusLg)) // Extracted glassmorphism radius
            .clickable(onClick = onClick),
        tone = IronLogSurfaceTone.MUTED,
        alpha = 0.5f
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.spacingLg)
        ) {
            if (item.session.name.isNotBlank()) {
                Text(
                    text = item.session.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(dims.spacing2))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(dims.spacingXs))
                Text(
                    text = item.session.startTime.format(DateFormatting.DATE_FULL),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(dims.spacingMd))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(dims.spacingSm),
                modifier = Modifier.fillMaxWidth()
            ) {
                val durationMin = item.session.durationSeconds / 60
                
                BadgeStat(
                    icon = Icons.Default.Timer,
                    text = "$durationMin min"
                )
                
                BadgeStat(
                    icon = Icons.Default.FitnessCenter,
                    text = "${item.exerciseCount} Übungen / ${item.setCount} Sätze"
                )
            }
            
            if (item.totalVolume > 0) {
                Spacer(modifier = Modifier.height(dims.spacingSm))
                BadgeStat(
                    icon = Icons.Default.FitnessCenter, // Or something suited for kg/lbs
                    text = "Volumen: ${WeightFormatting.formatVolume(item.totalVolume, unitSystem)}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun BadgeStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val dims = ironLogDimens
    Row(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                shape = RoundedCornerShape(dims.radiusSm)
            )
            .padding(horizontal = dims.spacingSm, vertical = dims.spacing2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(dims.spacingXs))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = FontWeight.SemiBold
        )
    }
}
