package com.ironlog.app.presentation.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/**
 * Spring-physics press feedback: scales down on press, springs back on release.
 * Falls back to plain clickable when reduced motion is on.
 */
fun Modifier.pressScale(
    targetScale: Float = 0.96f,
    onClick: () -> Unit
): Modifier = composed {
    val motion = ironLogMotion
    if (motion.reduced) {
        return@composed this.pointerInput(onClick) {
            detectTapGestures(onTap = { onClick() })
        }
    }

    val scale = remember { Animatable(1f) }

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(onClick) {
            detectTapGestures(
                onPress = {
                    scale.animateTo(targetScale, spring(dampingRatio = 0.65f, stiffness = 600f))
                    val released = tryAwaitRelease()
                    scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                    if (released) {
                        onClick()
                    }
                }
            )
        }
}

/**
 * Staggered entrance animation for list items.
 * Items slide up from offsetStartPx and fade in, with a per-item delay.
 * Falls back to a no-op when reduced motion is on.
 */
fun Modifier.staggeredEntrance(
    index: Int,
    delayPerItemMs: Long = 40L,
    offsetStartPx: Int = 40
): Modifier = composed {
    val motion = ironLogMotion
    if (motion.reduced) return@composed this

    val translationY = remember { Animatable(offsetStartPx.toFloat()) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val delayMs = index * delayPerItemMs
        kotlinx.coroutines.delay(delayMs)
        launch { translationY.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 300f)) }
        launch { alpha.animateTo(1f, tween(180)) }
    }

    this.graphicsLayer {
        this.translationY = translationY.value
        this.alpha = alpha.value
    }
}
