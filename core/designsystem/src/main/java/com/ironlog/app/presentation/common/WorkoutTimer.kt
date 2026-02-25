package com.ironlog.app.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

private val RING_SIZE = 120.dp
private val RING_STROKE = 2.dp
private const val WORKOUT_TARGET_SECONDS = 3600f // 60 min goal

@Composable
fun WorkoutTimer(
    startTime: LocalDateTime,
    modifier: Modifier = Modifier
) {
    var elapsed by remember { mutableLongStateOf(0L) }

    LaunchedEffect(startTime) {
        while (isActive) {
            val now = LocalDateTime.now()
            elapsed = now.toEpochSecond(ZoneOffset.UTC) - startTime.toEpochSecond(ZoneOffset.UTC)
            delay(1000)
        }
    }

    val hours = elapsed / 3600
    val minutes = (elapsed % 3600) / 60
    val seconds = elapsed % 60

    val timeText = if (hours > 0) {
        String.format(Locale.GERMAN, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.GERMAN, "%02d:%02d", minutes, seconds)
    }

    val fraction = (elapsed / WORKOUT_TARGET_SECONDS).coerceIn(0f, 1f)
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(RING_SIZE)) {
            val strokePx = RING_STROKE.toPx()
            val inset = strokePx / 2f
            // Track arc (full circle, low alpha)
            drawArc(
                color = primary.copy(alpha = 0.18f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokePx)
            )
            // Progress arc
            if (fraction > 0f) {
                drawArc(
                    color = primary.copy(alpha = 0.80f),
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        Text(
            text = timeText,
            style = MaterialTheme.typography.headlineLarge.merge(
                TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    fontFeatureSettings = "tnum"
                )
            ),
            color = primary
        )
    }
}
