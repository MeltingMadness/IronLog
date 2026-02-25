package com.ironlog.app.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.layout.ColumnScope
import com.ironlog.app.presentation.theme.glassmorphism
import com.ironlog.app.presentation.theme.ironLogSurfaceRoles

enum class IronLogSurfaceTone {
    ELEVATED,
    MUTED
}

@Composable
fun IronLogSurfaceCard(
    modifier: Modifier = Modifier,
    tone: IronLogSurfaceTone = IronLogSurfaceTone.MUTED,
    alpha: Float = 0.68f,
    shape: Shape = MaterialTheme.shapes.large,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val surfaces = ironLogSurfaceRoles
    val backgroundColor = when (tone) {
        IronLogSurfaceTone.ELEVATED -> surfaces.elevated
        IronLogSurfaceTone.MUTED -> surfaces.muted
    }.copy(alpha = alpha)

    Card(
        modifier = modifier.glassmorphism(
            shape = shape,
            backgroundColor = backgroundColor
        ),
        shape = shape,
        border = border,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        content = content
    )
}
