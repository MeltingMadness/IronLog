package com.ironlog.app.presentation.exercises

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironlog.app.domain.model.AppearanceStyle
import com.ironlog.app.presentation.theme.GlassLevel
import com.ironlog.app.presentation.theme.LocalAppearanceStyle
import com.ironlog.app.presentation.theme.liquidGlass

@Composable
internal fun isLiquidGlass(): Boolean = LocalAppearanceStyle.current == AppearanceStyle.LIQUID_GLASS

private val ExerciseRowShape = RoundedCornerShape(22.dp)

/** Liquid Glass: every exercise is its own glass row; Ember keeps the plain list item. */
@Composable
internal fun Modifier.exerciseRowSurface(): Modifier =
    if (isLiquidGlass()) liquidGlass(GlassLevel.STANDARD, ExerciseRowShape) else this

/** Transparent list item on glass so the row surface shows through. */
@Composable
internal fun exerciseRowColors(): ListItemColors =
    if (isLiquidGlass()) ListItemDefaults.colors(containerColor = Color.Transparent) else ListItemDefaults.colors()

@Composable
internal fun ExerciseName(name: String) {
    if (isLiquidGlass()) Text(name, fontWeight = FontWeight.ExtraBold) else Text(name)
}
