package com.ironlog.app.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.glassmorphism(
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = Color.White.copy(alpha = 0.58f),
    borderColor: Color = Color.White.copy(alpha = 0.34f),
    borderWidth: Dp = 1.dp
): Modifier = this.then(
    Modifier
        .clip(shape)
        .background(backgroundColor, shape)
        .border(
            width = borderWidth,
            brush = Brush.linearGradient(
                colors = listOf(
                    borderColor,
                    borderColor.copy(alpha = 0.16f),
                    Color.Transparent,
                    borderColor.copy(alpha = 0.24f)
                )
            ),
            shape = shape
        )
)

fun Modifier.glow(
    color: Color,
    radius: Dp = 20.dp,
    alpha: Float = 0.5f
) = this.then(
    Modifier
        .blur(radius, BlurredEdgeTreatment.Unbounded)
        .background(color.copy(alpha = alpha))
)
