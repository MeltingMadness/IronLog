package com.ironlog.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.ironlog.app.domain.model.ThemeScheme

val ironLogDimens: IronLogDimens
    @Composable get() = LocalIronLogDimens.current

val ironLogMotion: IronLogMotion
    @Composable get() = LocalIronLogMotion.current

val ironLogSurfaceRoles: IronLogSurfaceRoles
    @Composable get() = LocalIronLogSurfaceRoles.current

val MaterialTheme.semantic: EmberSemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalEmberSemanticColors.current

internal val LocalAccentText = staticCompositionLocalOf { AmberDarkAccentText }

val MaterialTheme.accentText: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAccentText.current

internal fun accentTextFor(scheme: ThemeScheme, dark: Boolean): Color = when (scheme) {
    ThemeScheme.AMBER -> if (dark) AmberDarkAccentText else AmberLightAccentText
    ThemeScheme.DEEP_CYAN -> if (dark) CyanDarkAccentText else CyanLightAccentText
    ThemeScheme.NEON_RED -> if (dark) RedDarkAccentText else RedLightAccentText
    ThemeScheme.FORGE -> if (dark) ForgeDarkAccentText else ForgeLightAccentText
    ThemeScheme.RASTER -> if (dark) RasterDarkAccentText else RasterLightAccentText
    ThemeScheme.TIDE -> if (dark) TideDarkAccentText else TideLightAccentText
    ThemeScheme.PULSE -> if (dark) PulseDarkAccentText else PulseLightAccentText
}
