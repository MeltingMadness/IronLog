package com.ironlog.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

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
