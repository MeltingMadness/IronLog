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
import androidx.compose.material3.TextButton
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
import com.ironlog.app.domain.model.ProgressionScheme
import com.ironlog.app.domain.model.ProgressionSuggestionStatus
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.presentation.common.EmptyStateScreen
import com.ironlog.app.presentation.common.LoadingScreen
import com.ironlog.app.presentation.common.IronLogScreenScaffold
import com.ironlog.app.presentation.common.IronLogSurfaceCard
import com.ironlog.app.presentation.common.IronLogSurfaceTone
import com.ironlog.app.presentation.common.ironLogSharedElement
import com.ironlog.app.domain.util.DateFormatting
import com.ironlog.app.domain.util.WeightFormatting
import com.ironlog.app.presentation.progression.ProgressionReasonText
import com.ironlog.app.presentation.progression.ProgressionReviewItemUi
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
        } else if (state.notFound) {
            EmptyStateScreen(
                title = stringResource(id = R.string.workout_detail_not_found_title),
                subtitle = stringResource(id = R.string.workout_detail_not_found_subtitle),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                action = {
                    TextButton(onClick = onBack) {
                        Text(text = stringResource(id = R.string.nav_back))
                    }
                }
            )
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

                if (state.progressionOutcomes.isNotEmpty()) {
                    item(key = "progression_title") {
                        Text(
                            text = stringResource(id = R.string.workout_detail_progression_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(
                        state.progressionOutcomes,
                        key = { outcome -> "progression_${outcome.id}" }
                    ) { outcome ->
                        ProgressionOutcomeCard(
                            outcome = outcome,
                            unitSystem = preferences.unitSystem
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(dims.spacingMd)) }
            }
        }
    }
}

@Composable
private fun ProgressionOutcomeCard(
    outcome: ProgressionReviewItemUi,
    unitSystem: UnitSystem
) {
    IronLogSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        tone = if (outcome.status == ProgressionSuggestionStatus.PENDING) {
            IronLogSurfaceTone.ACCENT
        } else {
            IronLogSurfaceTone.MUTED
        },
        alpha = 0.68f
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = outcome.exerciseName
                        ?: stringResource(R.string.progression_review_exercise, outcome.exerciseId),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = progressionStatusText(outcome.status),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = progressionStatusColor(outcome.status),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(
                text = stringResource(
                    R.string.progression_review_scheme,
                    progressionSchemeText(outcome.scheme)
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = ProgressionReasonText(outcome, unitSystem),
                style = MaterialTheme.typography.bodyMedium
            )
            TargetChangesSummary(outcome = outcome, unitSystem = unitSystem)
        }
    }
}

@Composable
private fun TargetChangesSummary(
    outcome: ProgressionReviewItemUi,
    unitSystem: UnitSystem
) {
    val proposed = outcome.proposed ?: return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (outcome.source.sets != proposed.sets) {
            Text(
                text = stringResource(
                    R.string.progression_review_change_sets,
                    outcome.source.sets,
                    proposed.sets
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (outcome.source.reps != proposed.reps) {
            Text(
                text = stringResource(
                    R.string.progression_review_change_reps,
                    outcome.source.reps,
                    proposed.reps
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (outcome.source.weightKg != proposed.weightKg) {
            Text(
                text = stringResource(
                    R.string.progression_review_change_weight,
                    WeightFormatting.formatWeight(outcome.source.weightKg, unitSystem),
                    WeightFormatting.formatWeight(proposed.weightKg, unitSystem)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
private fun progressionStatusColor(status: ProgressionSuggestionStatus): Color = when (status) {
    ProgressionSuggestionStatus.PENDING -> MaterialTheme.colorScheme.primary
    ProgressionSuggestionStatus.ACCEPTED -> MaterialTheme.semantic.success
    ProgressionSuggestionStatus.REJECTED -> MaterialTheme.semantic.danger
    ProgressionSuggestionStatus.INFORMATIONAL,
    ProgressionSuggestionStatus.STALE -> MaterialTheme.colorScheme.onSurfaceVariant
}

