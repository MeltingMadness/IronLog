package com.ironlog.app.presentation.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironlog.core.designsystem.R
import com.ironlog.app.presentation.theme.glassmorphism
import com.ironlog.app.presentation.theme.semantic
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Duration
import java.time.Instant
import java.util.Locale

@Composable
fun RestTimer(
    startTime: Instant,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    titleText: String? = null,
    baseColor: Color? = null
) {
    var elapsed by remember { mutableLongStateOf(0L) }

    LaunchedEffect(startTime) {
        while (isActive) {
            elapsed = Duration.between(startTime, Instant.now()).seconds
            delay(1000)
        }
    }

    val minutes = elapsed / 60
    val seconds = elapsed % 60
    val timeText = String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val activeColor = baseColor ?: MaterialTheme.semantic.sky
    val containerColor = baseColor?.copy(alpha = 0.15f)
        ?: MaterialTheme.semantic.sky.copy(alpha = 0.14f)
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .glassmorphism(
                backgroundColor = containerColor,
                borderColor = activeColor.copy(alpha = 0.3f)
            )
            .clickable { onDismiss() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Pulsing dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(activeColor, CircleShape)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = titleText ?: stringResource(id = R.string.workout_rest_timer_prefix),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = timeText,
                style = MaterialTheme.typography.titleMedium.merge(
                    TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        fontFeatureSettings = "tnum"
                    )
                ),
                color = activeColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
