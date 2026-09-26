package com.ironlog.app.presentation.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ironlog.app.presentation.common.WorkoutTimer
import com.ironlog.app.presentation.theme.AthleticNumber
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.core.designsystem.R
import java.time.LocalDateTime

/**
 * Workout header: the elapsed-time ring on the left, set progress, volume and running
 * rest timers on the right. The right column stays below the ring height, so a rest
 * timer appearing does not push the exercise list down.
 */
@Composable
internal fun WorkoutHeader(
    startTime: LocalDateTime,
    loggedSetCount: Int,
    plannedSetCount: Int,
    completedPlannedSetCount: Int,
    volumeText: String,
    modifier: Modifier = Modifier,
    restTimers: @Composable ColumnScope.() -> Unit = {}
) {
    val dims = ironLogDimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dims.spacingMd, vertical = dims.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WorkoutTimer(startTime = startTime)
        Spacer(Modifier.width(dims.spacingMd))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dims.spacingXs)
        ) {
            Text(
                text = stringResource(R.string.workout_header_progress_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (plannedSetCount > 0) {
                    stringResource(
                        R.string.workout_header_progress_planned,
                        completedPlannedSetCount,
                        plannedSetCount
                    )
                } else {
                    pluralStringResource(R.plurals.workout_header_progress_free, loggedSetCount, loggedSetCount)
                },
                style = AthleticNumber,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (plannedSetCount > 0) {
                LinearProgressIndicator(
                    progress = { (completedPlannedSetCount.toFloat() / plannedSetCount).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
            }
            Text(
                text = stringResource(R.string.workout_header_volume, volumeText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            restTimers()
        }
    }
}
