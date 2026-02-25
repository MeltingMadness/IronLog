package com.ironlog.app.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class IronLogDimens(
    val spacing2: Dp = 4.dp,
    val spacingXs: Dp = 8.dp,
    val spacingSm: Dp = 14.dp,
    val spacingMd: Dp = 18.dp,
    val spacingLg: Dp = 28.dp,
    val spacingXl: Dp = 36.dp,
    val radiusSm: Dp = 8.dp,
    val radiusMd: Dp = 14.dp,
    val radiusLg: Dp = 20.dp,
    val radiusXl: Dp = 28.dp
)

@Immutable
data class IronLogMotion(
    val fastMillis: Int = 140,
    val normalMillis: Int = 240,
    val emphasisMillis: Int = 360,
    val reduced: Boolean = false
)

@Immutable
data class IronLogSurfaceRoles(
    val elevated: androidx.compose.ui.graphics.Color,
    val muted: androidx.compose.ui.graphics.Color,
    val accentSuccess: androidx.compose.ui.graphics.Color,
    val accentWarning: androidx.compose.ui.graphics.Color
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
