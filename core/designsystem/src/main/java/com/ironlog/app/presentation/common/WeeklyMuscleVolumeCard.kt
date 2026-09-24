package com.ironlog.app.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ironlog.app.domain.util.MuscleVolume
import com.ironlog.app.domain.util.VolumeStatus
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.app.presentation.theme.semantic
import com.ironlog.core.designsystem.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WEEK_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

/**
 * Wöchentliches Volumen je Muskelgruppe mit Fortschrittsbalken Richtung MRV,
 * Markern für MEV/MAV und einer vorsichtigen Einordnung des erreichten Bereichs.
 *
 * The card is shared by Dashboard and Statistics. Callers that have no week
 * navigation can omit the date and callbacks; callers that load data remotely
 * should provide [isLoading] and [error] so neither state is shown as zero sets.
 */
@Composable
fun WeeklyMuscleVolumeCard(
    volumes: List<MuscleVolume>,
    modifier: Modifier = Modifier,
    weekStart: LocalDate? = null,
    currentWeekStart: LocalDate? = null,
    completedWorkoutCount: Int? = null,
    isLoading: Boolean = false,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
    onPreviousWeek: (() -> Unit)? = null,
    onNextWeek: (() -> Unit)? = null,
    collapsible: Boolean = false
) {
    val dims = ironLogDimens
    val isCurrentWeek = weekStart != null && weekStart == currentWeekStart
    val hasAnyVolume = volumes.any { it.weeklySets > 0.0 }
    var isExpanded by rememberSaveable(collapsible) { mutableStateOf(!collapsible) }

    IronLogSurfaceCard(
        modifier = modifier.fillMaxWidth(),
        tone = IronLogSurfaceTone.MUTED
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.spacingMd),
            verticalArrangement = Arrangement.spacedBy(dims.spacingSm)
        ) {
            Text(
                text = stringResource(id = R.string.stats_weekly_volume_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(id = R.string.stats_weekly_volume_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (weekStart != null) {
                WeeklyVolumeWeekHeader(
                    weekStart = weekStart,
                    currentWeekStart = currentWeekStart,
                    isCurrentWeek = isCurrentWeek,
                    onPreviousWeek = onPreviousWeek,
                    onNextWeek = onNextWeek
                )
            }

            if (!isLoading && error == null && completedWorkoutCount != null) {
                Text(
                    text = stringResource(
                        id = R.string.stats_weekly_volume_summary,
                        formatSets(volumes.sumOf { it.weeklySets }),
                        completedWorkoutCount
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (collapsible) {
                val toggleLabel = stringResource(
                    id = if (isExpanded) {
                        R.string.stats_weekly_volume_collapse
                    } else {
                        R.string.stats_weekly_volume_expand
                    }
                )
                val expandedState = stringResource(
                    if (isExpanded) R.string.stats_weekly_volume_expanded
                    else R.string.stats_weekly_volume_collapsed
                )
                TextButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics {
                            role = Role.Button
                            stateDescription = expandedState
                        }
                ) {
                    Icon(
                        imageVector = if (isExpanded) {
                            Icons.Default.ExpandLess
                        } else {
                            Icons.Default.ExpandMore
                        },
                        contentDescription = null
                    )
                    Text(text = toggleLabel)
                }
            }

            when {
                isLoading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = dims.spacingLg),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp
                        )
                    }
                }

                error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = dims.spacingSm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(dims.spacingXs)
                    ) {
                        Text(
                            text = stringResource(id = R.string.stats_weekly_volume_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        onRetry?.let { retry ->
                            TextButton(onClick = retry) {
                                Text(stringResource(id = R.string.stats_weekly_volume_retry))
                            }
                        }
                    }
                }

                else -> {
                    if (isExpanded) {
                        if (volumes.isEmpty()) {
                            Text(
                                text = stringResource(id = R.string.stats_weekly_volume_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = dims.spacingSm)
                            )
                        }
                        if (!hasAnyVolume && volumes.isNotEmpty()) {
                            Text(
                                text = stringResource(id = R.string.stats_weekly_volume_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = dims.spacingSm)
                            )
                        }
                        volumes.forEach { volume ->
                            MuscleVolumeRow(volume = volume)
                        }

                        Text(
                            text = stringResource(id = R.string.stats_weekly_volume_orientation_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyVolumeWeekHeader(
    weekStart: LocalDate,
    currentWeekStart: LocalDate?,
    isCurrentWeek: Boolean,
    onPreviousWeek: (() -> Unit)?,
    onNextWeek: (() -> Unit)?
) {
    val dims = ironLogDimens
    val isNextEnabled = onNextWeek != null &&
        currentWeekStart != null &&
        weekStart.isBefore(currentWeekStart)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dims.spacingXs)
        ) {
            Text(
                text = stringResource(
                    id = R.string.stats_weekly_volume_week_range,
                    weekStart.format(WEEK_DATE_FORMAT),
                    weekStart.plusDays(6).format(WEEK_DATE_FORMAT)
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(
                    id = if (isCurrentWeek) {
                        R.string.stats_weekly_volume_week_current
                    } else {
                        R.string.stats_weekly_volume_week_complete
                    }
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (onPreviousWeek != null || onNextWeek != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                onPreviousWeek?.let { previous ->
                    IconButton(
                        onClick = previous,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = stringResource(
                                id = R.string.stats_weekly_volume_previous_week
                            )
                        )
                    }
                }
                onNextWeek?.let { next ->
                    IconButton(
                        onClick = next,
                        enabled = isNextEnabled,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = stringResource(
                                id = R.string.stats_weekly_volume_next_week
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MuscleVolumeRow(volume: MuscleVolume) {
    val dims = ironLogDimens
    val hasSets = volume.weeklySets > 0.0
    val status = volume.status
    val statusColor = if (hasSets) {
        status.statusColor()
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(verticalArrangement = Arrangement.spacedBy(dims.spacingXs)) {
        Text(
            text = volume.muscleGroup.displayName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(
                id = R.string.stats_volume_sets_progress,
                formatSets(volume.weeklySets),
                formatSets(volume.thresholds.mav)
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        VolumeProgressBar(
            progress = volume.progress,
            mevFraction = volume.mevFraction(),
            mavFraction = volume.mavFraction(),
            color = statusColor
        )

        Text(
            text = stringResource(
                id = if (hasSets) {
                    status.orientingStatusLabelRes()
                } else {
                    R.string.stats_volume_status_no_sets
                }
            ),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = statusColor
        )
        Text(
            text = stringResource(
                id = R.string.stats_volume_thresholds,
                formatSets(volume.thresholds.mev),
                formatSets(volume.thresholds.mav),
                formatSets(volume.thresholds.mrv)
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}

/**
 * Fortschrittsbalken mit farbigem Füllstand Richtung MRV und vertikalen
 * Markern für MEV (Start des Zielbereichs) und MAV (Zielwert).
 */
@Composable
private fun VolumeProgressBar(
    progress: Float,
    mevFraction: Float,
    mavFraction: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val barHeight = 10.dp
    val cornerRadius = RoundedCornerShape(barHeight / 2)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = cornerRadius
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color = color.copy(alpha = 0.85f), shape = cornerRadius)
        )

        VolumeBarMarker(fraction = mevFraction)
        VolumeBarMarker(fraction = mavFraction)
    }
}

@Composable
private fun VolumeBarMarker(fraction: Float) {
    if (fraction <= 0f) return
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction.coerceIn(0f, 1f))
            .fillMaxHeight()
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
                .align(Alignment.CenterEnd)
                .background(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f))
        )
    }
}

@Composable
private fun VolumeStatus.statusColor(): Color = when (this) {
    VolumeStatus.LOW -> MaterialTheme.semantic.warning
    VolumeStatus.OPTIMAL -> MaterialTheme.semantic.success
    VolumeStatus.HIGH -> MaterialTheme.semantic.danger
}

private fun VolumeStatus.orientingStatusLabelRes(): Int = when (this) {
    VolumeStatus.LOW -> R.string.stats_volume_status_below_mev
    VolumeStatus.OPTIMAL -> R.string.stats_volume_status_in_orientation
    VolumeStatus.HIGH -> R.string.stats_volume_status_above_mrv
}

private fun formatSets(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.GERMANY, "%.1f", value)
    }

private fun MuscleVolume.mevFraction(): Float =
    (thresholds.mev / thresholds.mrv).toFloat().coerceIn(0f, 1f)

private fun MuscleVolume.mavFraction(): Float =
    (thresholds.mav / thresholds.mrv).toFloat().coerceIn(0f, 1f)
