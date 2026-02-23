package com.ironlog.app.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
    onError = OnError,
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
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = CyanOnTertiaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
    onError = OnError,
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
    onError = OnError,
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
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = DarkError,
    onError = DarkOnError,
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
    onError = DarkOnError,
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
    onError = DarkOnError,
)

/**
 * Derives [IronLogSurfaceRoles] from any [ColorScheme], so surface roles
 * stay visually consistent with dynamic or static themes.
 */
private fun deriveSurfaceRoles(
    colorScheme: ColorScheme,
    isDark: Boolean
): IronLogSurfaceRoles {
    // "elevated" = surface tinted slightly by primary (like elevation tone 2)
    val elevated = colorScheme.primary.copy(alpha = if (isDark) 0.08f else 0.05f)
        .compositeOver(colorScheme.surface)

    // "muted" = surface with subtle tint (like elevation tone 1)
    val muted = colorScheme.primary.copy(alpha = if (isDark) 0.05f else 0.04f)
        .compositeOver(colorScheme.surface)

    // "accentSuccess" = tertiary color serves as success semantic
    val accentSuccess = colorScheme.tertiary

    // "accentWarning" = primary serves as the attention/warning semantic
    val accentWarning = colorScheme.primary

    return IronLogSurfaceRoles(
        elevated = elevated,
        muted = muted,
        accentSuccess = accentSuccess,
        accentWarning = accentWarning
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
        isDynamic -> {
            if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
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

    val surfaceRoles = if (isDynamic) {
        // Derive surface roles from the dynamic color scheme so everything
        // stays visually harmonious with the wallpaper-based palette.
        deriveSurfaceRoles(colorScheme, isDarkTheme)
    } else if (isDarkTheme) {
        IronLogSurfaceRoles(
            elevated = DarkSurfaceElevated,
            muted = DarkSurfaceMuted,
            accentSuccess = DarkAccentSuccess,
            accentWarning = DarkAccentWarning
        )
    } else {
        IronLogSurfaceRoles(
            elevated = SurfaceElevated,
            muted = SurfaceMuted,
            accentSuccess = AccentSuccess,
            accentWarning = AccentWarning
        )
    }

    CompositionLocalProvider(
        LocalIronLogDimens provides IronLogDimens(),
        LocalIronLogMotion provides IronLogMotion(reduced = reducedMotion),
        LocalIronLogSurfaceRoles provides surfaceRoles
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
