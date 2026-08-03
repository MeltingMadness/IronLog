package com.ironlog.app.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Radius {
    val xs = 6.dp
    val sm = 10.dp
    val md = 14.dp
    val lg = 18.dp
    val xl = 24.dp
    val xxl = 28.dp
    val pill = 999.dp
}

object IconSize {
    val sm = 18.dp
    val md = 22.dp
    val lg = 28.dp
    val xl = 36.dp
    val xxl = 72.dp
}

object ButtonSize {
    val height = 48.dp
    val heightSm = 36.dp
    val heightXs = 28.dp
    val iconButton = 40.dp
    val minWidth = 120.dp
}

@Immutable
data class IronLogDimens(
    val spacing2: Dp = 2.dp,
    val spacingXs: Dp = 4.dp,
    val spacingSm: Dp = 8.dp,
    val spacingMd: Dp = 12.dp,
    val spacingLg: Dp = 16.dp,
    val spacingXl: Dp = 24.dp
)

@Immutable
data class IronLogMotion(
    val fastMillis: Int = 130,
    val normalMillis: Int = 220,
    val emphasisMillis: Int = 300,
    val reduced: Boolean = false
)

@Immutable
data class IronLogSurfaceRoles(
    val elevated: Color,
    val muted: Color,
    val accentSuccess: Color,
    val accentWarning: Color
)

@Immutable
data class EmberSemanticColors(
    val success: Color,
    val danger: Color,
    val warning: Color,
    val rose: Color,
    val roseLight: Color,
    val sky: Color,
    val skyLight: Color,
    val violet: Color,
    val violetLight: Color,
    val teal: Color,
    val tealLight: Color
)

internal val LocalIronLogDimens = compositionLocalOf { IronLogDimens() }
internal val LocalIronLogMotion = compositionLocalOf { IronLogMotion() }
internal val LocalIronLogSurfaceRoles = compositionLocalOf {
    IronLogSurfaceRoles(
        elevated = SurfaceElevated,
        muted = SurfaceMuted,
        accentSuccess = AccentSuccess,
        accentWarning = AccentWarning
    )
}
internal val LocalEmberSemanticColors = compositionLocalOf {
    EmberSemanticColors(
        success = EmberSuccess,
        danger = EmberDanger,
        warning = EmberWarning,
        rose = EmberRose,
        roseLight = EmberRoseLight,
        sky = EmberSky,
        skyLight = EmberSkyLight,
        violet = EmberViolet,
        violetLight = EmberVioletLight,
        teal = EmberTeal,
        tealLight = EmberTealLight
    )
}
