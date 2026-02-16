package com.ironlog.app.presentation.theme

import androidx.compose.runtime.Composable

val ironLogDimens: IronLogDimens
    @Composable get() = LocalIronLogDimens.current

val ironLogMotion: IronLogMotion
    @Composable get() = LocalIronLogMotion.current

val ironLogSurfaceRoles: IronLogSurfaceRoles
    @Composable get() = LocalIronLogSurfaceRoles.current
