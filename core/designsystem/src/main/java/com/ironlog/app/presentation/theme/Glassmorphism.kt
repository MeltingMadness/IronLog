package com.ironlog.app.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.glassmorphism(
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = Color.White.copy(alpha = 0.56f),
    borderColor: Color = Color(0xFFFFB464).copy(alpha = 0.18f),
    borderWidth: Dp = 1.dp,
    specularHighlight: Boolean = true
): Modifier = composed {
    val scheme = MaterialTheme.colorScheme
    // Amber stays as the neutral fallback via the default parameter colors above;
    // the tint layers follow the active scheme (primary/onSurface with alpha).
    val highlight = scheme.primary
    val shadow = scheme.onSurface

    this
        .clip(shape)
        .background(backgroundColor, shape)
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    highlight.copy(alpha = 0.07f),
                    Color.Transparent,
                    shadow.copy(alpha = 0.10f)
                )
            ),
            shape = shape
        )
        .border(
            width = borderWidth,
            brush = Brush.linearGradient(
                colors = listOf(
                    borderColor,
                    borderColor.copy(alpha = 0.10f),
                    Color.Transparent,
                    borderColor.copy(alpha = 0.18f)
                )
            ),
            shape = shape
        )
        .drawWithContent {
            drawContent()
            if (specularHighlight) {
                val specularBrush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to highlight.copy(alpha = 0.16f),
                        0.25f to highlight.copy(alpha = 0.05f),
                        1f to Color.Transparent
                    ),
                    start = Offset.Zero,
                    end = Offset(0f, minOf(size.height * 0.35f, 60f))
                )
                drawRect(specularBrush)
            }
        }
}

fun Modifier.glow(
    color: Color,
    radius: Dp = 20.dp,
    alpha: Float = 0.5f
) = this.then(
    Modifier
        .blur(radius, BlurredEdgeTreatment.Unbounded)
        .background(color.copy(alpha = alpha))
)
