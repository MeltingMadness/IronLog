package com.thrym.app.presentation.common

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
import com.thrym.app.domain.model.AppearanceStyle
import com.thrym.app.presentation.theme.GlassLevel
import com.thrym.app.presentation.theme.LocalAppearanceStyle
import com.thrym.app.presentation.theme.glassmorphism
import com.thrym.app.presentation.theme.liquidGlass
import com.thrym.app.presentation.theme.thrymSurfaceRoles

enum class ThrymSurfaceTone {
    ELEVATED,
    MUTED,
    ACCENT,
    COLORED
}

@Composable
fun ThrymSurfaceCard(
    modifier: Modifier = Modifier,
    tone: ThrymSurfaceTone = ThrymSurfaceTone.MUTED,
    alpha: Float = when (tone) {
        ThrymSurfaceTone.ELEVATED -> 0.74f
        ThrymSurfaceTone.MUTED -> 0.66f
        ThrymSurfaceTone.ACCENT -> 0.82f
        ThrymSurfaceTone.COLORED -> 0.72f
    },
    shape: Shape = MaterialTheme.shapes.large,
    border: BorderStroke? = null,
    semanticColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val surfaces = thrymSurfaceRoles
    val semanticTint = semanticColor ?: MaterialTheme.colorScheme.primary
    val backgroundColor = when (tone) {
        ThrymSurfaceTone.ELEVATED -> surfaces.elevated
        ThrymSurfaceTone.MUTED -> surfaces.muted
        ThrymSurfaceTone.ACCENT -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            .compositeOver(surfaces.elevated)
        ThrymSurfaceTone.COLORED -> semanticTint.copy(alpha = 0.08f)
            .compositeOver(surfaces.muted)
    }.copy(alpha = alpha)
    val resolvedBorder = border ?: BorderStroke(
        1.dp,
        when (tone) {
            ThrymSurfaceTone.ACCENT -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
            ThrymSurfaceTone.COLORED -> semanticTint.copy(alpha = 0.18f)
            ThrymSurfaceTone.ELEVATED -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
            ThrymSurfaceTone.MUTED -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
        }
    )
    val glassBorderColor = when (tone) {
        ThrymSurfaceTone.ACCENT -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        ThrymSurfaceTone.COLORED -> semanticTint.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(
            alpha = if (tone == ThrymSurfaceTone.ELEVATED) 0.38f else 0.24f
        )
    }

    if (LocalAppearanceStyle.current == AppearanceStyle.LIQUID_GLASS) {
        // Liquid Glass draws its own tint, sheen and edge; the Ember border would double it.
        Card(
            modifier = modifier.liquidGlass(
                level = when (tone) {
                    ThrymSurfaceTone.ELEVATED, ThrymSurfaceTone.ACCENT -> GlassLevel.STRONG
                    ThrymSurfaceTone.MUTED -> GlassLevel.STANDARD
                    ThrymSurfaceTone.COLORED -> GlassLevel.TINT
                },
                shape = shape,
                tint = semanticColor
            ),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            content = content
        )
        return
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
