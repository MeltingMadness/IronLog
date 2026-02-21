package com.ironlog.app.presentation.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

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

    Text(
        text = timeText,
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}
