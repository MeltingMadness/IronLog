package com.ironlog.app.presentation.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.app.presentation.theme.Radius

/**
 * Applies a sweeping shimmer effect to any composable.
 * Uses a traveling linear gradient over the surface color.
 */
fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing)
        ),
        label = "shimmerOffset"
    )
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val surface = MaterialTheme.colorScheme.surface

    this.drawWithContent {
        drawContent()
        val shimmerColors = listOf(
            surfaceVariant,
            surface.copy(alpha = 0.85f),
            surfaceVariant
        )
        val shimmerBrush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(size.width * offset, 0f),
            end = Offset(size.width * (offset + 1f), size.height * 0.5f)
        )
        drawRect(shimmerBrush)
    }
}

@Composable
fun DashboardSkeleton(modifier: Modifier = Modifier) {
    val dims = ironLogDimens
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dims.spacingMd),
        verticalArrangement = Arrangement.spacedBy(dims.spacingMd)
    ) {
        // CommandCenter placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(Radius.lg))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .shimmerEffect()
        )
        // StatCards row placeholder
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dims.spacingSm)
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp)
                        .clip(RoundedCornerShape(Radius.lg))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .shimmerEffect()
                )
            }
        }
        // Heatmap placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(Radius.lg))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .shimmerEffect()
        )
    }
}

@Composable
fun HistorySkeleton(modifier: Modifier = Modifier) {
    val dims = ironLogDimens
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dims.spacingMd),
        verticalArrangement = Arrangement.spacedBy(dims.spacingSm)
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(Radius.lg))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .shimmerEffect()
            )
        }
    }
}

@Composable
fun LoadingSkeleton(
    lineCount: Int,
    modifier: Modifier = Modifier
) {
    val dims = ironLogDimens
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dims.spacingMd),
        verticalArrangement = Arrangement.spacedBy(dims.spacingMd)
    ) {
        repeat(lineCount) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .shimmerEffect()
            )
        }
    }
}
