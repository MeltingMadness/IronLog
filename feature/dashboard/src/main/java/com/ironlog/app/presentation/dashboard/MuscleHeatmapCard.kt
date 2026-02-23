package com.ironlog.app.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironlog.core.designsystem.R
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.app.presentation.theme.ironLogSurfaceRoles

/**
 * Displays a visual heatmap of muscle groups trained this week.
 * Each muscle group is shown as a colored chip with intensity based on set count.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleHeatmapCard(
    heatmap: Map<MuscleGroup, Int>,
    modifier: Modifier = Modifier
) {
    val dims = ironLogDimens
    val surfaces = ironLogSurfaceRoles
    val maxSets = heatmap.values.maxOrNull() ?: 1

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaces.elevated)
    ) {
        Column(
            modifier = Modifier.padding(dims.spacingMd),
            verticalArrangement = Arrangement.spacedBy(dims.spacingSm)
        ) {
            Text(
                text = stringResource(id = R.string.dashboard_muscle_heatmap_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (heatmap.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.dashboard_muscle_heatmap_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(dims.spacingXs),
                    verticalArrangement = Arrangement.spacedBy(dims.spacingXs)
                ) {
                    MuscleGroup.entries.forEach { muscle ->
                        val sets = heatmap[muscle] ?: 0
                        MuscleChip(
                            label = muscle.displayName,
                            sets = sets,
                            intensity = if (sets > 0) (sets.toFloat() / maxSets).coerceIn(0.2f, 1f) else 0f
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MuscleChip(
    label: String,
    sets: Int,
    intensity: Float
) {
    val dims = ironLogDimens
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = if (intensity > 0f) {
        primaryColor.copy(alpha = intensity * 0.6f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val textColor = if (intensity > 0.4f) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(dims.radiusSm))
            .background(backgroundColor)
            .padding(horizontal = dims.spacingSm, vertical = 6.dp)
    ) {
        Text(
            text = if (sets > 0) "$label ($sets)" else label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = if (sets > 0) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
