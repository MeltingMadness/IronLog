package com.ironlog.app.presentation.dashboard

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.core.designsystem.R
import com.ironlog.app.domain.util.DateFormatting
import com.ironlog.app.presentation.common.StatCard
import com.ironlog.app.presentation.theme.glassmorphism
import com.ironlog.app.presentation.theme.glow
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.app.presentation.theme.ironLogSurfaceRoles
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onStartWorkout: (Long) -> Unit,
    onContinueWorkout: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val dims = ironLogDimens

    LaunchedEffect(Unit) {
        viewModel.loadDashboard()
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.app_name)) },
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(dims.spacingMd),
            verticalArrangement = Arrangement.spacedBy(dims.spacingMd)
        ) {
            item {
                val isFirstTimeUser = state.lastWorkout == null && state.recentRecords.isEmpty()
                CommandCenterCard(
                    hasActiveSession = state.activeSession != null,
                    isFirstTimeUser = isFirstTimeUser,
                    onStartWorkout = { viewModel.startNewWorkout(onStartWorkout) },
                    onContinueWorkout = { state.activeSession?.let { onContinueWorkout(it.id) } }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dims.spacingSm)
                    ) {
                        StatCard(
                            label = stringResource(id = R.string.dashboard_this_week),
                            value = "${state.workoutsThisWeek}",
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = stringResource(id = R.string.dashboard_this_month),
                            value = "${state.workoutsThisMonth}",
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = stringResource(id = R.string.dashboard_streak),
                            value = stringResource(id = R.string.dashboard_streak_value, state.currentStreak),
                            modifier = Modifier.weight(1f)
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
                    items(state.recentRecords) { (record, exerciseName) ->
                        RecordCard(
                            exerciseName = exerciseName,
                            recordType = record.type.displayName,
                            recordValue = formatRecordValue(record.type.name, record.value)
                        )
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
    }
}

@Composable
private fun CommandCenterCard(
    hasActiveSession: Boolean,
    isFirstTimeUser: Boolean,
    onStartWorkout: () -> Unit,
    onContinueWorkout: () -> Unit
) {
    val dims = ironLogDimens
    val surfaces = ironLogSurfaceRoles

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isFirstTimeUser && !hasActiveSession) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.glassmorphism(
            backgroundColor = surfaces.elevated.copy(alpha = 0.5f)
        )
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = if (hasActiveSession) onContinueWorkout else onStartWorkout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dims.spacingXl + dims.spacingLg)
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
    recordValue: String
) {
    val dims = ironLogDimens
    val surfaces = ironLogSurfaceRoles

    Card(
        modifier = Modifier.fillMaxWidth()
            .glassmorphism(backgroundColor = surfaces.muted.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.spacingSm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = recordType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = recordValue,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
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
    val surfaces = ironLogSurfaceRoles

    Card(
        modifier = Modifier.fillMaxWidth()
            .glassmorphism(backgroundColor = surfaces.muted.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(dims.spacingSm)) {
            Text(
                text = dateTime,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(
                    id = R.string.dashboard_last_workout_meta,
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
        fontWeight = FontWeight.Bold
    )
}

private fun formatRecordValue(type: String, value: Double): String {
    return when (type) {
        "MAX_WEIGHT" -> "${value} kg"
        "MAX_REPS" -> "${value.toInt()} Wdh"
        "MAX_VOLUME" -> "${value.toInt()} kg"
        "MAX_E1RM" -> "${"%.1f".format(value)} kg"
        else -> "$value"
    }
}

@Composable
private fun OnboardingCard() {
    val dims = ironLogDimens
    val surfaces = ironLogSurfaceRoles

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .glassmorphism(backgroundColor = surfaces.elevated.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.spacingLg),
            verticalArrangement = Arrangement.spacedBy(dims.spacingMd)
        ) {
            Text(
                text = "Willkommen bei IronLog! \uD83D\uDE80",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Dein Trainingsverlauf ist noch leer. Starte dein erstes Workout über den Button oben. Sobald du Sätze loggst, findest du hier deine persönlichen Rekorde, Heatmaps und Statistiken.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

