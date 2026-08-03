package com.ironlog.app.presentation.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.core.designsystem.R
import com.ironlog.app.domain.model.AppPreferences
import com.ironlog.app.domain.model.IntensitySystem
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.presentation.common.LoadingScreen
import com.ironlog.app.presentation.common.IronLogScreenScaffold
import com.ironlog.app.presentation.common.IronLogSurfaceCard
import com.ironlog.app.presentation.common.IronLogSurfaceTone
import com.ironlog.app.presentation.common.ironLogSharedElement
import com.ironlog.app.domain.util.DateFormatting
import com.ironlog.app.domain.util.WeightFormatting
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.app.presentation.theme.semantic
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun WorkoutDetailScreen(
    onBack: () -> Unit,
    onExerciseClick: (Long) -> Unit,
    viewModel: WorkoutDetailViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val appPreferencesRepository: AppPreferencesRepository = koinInject()
    val preferences by appPreferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = AppPreferences()
    )
    val dims = ironLogDimens

    IronLogScreenScaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                title = { Text(stringResource(id = R.string.workout_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.nav_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            LoadingScreen(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(dims.spacingMd),
                verticalArrangement = Arrangement.spacedBy(dims.spacingSm)
            ) {
                item {
                    state.session?.let { session ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .ironLogSharedElement("workout_card_${session.id}")
                        ) {
                            Text(
                                text = session.startTime.format(DateFormatting.DATE_FULL),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            val durationMin = session.durationSeconds / 60
                            Text(
                                text = stringResource(
                                    id = R.string.workout_detail_meta,
                                    durationMin,
                                    WeightFormatting.formatVolume(state.totalVolume, preferences.unitSystem)
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (session.notes.isNotBlank()) {
                                Text(
                                    text = session.notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(dims.spacing2)) }

                items(state.exercises) { exerciseDetail ->
                    IronLogSurfaceCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onExerciseClick(exerciseDetail.exercise.id) },
                        tone = IronLogSurfaceTone.MUTED,
                        alpha = 0.68f
                    ) {
                        Column(modifier = Modifier.padding(dims.spacingMd)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = exerciseDetail.exercise.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (exerciseDetail.records.isNotEmpty()) {
                                    androidx.compose.material3.Surface(
                                        color = MaterialTheme.semantic.violet.copy(alpha = 0.15f),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = dims.spacingSm, vertical = dims.spacing2),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = stringResource(id = R.string.workout_detail_record_cd),
                                                tint = MaterialTheme.semantic.violet,
                                                modifier = Modifier.size(12.dp).padding(end = dims.spacing2)
                                            )
                                            Text(
                                                text = stringResource(id = R.string.workout_detail_pr_badge),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.semantic.violet,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(dims.spacingXs))

                            exerciseDetail.sets.filter { it.reps > 0 }.forEach { set ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (set.isWarmup) {
                                            stringResource(id = R.string.workout_detail_set_warmup, set.setNumber)
                                        } else {
                                            stringResource(id = R.string.workout_detail_set_work, set.setNumber)
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontStyle = if (set.isWarmup) FontStyle.Italic else FontStyle.Normal
                                    )
                                    val rpe = set.rpe
                                    val intensityString = if (rpe == null) {
                                        ""
                                    } else {
                                        when (preferences.intensitySystem) {
                                            IntensitySystem.OFF -> ""
                                            IntensitySystem.RPE -> " @ RPE ${rpe}"
                                            IntensitySystem.RIR -> {
                                                val rir = 10.0 - rpe
                                                " @ ${if (rir % 1.0 == 0.0) rir.toInt() else rir} RIR"
                                            }
                                        }
                                    }

                                    Text(
                                        text = stringResource(
                                            id = R.string.workout_detail_set_value,
                                            WeightFormatting.formatWeight(set.weightKg, preferences.unitSystem),
                                            set.reps
                                        ) + intensityString,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(dims.spacingMd)) }
            }
        }
    }
}

