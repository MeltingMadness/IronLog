package com.ironlog.app.presentation.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ironlog.core.designsystem.R
import com.ironlog.app.domain.util.MuscleVolume
import com.ironlog.app.domain.util.VolumeStatus
import com.ironlog.app.presentation.common.IronLogSurfaceCard
import com.ironlog.app.presentation.common.IronLogSurfaceTone
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.app.presentation.theme.semantic
import java.util.Locale

/**
 * Wöchentliches Volumen je Muskelgruppe mit Fortschrittsbalken Richtung MRV,
 * Markern für MEV/MAV und farbcodiertem Status (Niedrig / Optimal / Hoch).
 */
@Composable
fun WeeklyMuscleVolumeCard(
    volumes: List<MuscleVolume>,
    modifier: Modifier = Modifier
) {
    val dims = ironLogDimens

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
            } else {
                volumes.forEach { volume ->
                    MuscleVolumeRow(volume = volume)
                }
            }
        }
    }
}

@Composable
private fun MuscleVolumeRow(volume: MuscleVolume) {
    val dims = ironLogDimens
    val status = volume.status
    val statusColor = status.statusColor()

    Column(verticalArrangement = Arrangement.spacedBy(dims.spacingXs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = volume.muscleGroup.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(id = R.string.stats_volume_sets_count, formatSets(volume.weeklySets)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        VolumeProgressBar(
            progress = volume.progress,
            mevFraction = volume.mevFraction(),
            mavFraction = volume.mavFraction(),
            color = statusColor
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = status.statusLabelRes()),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
            Text(
                text = "MEV ${formatSets(volume.thresholds.mev)} · MAV ${formatSets(volume.thresholds.mav)} · MRV ${formatSets(volume.thresholds.mrv)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
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

        VolumeBarMarker(fraction = mevFraction, color = color)
        VolumeBarMarker(fraction = mavFraction, color = color)
    }
}

@Composable
private fun VolumeBarMarker(fraction: Float, color: Color) {
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

private fun VolumeStatus.statusLabelRes(): Int = when (this) {
    VolumeStatus.LOW -> R.string.stats_volume_status_low
    VolumeStatus.OPTIMAL -> R.string.stats_volume_status_optimal
    VolumeStatus.HIGH -> R.string.stats_volume_status_high
}

private fun formatSets(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }

private fun MuscleVolume.mevFraction(): Float =
    (thresholds.mev / thresholds.mrv).toFloat().coerceIn(0f, 1f)

private fun MuscleVolume.mavFraction(): Float =
    (thresholds.mav / thresholds.mrv).toFloat().coerceIn(0f, 1f)