package com.ironlog.app.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.DayOfWeek
import java.time.LocalDateTime
import com.ironlog.core.designsystem.R
import com.ironlog.app.domain.util.DateFormatting
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
import com.ironlog.app.domain.model.WeekStart
import org.koin.androidx.compose.koinViewModel

private const val DEFAULT_WEEKLY_WORKOUT_GOAL = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onStartWorkout: (Long, Long?, Long?) -> Unit,
    onContinueWorkout: (Long, Long?, Long?) -> Unit,
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

            if (state.lastWorkout == null && state.recentRecords.isEmpty()) {
                item {
                    OnboardingCard()
                }
            } else {
                item {
                    SectionTitle(text = stringResource(id = R.string.dashboard_quick_stats))
                }

                item {
                    StreakCard(
                        currentStreak = state.currentStreak,
                        workoutDaysThisWeek = state.workoutDaysThisWeek,
                        weekStart = state.weekStart
                    )
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
private fun StreakCard(
    currentStreak: Int,
    workoutDaysThisWeek: Set<DayOfWeek>,
    weekStart: WeekStart,
    modifier: Modifier = Modifier
) {
    val dims = ironLogDimens
    val startDay = if (weekStart == WeekStart.SUNDAY) DayOfWeek.SUNDAY else DayOfWeek.MONDAY
    val daysOfWeek = (0 until 7).map { DayOfWeek.of(((startDay.value - 1 + it) % 7) + 1) }
    val allLabels = mapOf(
        DayOfWeek.MONDAY to "Mo",
        DayOfWeek.TUESDAY to "Di",
        DayOfWeek.WEDNESDAY to "Mi",
        DayOfWeek.THURSDAY to "Do",
        DayOfWeek.FRIDAY to "Fr",
        DayOfWeek.SATURDAY to "Sa",
        DayOfWeek.SUNDAY to "So"
    )
    val dayLabels = daysOfWeek.map { allLabels.getValue(it) }

    IronLogSurfaceCard(
        modifier = modifier.fillMaxWidth(),
        tone = IronLogSurfaceTone.ACCENT
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.spacingMd),
            verticalArrangement = Arrangement.spacedBy(dims.spacingSm)
        ) {
            Text(
                text = stringResource(id = R.string.dashboard_streak_label, currentStreak),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                daysOfWeek.forEachIndexed { index, day ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    color = if (day in workoutDaysThisWeek) {
                                        MaterialTheme.semantic.success
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    },
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = dayLabels[index],
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { (workoutDaysThisWeek.size / DEFAULT_WEEKLY_WORKOUT_GOAL.toFloat()).coerceAtMost(1f) },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )

            Text(
                text = stringResource(
                    id = R.string.dashboard_weekly_progress,
                    workoutDaysThisWeek.size,
                    DEFAULT_WEEKLY_WORKOUT_GOAL
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

