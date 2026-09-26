package com.ironlog.app.presentation.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.ironlog.app.domain.model.AppearanceStyle
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Glass strength of a Liquid Glass surface.
 *
 * Only [STRONG] blurs the backdrop (navigation, hero cards, docks). [STANDARD] and [TINT] stay
 * translucent without blur so long lists scroll smoothly; the soft background blobs keep them
 * looking like glass anyway.
 */
enum class GlassLevel { STANDARD, STRONG, TINT }

/** Selected overall look, provided by [IronLogTheme]. */
val LocalAppearanceStyle = staticCompositionLocalOf { AppearanceStyle.EMBER }

/** Backdrop that [GlassLevel.STRONG] surfaces blur; `null` outside a [LiquidGlassHost]. */
internal val LocalGlassHazeState = staticCompositionLocalOf<HazeState?> { null }

private val GlassTeal = Color(0xFF2EC4B6)
private val GlassViolet = Color(0xFF6D5BFF)
private val GlassDarkBase = Color(0xFF07080C)
private val GlassLightBase = Color(0xFFEEF0F6)

@Composable
private fun isDarkGlass(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * Hosts the Liquid Glass backdrop: draws [LiquidBackground] once behind [content] and exposes it
 * as blur source. Real blur needs Android 12; below that, and with reduced motion, Haze falls
 * back to the tinted surfaces defined in [liquidGlass].
 */
@Composable
fun LiquidGlassHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val reduced = LocalIronLogMotion.current.reduced
    val hazeState = rememberHazeState(blurEnabled = !reduced && HazeDefaults.blurEnabled())
    Box(modifier) {
        LiquidBackground(Modifier.matchParentSize().hazeSource(hazeState))
        CompositionLocalProvider(LocalGlassHazeState provides hazeState) {
            content()
        }
    }
}

/**
 * Colored backdrop of the Liquid Glass look. The blobs are radial gradients instead of blurred
 * shapes: they look the same and cost nothing on older devices. The main blob follows the
 * active color scheme.
 */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val dark = isDarkGlass()
    val base = if (dark) GlassDarkBase else GlassLightBase
    val strength = if (dark) 1f else 0.55f
    Canvas(modifier) {
        drawRect(base)
        fun blob(center: Offset, radius: Float, color: Color, alpha: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to color.copy(alpha = alpha * strength),
                    0.55f to color.copy(alpha = alpha * strength * 0.45f),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
        val w = size.width
        val h = size.height
        blob(Offset(w * 0.25f, h * 0.06f), w * 1.0f, accent, 0.85f)
        blob(Offset(w * 0.98f, h * 0.48f), w * 0.9f, GlassTeal, 0.5f)
        blob(Offset(w * 0.12f, h * 0.96f), w * 0.95f, GlassViolet, 0.5f)
        // Soft veil at the bottom keeps text on lower surfaces readable.
        drawRect(
            Brush.verticalGradient(
                0.55f to Color.Transparent,
                1f to base.copy(alpha = 0.6f)
            )
        )
    }
}

/**
 * Liquid Glass surface: translucent tint, sheen from the top left, bright top edge and a dark
 * bottom edge for thickness. [GlassLevel.STRONG] additionally blurs the backdrop of the nearest
 * [LiquidGlassHost]; without a host every level falls back to a nearly opaque tint.
 */
fun Modifier.liquidGlass(
    level: GlassLevel = GlassLevel.STANDARD,
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color? = null
): Modifier = composed {
    val dark = isDarkGlass()
    val hazeState = LocalGlassHazeState.current
    val tintColor = tint ?: MaterialTheme.colorScheme.primary
    val translucent = when (level) {
        GlassLevel.STANDARD -> if (dark) Color(0x4710121A) else Color(0x59FFFFFF)
        GlassLevel.STRONG -> if (dark) Color(0x2E10121A) else Color(0x80FFFFFF)
        GlassLevel.TINT -> tintColor.copy(alpha = if (dark) 0.30f else 0.22f)
    }
    val opaque = when (level) {
        GlassLevel.STANDARD -> if (dark) Color(0xF01A1C26) else Color(0xEBFFFFFF)
        GlassLevel.STRONG -> if (dark) Color(0xF5222430) else Color(0xF5FFFFFF)
        GlassLevel.TINT -> tintColor.copy(alpha = if (dark) 0.45f else 0.30f)
            .compositeOverOpaque(if (dark) Color(0xFF1A1C26) else Color.White)
    }
    val sheen = if (dark) {
        listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.03f))
    } else {
        listOf(Color.White.copy(alpha = 0.70f), Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.20f))
    }
    val edge = if (dark) {
        listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.30f))
    } else {
        listOf(Color.White, Color.White.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.06f))
    }

    val surface = when {
        hazeState == null -> Modifier.background(opaque)
        level == GlassLevel.STRONG -> Modifier.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = if (dark) GlassDarkBase else GlassLightBase,
                tint = HazeTint(translucent),
                blurRadius = 32.dp,
                noiseFactor = 0f,
                fallbackTint = HazeTint(opaque)
            )
        )
        else -> Modifier.background(translucent)
    }

    this
        .clip(shape)
        .then(surface)
        .background(Brush.linearGradient(sheen, start = Offset.Zero, end = Offset.Infinite))
        .border(1.dp, Brush.verticalGradient(edge), shape)
}

private fun Color.compositeOverOpaque(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1 - a),
        green = green * a + background.green * (1 - a),
        blue = blue * a + background.blue * (1 - a),
        alpha = 1f
    )
}
