package com.ironlog.app.presentation.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.ironLogSharedElement(
    key: Any,
    boundsTransform: androidx.compose.animation.BoundsTransform = { _, _ ->
        androidx.compose.animation.core.spring(
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy
        )
    }
): Modifier {
    val sharedScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    return if (sharedScope == null || animatedVisibilityScope == null) {
        this
    } else {
        with(sharedScope) {
            sharedElement(
                sharedContentState = rememberSharedContentState(key = key),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = boundsTransform
            )
        }
    }
}
