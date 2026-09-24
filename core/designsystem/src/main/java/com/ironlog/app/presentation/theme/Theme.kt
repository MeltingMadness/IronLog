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
    surfaceContainerLowest = EmberSurface,
    surfaceContainerLow = EmberSurfaceElevated,
    surfaceContainer = EmberSurfaceMuted,
    surfaceContainerHigh = EmberSurfaceVariant,
    surfaceContainerHighest = EmberSurfaceContainerHighest,
    error = Error,
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
    surfaceContainerLowest = SurfaceElevated,
    surfaceContainerLow = Surface,
    surfaceContainer = SurfaceMuted,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceVariant,
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
    surfaceContainerLowest = SurfaceElevated,
    surfaceContainerLow = Surface,
    surfaceContainer = SurfaceMuted,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceVariant,
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
    surfaceContainerLowest = EmberDarkBackground,
    surfaceContainerLow = EmberDarkSurface,
    surfaceContainer = EmberDarkSurfaceMuted,
    surfaceContainerHigh = EmberDarkSurfaceVariant,
    surfaceContainerHighest = EmberDarkSurfaceElevated,
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
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceMuted,
    surfaceContainerHigh = DarkSurfaceElevated,
    surfaceContainerHighest = DarkSurfaceVariant,
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
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceMuted,
    surfaceContainerHigh = DarkSurfaceElevated,
    surfaceContainerHighest = DarkSurfaceVariant,
    error = DarkError,
    onError = DarkOnError
)

private val ForgeLightColorScheme = lightColorScheme(
    primary = ForgePrimary,
    onPrimary = ForgeOnPrimary,
    primaryContainer = ForgePrimaryContainer,
    onPrimaryContainer = ForgeOnPrimaryContainer,
    secondary = ForgeSecondary,
    onSecondary = ForgeOnSecondary,
    secondaryContainer = ForgeSecondaryContainer,
    onSecondaryContainer = ForgeOnSecondaryContainer,
    tertiary = ForgeTertiary,
    onTertiary = ForgeOnTertiary,
    background = ForgeLightBackground,
    onBackground = OnBackground,
    surface = ForgeLightSurface,
    onSurface = OnSurface,
    surfaceVariant = ForgeLightSurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = ForgeLightSurface,
    surfaceContainerLow = SurfaceElevated,
    surfaceContainer = SurfaceMuted,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = ForgeLightSurfaceVariant,
    error = Error,
    onError = OnError
)

private val ForgeDarkColorScheme = darkColorScheme(
    primary = ForgePrimary,
    onPrimary = ForgeOnPrimary,
    primaryContainer = ForgePrimaryContainer,
    onPrimaryContainer = ForgeOnPrimaryContainer,
    secondary = ForgeSecondary,
    onSecondary = ForgeOnSecondary,
    secondaryContainer = ForgeSecondaryContainer,
    onSecondaryContainer = ForgeOnSecondaryContainer,
    tertiary = ForgeTertiary,
    onTertiary = ForgeOnTertiary,
    background = ForgeDarkBackground,
    onBackground = ForgeDarkOnBackground,
    surface = ForgeDarkSurface,
    onSurface = ForgeDarkOnSurface,
    surfaceVariant = ForgeDarkSurfaceVariant,
    onSurfaceVariant = ForgeDarkOnSurfaceVariant,
    surfaceContainerLowest = ForgeDarkBackground,
    surfaceContainerLow = ForgeDarkSurface,
    surfaceContainer = ForgeDarkSurfaceMuted,
    surfaceContainerHigh = ForgeDarkSurfaceVariant,
    surfaceContainerHighest = ForgeDarkSurfaceElevated,
    error = EmberDanger,
    onError = DarkOnError
)

private val RasterLightColorScheme = lightColorScheme(
    primary = RasterPrimary,
    onPrimary = RasterOnPrimary,
    primaryContainer = RasterPrimaryContainer,
    onPrimaryContainer = RasterOnPrimaryContainer,
    secondary = RasterSecondary,
    onSecondary = RasterOnSecondary,
    secondaryContainer = RasterSecondaryContainer,
    onSecondaryContainer = RasterOnSecondaryContainer,
    tertiary = RasterTertiary,
    onTertiary = RasterOnTertiary,
    background = RasterLightBackground,
    onBackground = OnBackground,
    surface = RasterLightSurface,
    onSurface = OnSurface,
    surfaceVariant = RasterLightSurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = RasterLightSurface,
    surfaceContainerLow = SurfaceElevated,
    surfaceContainer = SurfaceMuted,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = RasterLightSurfaceVariant,
    error = Error,
    onError = OnError
)

private val RasterDarkColorScheme = darkColorScheme(
    primary = RasterPrimary,
    onPrimary = RasterOnPrimary,
    primaryContainer = RasterPrimaryContainer,
    onPrimaryContainer = RasterOnPrimaryContainer,
    secondary = RasterSecondary,
    onSecondary = RasterOnSecondary,
    secondaryContainer = RasterSecondaryContainer,
    onSecondaryContainer = RasterOnSecondaryContainer,
    tertiary = RasterTertiary,
    onTertiary = RasterOnTertiary,
    background = RasterDarkBackground,
    onBackground = RasterDarkOnBackground,
    surface = RasterDarkSurface,
    onSurface = RasterDarkOnSurface,
    surfaceVariant = RasterDarkSurfaceVariant,
    onSurfaceVariant = RasterDarkOnSurfaceVariant,
    surfaceContainerLowest = RasterDarkBackground,
    surfaceContainerLow = RasterDarkSurface,
    surfaceContainer = RasterDarkSurfaceMuted,
    surfaceContainerHigh = RasterDarkSurfaceVariant,
    surfaceContainerHighest = RasterDarkSurfaceElevated,
    error = DarkError,
    onError = DarkOnError
)

private val TideLightColorScheme = lightColorScheme(
    primary = TidePrimary,
    onPrimary = TideOnPrimary,
    primaryContainer = TidePrimaryContainer,
    onPrimaryContainer = TideOnPrimaryContainer,
    secondary = TideSecondary,
    onSecondary = TideOnSecondary,
    secondaryContainer = TideSecondaryContainer,
    onSecondaryContainer = TideOnSecondaryContainer,
    tertiary = TideTertiary,
    onTertiary = TideOnTertiary,
    background = TideLightBackground,
    onBackground = OnBackground,
    surface = TideLightSurface,
    onSurface = OnSurface,
    surfaceVariant = TideLightSurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = TideLightSurface,
    surfaceContainerLow = SurfaceElevated,
    surfaceContainer = SurfaceMuted,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = TideLightSurfaceVariant,
    error = Error,
    onError = OnError
)

private val TideDarkColorScheme = darkColorScheme(
    primary = TidePrimary,
    onPrimary = TideOnPrimary,
    primaryContainer = TidePrimaryContainer,
    onPrimaryContainer = TideOnPrimaryContainer,
    secondary = TideSecondary,
    onSecondary = TideOnSecondary,
    secondaryContainer = TideSecondaryContainer,
    onSecondaryContainer = TideOnSecondaryContainer,
    tertiary = TideTertiary,
    onTertiary = TideOnTertiary,
    background = TideDarkBackground,
    onBackground = TideDarkOnBackground,
    surface = TideDarkSurface,
    onSurface = TideDarkOnSurface,
    surfaceVariant = TideDarkSurfaceVariant,
    onSurfaceVariant = TideDarkOnSurfaceVariant,
    surfaceContainerLowest = TideDarkBackground,
    surfaceContainerLow = TideDarkSurface,
    surfaceContainer = TideDarkSurfaceMuted,
    surfaceContainerHigh = TideDarkSurfaceVariant,
    surfaceContainerHighest = TideDarkSurfaceElevated,
    error = DarkError,
    onError = DarkOnError
)

private val PulseLightColorScheme = lightColorScheme(
    primary = PulsePrimary,
    onPrimary = PulseOnPrimary,
    primaryContainer = PulsePrimaryContainer,
    onPrimaryContainer = PulseOnPrimaryContainer,
    secondary = PulseSecondary,
    onSecondary = PulseOnSecondary,
    secondaryContainer = PulseSecondaryContainer,
    onSecondaryContainer = PulseOnSecondaryContainer,
    tertiary = PulseTertiary,
    onTertiary = PulseOnTertiary,
    background = PulseLightBackground,
    onBackground = OnBackground,
    surface = PulseLightSurface,
    onSurface = OnSurface,
    surfaceVariant = PulseLightSurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = PulseLightSurface,
    surfaceContainerLow = SurfaceElevated,
    surfaceContainer = SurfaceMuted,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = PulseLightSurfaceVariant,
    error = Error,
    onError = OnError
)

private val PulseDarkColorScheme = darkColorScheme(
    primary = PulsePrimary,
    onPrimary = PulseOnPrimary,
    primaryContainer = PulsePrimaryContainer,
    onPrimaryContainer = PulseOnPrimaryContainer,
    secondary = PulseSecondary,
    onSecondary = PulseOnSecondary,
    secondaryContainer = PulseSecondaryContainer,
    onSecondaryContainer = PulseOnSecondaryContainer,
    tertiary = PulseTertiary,
    onTertiary = PulseOnTertiary,
    background = PulseDarkBackground,
    onBackground = PulseDarkOnBackground,
    surface = PulseDarkSurface,
    onSurface = PulseDarkOnSurface,
    surfaceVariant = PulseDarkSurfaceVariant,
    onSurfaceVariant = PulseDarkOnSurfaceVariant,
    surfaceContainerLowest = PulseDarkBackground,
    surfaceContainerLow = PulseDarkSurface,
    surfaceContainer = PulseDarkSurfaceMuted,
    surfaceContainerHigh = PulseDarkSurfaceVariant,
    surfaceContainerHighest = PulseDarkSurfaceElevated,
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
            ThemeScheme.FORGE -> ForgeDarkColorScheme
            ThemeScheme.RASTER -> RasterDarkColorScheme
            ThemeScheme.TIDE -> TideDarkColorScheme
            ThemeScheme.PULSE -> PulseDarkColorScheme
        }
        else -> when (themeScheme) {
            ThemeScheme.AMBER -> AmberLightColorScheme
            ThemeScheme.DEEP_CYAN -> CyanLightColorScheme
            ThemeScheme.NEON_RED -> RedLightColorScheme
            ThemeScheme.FORGE -> ForgeLightColorScheme
            ThemeScheme.RASTER -> RasterLightColorScheme
            ThemeScheme.TIDE -> TideLightColorScheme
            ThemeScheme.PULSE -> PulseLightColorScheme
        }
    }

    val semanticColors = if (isDarkTheme) {
        when (themeScheme) {
            ThemeScheme.FORGE -> EmberSemanticColors(
                success = ForgeTertiary,
                danger = EmberDanger,
                warning = ForgePrimary,
                rose = ForgePrimary,
                roseLight = EmberRoseLight,
                sky = EmberSky,
                skyLight = EmberSkyLight,
                violet = EmberViolet,
                violetLight = EmberVioletLight,
                teal = EmberTeal,
                tealLight = EmberTealLight
            )
            ThemeScheme.RASTER -> EmberSemanticColors(
                success = RasterTertiary,
                danger = EmberDanger,
                warning = EmberWarning,
                rose = EmberRose,
                roseLight = EmberRoseLight,
                sky = RasterPrimary,
                skyLight = EmberSkyLight,
                violet = EmberViolet,
                violetLight = EmberVioletLight,
                teal = EmberTeal,
                tealLight = EmberTealLight
            )
            ThemeScheme.TIDE -> EmberSemanticColors(
                success = TidePrimary,
                danger = EmberDanger,
                warning = EmberWarning,
                rose = EmberRose,
                roseLight = EmberRoseLight,
                sky = TideSecondary,
                skyLight = EmberSkyLight,
                violet = TideTertiary,
                violetLight = EmberVioletLight,
                teal = TidePrimary,
                tealLight = EmberTealLight
            )
            ThemeScheme.PULSE -> EmberSemanticColors(
                success = EmberSuccess,
                danger = PulsePrimary,
                warning = EmberWarning,
                rose = PulsePrimary,
                roseLight = EmberRoseLight,
                sky = PulseTertiary,
                skyLight = EmberSkyLight,
                violet = PulseSecondary,
                violetLight = EmberVioletLight,
                teal = EmberTeal,
                tealLight = EmberTealLight
            )
            else -> EmberSemanticColors(
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
    } else {
        EmberSemanticColors(
            success = EmberSuccessDeep,
            danger = EmberDanger,
            warning = EmberWarningDeep,
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
            ThemeScheme.FORGE -> IronLogSurfaceRoles(
                elevated = ForgeDarkSurfaceElevated,
                muted = ForgeDarkSurfaceMuted,
                accentSuccess = ForgeTertiary,
                accentWarning = ForgePrimary
            )
            ThemeScheme.RASTER -> IronLogSurfaceRoles(
                elevated = RasterDarkSurfaceElevated,
                muted = RasterDarkSurfaceMuted,
                accentSuccess = RasterTertiary,
                accentWarning = RasterPrimary
            )
            ThemeScheme.TIDE -> IronLogSurfaceRoles(
                elevated = TideDarkSurfaceElevated,
                muted = TideDarkSurfaceMuted,
                accentSuccess = TidePrimary,
                accentWarning = TideSecondary
            )
            ThemeScheme.PULSE -> IronLogSurfaceRoles(
                elevated = PulseDarkSurfaceElevated,
                muted = PulseDarkSurfaceMuted,
                accentSuccess = EmberSuccess,
                accentWarning = PulsePrimary
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
            ThemeScheme.FORGE -> IronLogSurfaceRoles(
                elevated = SurfaceElevated,
                muted = SurfaceMuted,
                accentSuccess = AccentSuccess,
                accentWarning = ForgePrimary
            )
            ThemeScheme.RASTER -> IronLogSurfaceRoles(
                elevated = SurfaceElevated,
                muted = SurfaceMuted,
                accentSuccess = AccentSuccess,
                accentWarning = RasterPrimary
            )
            ThemeScheme.TIDE -> IronLogSurfaceRoles(
                elevated = SurfaceElevated,
                muted = SurfaceMuted,
                accentSuccess = AccentSuccess,
                accentWarning = TidePrimary
            )
            ThemeScheme.PULSE -> IronLogSurfaceRoles(
                elevated = SurfaceElevated,
                muted = SurfaceMuted,
                accentSuccess = AccentSuccess,
                accentWarning = PulsePrimary
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
