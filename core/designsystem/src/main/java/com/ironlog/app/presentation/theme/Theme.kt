package com.ironlog.app.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import com.ironlog.app.domain.model.ThemeMode
import com.ironlog.app.domain.model.ThemeScheme

private val AmberLightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = EmberBackground,
    onBackground = EmberOnBackground,
    surface = EmberSurface,
    onSurface = EmberOnSurface,
    surfaceVariant = EmberSurfaceVariant,
    onSurfaceVariant = EmberOnSurfaceVariant,
    error = EmberDanger,
    onError = OnError
)

private val CyanLightColorScheme = lightColorScheme(
    primary = CyanPrimary,
    onPrimary = CyanOnPrimary,
    primaryContainer = CyanPrimaryContainer,
    onPrimaryContainer = CyanOnPrimaryContainer,
    secondary = CyanSecondary,
    onSecondary = CyanOnSecondary,
    secondaryContainer = CyanSecondaryContainer,
    onSecondaryContainer = CyanOnSecondaryContainer,
    tertiary = CyanTertiary,
    onTertiary = CyanOnTertiary,
    tertiaryContainer = CyanTertiaryContainer,
    onTertiaryContainer = CyanOnTertiaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
    onError = OnError
)

private val RedLightColorScheme = lightColorScheme(
    primary = RedPrimary,
    onPrimary = RedOnPrimary,
    primaryContainer = RedPrimaryContainer,
    onPrimaryContainer = RedOnPrimaryContainer,
    secondary = RedSecondary,
    onSecondary = RedOnSecondary,
    secondaryContainer = RedSecondaryContainer,
    onSecondaryContainer = RedOnSecondaryContainer,
    tertiary = RedTertiary,
    onTertiary = RedOnTertiary,
    tertiaryContainer = RedTertiaryContainer,
    onTertiaryContainer = RedOnTertiaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
    onError = OnError
)

private val AmberDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = EmberDarkBackground,
    onBackground = EmberDarkOnBackground,
    surface = EmberDarkSurface,
    onSurface = EmberDarkOnSurface,
    surfaceVariant = EmberDarkSurfaceVariant,
    onSurfaceVariant = EmberDarkOnSurfaceVariant,
    error = EmberDanger,
    onError = DarkOnError
)

private val CyanDarkColorScheme = darkColorScheme(
    primary = DarkCyanPrimary,
    onPrimary = DarkCyanOnPrimary,
    primaryContainer = DarkCyanPrimaryContainer,
    onPrimaryContainer = DarkCyanOnPrimaryContainer,
    secondary = DarkCyanSecondary,
    onSecondary = DarkCyanOnSecondary,
    secondaryContainer = DarkCyanSecondaryContainer,
    onSecondaryContainer = DarkCyanOnSecondaryContainer,
    tertiary = DarkCyanTertiary,
    onTertiary = DarkCyanOnTertiary,
    tertiaryContainer = DarkCyanTertiaryContainer,
    onTertiaryContainer = DarkCyanOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = DarkError,
    onError = DarkOnError
)

private val RedDarkColorScheme = darkColorScheme(
    primary = DarkRedPrimary,
    onPrimary = DarkRedOnPrimary,
    primaryContainer = DarkRedPrimaryContainer,
    onPrimaryContainer = DarkRedOnPrimaryContainer,
    secondary = DarkRedSecondary,
    onSecondary = DarkRedOnSecondary,
    secondaryContainer = DarkRedSecondaryContainer,
    onSecondaryContainer = DarkRedOnSecondaryContainer,
    tertiary = DarkRedTertiary,
    onTertiary = DarkRedOnTertiary,
    tertiaryContainer = DarkRedTertiaryContainer,
    onTertiaryContainer = DarkRedOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = DarkError,
    onError = DarkOnError
)

private fun deriveSurfaceRoles(
    colorScheme: ColorScheme,
    isDark: Boolean
): IronLogSurfaceRoles {
    val tintBase = if (isDark) DarkPrimary else Primary
    val elevated = tintBase.copy(alpha = if (isDark) 0.10f else 0.06f)
        .compositeOver(colorScheme.surface)
    val muted = tintBase.copy(alpha = if (isDark) 0.06f else 0.04f)
        .compositeOver(colorScheme.surface)

    return IronLogSurfaceRoles(
        elevated = elevated,
        muted = muted,
        accentSuccess = EmberSuccess,
        accentWarning = tintBase
    )
}

@Composable
fun IronLogTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    themeScheme: ThemeScheme = ThemeScheme.AMBER,
    useDynamicColor: Boolean = false,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val isDynamic = useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        isDynamic -> if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        isDarkTheme -> when (themeScheme) {
            ThemeScheme.AMBER -> AmberDarkColorScheme
            ThemeScheme.DEEP_CYAN -> CyanDarkColorScheme
            ThemeScheme.NEON_RED -> RedDarkColorScheme
        }
        else -> when (themeScheme) {
            ThemeScheme.AMBER -> AmberLightColorScheme
            ThemeScheme.DEEP_CYAN -> CyanLightColorScheme
            ThemeScheme.NEON_RED -> RedLightColorScheme
        }
    }

    val semanticColors = EmberSemanticColors(
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

    val surfaceRoles = if (isDynamic) {
        deriveSurfaceRoles(colorScheme, isDarkTheme)
    } else if (isDarkTheme) {
        when (themeScheme) {
            ThemeScheme.AMBER -> IronLogSurfaceRoles(
                elevated = EmberDarkSurfaceElevated,
                muted = EmberDarkSurfaceMuted,
                accentSuccess = EmberSuccess,
                accentWarning = DarkPrimary
            )
            ThemeScheme.DEEP_CYAN -> IronLogSurfaceRoles(
                elevated = DarkSurfaceElevated,
                muted = DarkSurfaceMuted,
                accentSuccess = DarkAccentSuccess,
                accentWarning = DarkCyanPrimary
            )
            ThemeScheme.NEON_RED -> IronLogSurfaceRoles(
                elevated = DarkSurfaceElevated,
                muted = DarkSurfaceMuted,
                accentSuccess = DarkAccentSuccess,
                accentWarning = DarkRedPrimary
            )
        }
    } else {
        when (themeScheme) {
            ThemeScheme.AMBER -> IronLogSurfaceRoles(
                elevated = EmberSurfaceElevated,
                muted = EmberSurfaceMuted,
                accentSuccess = EmberSuccess,
                accentWarning = Primary
            )
            ThemeScheme.DEEP_CYAN -> IronLogSurfaceRoles(
                elevated = SurfaceElevated,
                muted = SurfaceMuted,
                accentSuccess = AccentSuccess,
                accentWarning = CyanPrimary
            )
            ThemeScheme.NEON_RED -> IronLogSurfaceRoles(
                elevated = SurfaceElevated,
                muted = SurfaceMuted,
                accentSuccess = AccentSuccess,
                accentWarning = RedPrimary
            )
        }
    }

    CompositionLocalProvider(
        LocalIronLogDimens provides IronLogDimens(),
        LocalIronLogMotion provides IronLogMotion(reduced = reducedMotion),
        LocalIronLogSurfaceRoles provides surfaceRoles,
        LocalEmberSemanticColors provides semanticColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
