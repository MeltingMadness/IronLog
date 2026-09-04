package com.ironlog.app.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import com.ironlog.app.presentation.common.DashboardSkeleton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDateTime
import com.ironlog.core.designsystem.R
import com.ironlog.app.domain.util.DateFormatting
import com.ironlog.app.domain.model.DeloadAssessment
import com.ironlog.app.domain.model.DeloadMode
import com.ironlog.app.domain.model.DeloadSignal
import com.ironlog.app.presentation.common.IronLogScreenScaffold
import com.ironlog.app.presentation.common.IronLogSurfaceCard
import com.ironlog.app.presentation.common.IronLogSurfaceTone
import com.ironlog.app.presentation.common.StatCard
import com.ironlog.app.presentation.common.StatCardVariant
import com.ironlog.app.presentation.theme.ButtonSize
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.app.presentation.theme.ironLogMotion
import com.ironlog.app.presentation.theme.semantic
import com.ironlog.app.presentation.theme.staggeredEntrance
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onStartWorkout: (Long, Long?, Long?) -> Unit,
    onContinueWorkout: (Long, Long?, Long?) -> Unit,
    onOpenProgressionReview: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val dims = ironLogDimens

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    IronLogScreenScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(id = R.string.settings_title)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading) {
            DashboardSkeleton(modifier = Modifier.padding(padding))
            return@IronLogScreenScaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(dims.spacingMd),
            verticalArrangement = Arrangement.spacedBy(dims.spacingMd)
        ) {
            item {
                GreetingHeader()
            }

            item {
                val isFirstTimeUser = state.lastWorkout == null && state.recentRecords.isEmpty()
                CommandCenterCard(
                    hasActiveSession = state.activeSession != null,
                    isFirstTimeUser = isFirstTimeUser,
                    onStartWorkout = { viewModel.showPlanSelectionSheet() },
                    onContinueWorkout = {
                        state.activeSession?.let { session ->
                            onContinueWorkout(session.id, session.planId, session.metaPlanId)
                        }
                    }
                )
            }

            val deload = state.deload
            if ((deload?.recommended == true || state.deloadMode != null) && deload != null) {
                item(key = "deload_card") {
                    DeloadCard(
                        assessment = deload,
                        exerciseName = state.deloadExerciseName,
                        activeMode = state.deloadMode,
                        onActivate = viewModel::activateDeloadMode,
                        onDeactivate = viewModel::deactivateDeloadMode
                    )
                }
            }

            if (state.pendingProgressionCount > 0) {
                item(key = "pending_progressions") {
                    PendingProgressionCard(
                        count = state.pendingProgressionCount,
                        onOpenProgressionReview = onOpenProgressionReview
                    )
                }
            }

            if (state.lastWorkout == null && state.recentRecords.isEmpty()) {
                item {
                    OnboardingCard()
                }
            } else {
                item {
                    SectionTitle(text = stringResource(id = R.string.dashboard_quick_stats))
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dims.spacingSm)
                    ) {
                        StatCard(
                            label = stringResource(id = R.string.dashboard_this_week),
                            value = "${state.workoutsThisWeek}",
                            modifier = Modifier.weight(1f),
                            variant = StatCardVariant.SECONDARY
                        )
                        StatCard(
                            label = stringResource(id = R.string.dashboard_this_month),
                            value = "${state.workoutsThisMonth}",
                            modifier = Modifier.weight(1f),
                            variant = StatCardVariant.TERTIARY
                        )
                    }
                }

                item {
                    MuscleHeatmapCard(heatmap = state.muscleHeatmap)
                }

                item {
                    SectionTitle(text = stringResource(id = R.string.dashboard_recent_records))
                }

                if (state.recentRecords.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(id = R.string.dashboard_no_records),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    item {
                        androidx.compose.foundation.lazy.LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(dims.spacingSm)
                        ) {
                            items(
                                items = state.recentRecords,
                                key = { (record, _) -> "${record.exerciseId}-${record.type.name}-${record.achievedAt}" }
                            ) { (record, exerciseName) ->
                                val index = state.recentRecords.indexOfFirst {
                                    it.first.id == record.id && it.first.achievedAt == record.achievedAt
                                }
                                RecordCard(
                                    exerciseName = exerciseName,
                                    recordType = record.type.displayName,
                                    recordValue = formatRecordValue(record.type.name, record.value),
                                    modifier = Modifier.staggeredEntrance(index)
                                )
                            }
                        }
                    }
                }

                item {
                    WeeklyVolumeCard(weeklyVolume = state.weeklyVolume)
                }

                state.lastWorkout?.let { workout ->
                    item {
                        SectionTitle(text = stringResource(id = R.string.dashboard_last_workout))
                    }
                    item {
                        LastWorkoutCard(
                            dateTime = workout.startTime.format(DateFormatting.DATE_TIME),
                            durationMin = (workout.durationSeconds / 60).toInt(),
                            exerciseCount = state.lastWorkoutExerciseCount
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(dims.spacingXl))
            }
        }

        if (state.showPlanSelectionSheet) {
            PlanSelectionSheet(
                plans = state.trainingPlans,
                metaPlanOptions = state.metaPlanOptions,
                skippingMetaPlanId = state.skippingMetaPlanId,
                onDismiss = viewModel::dismissPlanSelectionSheet,
                onPlanSelected = { plan -> 
                    viewModel.dismissPlanSelectionSheet()
                    viewModel.startNewWorkoutWithPlan(plan) { sessionId, planId ->
                        onStartWorkout(sessionId, planId, null)
                    }
                },
                onMetaPlanSelected = { metaPlanId ->
                    viewModel.dismissPlanSelectionSheet()
                    viewModel.startNewWorkoutWithMetaPlan(metaPlanId, onStartWorkout)
                },
                onSkipMetaPlan = viewModel::skipCurrentMetaSubPlan,
                onFreeWorkoutSelected = { 
                    viewModel.dismissPlanSelectionSheet()
                    viewModel.startNewWorkout { sessionId, planId ->
                        onStartWorkout(sessionId, planId, null)
                    }
                }
            )
        }
    }
}

@Composable
private fun DeloadCard(
    assessment: DeloadAssessment,
    exerciseName: String?,
    activeMode: DeloadMode?,
    onActivate: (DeloadMode) -> Unit,
    onDeactivate: () -> Unit
) {
    val dims = ironLogDimens

    IronLogSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        tone = IronLogSurfaceTone.COLORED,
        semanticColor = MaterialTheme.semantic.warning
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.spacingMd),
            verticalArrangement = Arrangement.spacedBy(dims.spacingXs)
        ) {
            Text(
                text = stringResource(
                    id = if (activeMode != null) {
                        R.string.deload_card_title_active
                    } else {
                        R.string.deload_card_title_recommended
                    }
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(id = R.string.deload_card_fatigue_score, assessment.fatigueScore),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (activeMode == null) {
                Text(
                    text = stringResource(id = R.string.deload_card_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                assessment.signals.forEach { signal ->
                    Text(
                        text = "• ${stringResource(id = signal.labelRes())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val strongestChange = assessment.strongestExerciseChangePercent
                if (exerciseName != null && strongestChange != null) {
                    Text(
                        text = stringResource(
                            id = R.string.deload_strongest_exercise,
                            exerciseName,
                            formatPercent(strongestChange)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Text(
                    text = stringResource(
                        id = when (activeMode) {
                            DeloadMode.HALVE_SET_VOLUME -> R.string.deload_active_hint_volume
                            DeloadMode.REDUCE_INTENSITY_BY_15_PERCENT -> R.string.deload_active_hint_intensity
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = dims.spacingXs),
                horizontalArrangement = Arrangement.spacedBy(dims.spacingSm),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                if (activeMode == null) {
                    Button(
                        onClick = { onActivate(DeloadMode.HALVE_SET_VOLUME) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(id = R.string.deload_mode_halve_volume))
                    }
                    TextButton(
                        onClick = { onActivate(DeloadMode.REDUCE_INTENSITY_BY_15_PERCENT) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(id = R.string.deload_mode_reduce_intensity))
                    }
                } else {
                    Text(
                        text = stringResource(id = R.string.deload_mode_active),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.semantic.success
                    )
                    TextButton(onClick = onDeactivate) {
                        Text(stringResource(id = R.string.deload_mode_end))
                    }
                }
            }
        }
    }
}

private fun DeloadSignal.labelRes(): Int = when (this) {
    DeloadSignal.E1RM_STAGNATION -> R.string.deload_signal_e1rm_stagnation
    DeloadSignal.E1RM_DROP -> R.string.deload_signal_e1rm_drop
    DeloadSignal.RPE_CREEP -> R.string.deload_signal_rpe_creep
    DeloadSignal.FAILURE_FREQUENCY -> R.string.deload_signal_failure_frequency
}

private fun formatPercent(changePercent: Double): String =
    String.format(Locale.ROOT, "%.1f %%", changePercent)

@Composable
private fun PendingProgressionCard(
    count: Int,
    onOpenProgressionReview: () -> Unit
) {
    val dims = ironLogDimens

    IronLogSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        tone = IronLogSurfaceTone.MUTED
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dims.spacingMd, vertical = dims.spacingXs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = pluralStringResource(
                    id = R.plurals.dashboard_pending_progressions,
                    count = count,
                    count
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onOpenProgressionReview) {
                Text(stringResource(R.string.dashboard_review_progressions))
            }
        }
    }
}

@Composable
private fun GreetingHeader() {
    val hour = remember { java.time.LocalDateTime.now().hour }
    val greetingRes = when (hour) {
        in 5..11 -> R.string.dashboard_greeting_morning
        in 12..17 -> R.string.dashboard_greeting_day
        in 18..21 -> R.string.dashboard_greeting_evening
        else -> R.string.dashboard_greeting_late
    }
    Text(
        text = stringResource(id = greetingRes),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun CommandCenterCard(
    hasActiveSession: Boolean,
    isFirstTimeUser: Boolean,
    onStartWorkout: () -> Unit,
    onContinueWorkout: () -> Unit
) {
    val dims = ironLogDimens
    val motion = ironLogMotion

    val scale = if (isFirstTimeUser && !hasActiveSession && !motion.reduced) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val animatedScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        animatedScale
    } else {
        1f
    }

    IronLogSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        tone = IronLogSurfaceTone.ACCENT
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.spacingLg),
            verticalArrangement = Arrangement.spacedBy(dims.spacingSm)
        ) {
            Text(
                text = stringResource(id = R.string.dashboard_command_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (hasActiveSession) {
                    stringResource(id = R.string.dashboard_command_subtitle_active)
                } else {
                    stringResource(id = R.string.dashboard_command_subtitle_idle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f)
            )

            val buttonAction = if (hasActiveSession) onContinueWorkout else onStartWorkout
            Button(
                onClick = buttonAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonSize.height)
                    .scale(scale)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text(
                    text = if (hasActiveSession) {
                        stringResource(id = R.string.dashboard_continue_workout)
                    } else {
                        stringResource(id = R.string.dashboard_start_workout)
                    },
                    modifier = Modifier.padding(start = dims.spacingXs),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun RecordCard(
    exerciseName: String,
    recordType: String,
    recordValue: String,
    modifier: Modifier = Modifier
) {
    val dims = ironLogDimens

    IronLogSurfaceCard(
        modifier = modifier
            .width(140.dp)
            .height(120.dp),
        tone = IronLogSurfaceTone.COLORED,
        semanticColor = MaterialTheme.semantic.warning
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dims.spacingSm),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = exerciseName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)
                )
                Text(
                    text = recordType,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = recordValue,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFeatureSettings = "tnum"
                ),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.semantic.warning,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LastWorkoutCard(
    dateTime: String,
    durationMin: Int,
    exerciseCount: Int
) {
    val dims = ironLogDimens

    IronLogSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        tone = IronLogSurfaceTone.ACCENT
    ) {
        Column(modifier = Modifier.padding(dims.spacingSm)) {
            Text(
                text = dateTime,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = pluralStringResource(
                    id = R.plurals.dashboard_last_workout_meta,
                    count = exerciseCount,
                    durationMin,
                    exerciseCount
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun formatRecordValue(type: String, value: Double): String {
    return when (type) {
        "MAX_WEIGHT" -> stringResource(id = R.string.common_record_weight, value)
        "MAX_REPS" -> stringResource(id = R.string.common_record_reps, value.toInt())
        "MAX_VOLUME" -> stringResource(id = R.string.common_record_volume, value.toInt())
        "MAX_E1RM" -> stringResource(id = R.string.common_record_e1rm, value)
        else -> "$value"
    }
}

@Composable
private fun OnboardingCard() {
    val dims = ironLogDimens

    IronLogSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        tone = IronLogSurfaceTone.ACCENT
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.spacingLg),
            verticalArrangement = Arrangement.spacedBy(dims.spacingMd)
        ) {
            Text(
                text = stringResource(id = R.string.dashboard_onboarding_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(id = R.string.dashboard_onboarding_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

