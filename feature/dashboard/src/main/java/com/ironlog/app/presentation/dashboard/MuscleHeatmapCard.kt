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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironlog.core.designsystem.R
import com.ironlog.app.presentation.theme.glassmorphism
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.app.presentation.theme.ironLogSurfaceRoles
import com.ironlog.app.presentation.theme.semantic
import com.ironlog.app.presentation.theme.Radius

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
        modifier = modifier
            .fillMaxWidth()
            .glassmorphism(backgroundColor = surfaces.elevated.copy(alpha = 0.72f)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
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
                    heatmap.entries
                        .filter { (_, count) -> count > 0 }
                        .sortedByDescending { (_, count) -> count }
                        .forEach { (muscle, sets) ->
                            MuscleChip(
                                label = muscle.displayName,
                                sets = sets,
                                intensity = (sets.toFloat() / maxSets).coerceIn(0.2f, 1f)
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
    val chipColor = when {
        intensity >= 0.75f -> MaterialTheme.semantic.rose
        intensity >= 0.45f -> MaterialTheme.semantic.warning
        else -> MaterialTheme.semantic.teal
    }
    val backgroundColor = chipColor.copy(alpha = (intensity * 0.55f + 0.15f).coerceIn(0.15f, 0.7f))

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(backgroundColor)
            .padding(horizontal = dims.spacingSm, vertical = 6.dp)
    ) {
        Text(
            text = if (sets > 0) "$label ($sets)" else label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (sets > 0) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
