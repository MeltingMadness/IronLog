package com.ironlog.app.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import com.ironlog.app.presentation.theme.glassmorphism
import com.ironlog.app.presentation.theme.ironLogSurfaceRoles

enum class IronLogSurfaceTone {
    ELEVATED,
    MUTED,
    ACCENT,
    COLORED
}

@Composable
fun IronLogSurfaceCard(
    modifier: Modifier = Modifier,
    tone: IronLogSurfaceTone = IronLogSurfaceTone.MUTED,
    alpha: Float = when (tone) {
        IronLogSurfaceTone.ELEVATED -> 0.74f
        IronLogSurfaceTone.MUTED -> 0.66f
        IronLogSurfaceTone.ACCENT -> 0.82f
        IronLogSurfaceTone.COLORED -> 0.72f
    },
    shape: Shape = MaterialTheme.shapes.large,
    border: BorderStroke? = null,
    semanticColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val surfaces = ironLogSurfaceRoles
    val semanticTint = semanticColor ?: MaterialTheme.colorScheme.primary
    val backgroundColor = when (tone) {
        IronLogSurfaceTone.ELEVATED -> surfaces.elevated
        IronLogSurfaceTone.MUTED -> surfaces.muted
        IronLogSurfaceTone.ACCENT -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            .compositeOver(surfaces.elevated)
        IronLogSurfaceTone.COLORED -> semanticTint.copy(alpha = 0.08f)
            .compositeOver(surfaces.muted)
    }.copy(alpha = alpha)
    val resolvedBorder = border ?: BorderStroke(
        1.dp,
        when (tone) {
            IronLogSurfaceTone.ACCENT -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
            IronLogSurfaceTone.COLORED -> semanticTint.copy(alpha = 0.18f)
            IronLogSurfaceTone.ELEVATED -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
            IronLogSurfaceTone.MUTED -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
        }
    )
    val glassBorderColor = when (tone) {
        IronLogSurfaceTone.ACCENT -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        IronLogSurfaceTone.COLORED -> semanticTint.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(
            alpha = if (tone == IronLogSurfaceTone.ELEVATED) 0.38f else 0.24f
        )
    }

    Card(
        modifier = modifier.glassmorphism(
            shape = shape,
            backgroundColor = backgroundColor,
            borderColor = glassBorderColor
        ),
        shape = shape,
        border = resolvedBorder,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        content = content
    )
}
