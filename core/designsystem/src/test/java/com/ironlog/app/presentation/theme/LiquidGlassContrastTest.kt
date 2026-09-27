package com.ironlog.app.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.ironlog.app.domain.model.ThemeScheme
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

private data class Rgb(val red: Double, val green: Double, val blue: Double)
private data class Rgba(val rgb: Rgb, val alpha: Double)
private data class BackdropSample(val x: Double, val y: Double, val color: Rgb)

/** WCAG 2.x: sRGB-Kanäle linearisieren, dann mit den normierten Gewichten summieren. */
private fun relativeLuminance(color: Rgb): Double {
    fun linear(channel: Double): Double =
        if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

    return 0.2126 * linear(color.red) +
        0.7152 * linear(color.green) +
        0.0722 * linear(color.blue)
}

private fun contrastRatio(first: Rgb, second: Rgb): Double {
    val firstLuminance = relativeLuminance(first)
    val secondLuminance = relativeLuminance(second)
    return (maxOf(firstLuminance, secondLuminance) + 0.05) /
        (minOf(firstLuminance, secondLuminance) + 0.05)
}

/** Alpha-Compositing in sRGB, wie die nicht weichgezeichneten Canvas-Flächen. */
private fun compositeOver(foreground: Rgba, background: Rgb): Rgb {
    val alpha = foreground.alpha
    return Rgb(
        foreground.rgb.red * alpha + background.red * (1 - alpha),
        foreground.rgb.green * alpha + background.green * (1 - alpha),
        foreground.rgb.blue * alpha + background.blue * (1 - alpha)
    )
}

private fun Color.asRgb() = Rgb(red.toDouble(), green.toDouble(), blue.toDouble())

private data class ContrastCheck(
    val scheme: ThemeScheme,
    val mode: String,
    val glassLevel: String,
    val pair: String,
    val ratio: Double,
    val threshold: Double,
    val worstAt: BackdropSample? = null
)

class LiquidGlassContrastTest {
    companion object {
        private const val NORMAL_TEXT = 4.5
        private const val LARGE_TEXT_OR_CONTROL = 3.0

        /*
         * Modell für LiquidBackground: ein 1:2-Viewport (Breite:Höhe), je Achse 0..100
         * inklusive aller drei Blob-Zentren. Die drei Kreise werden in Zeichenreihenfolge
         * über der Basis komponiert; die Stops 0/0,55/1 ergeben linear alpha*(1-r/R).
         * Danach folgt der vertikale Basisschleier ab 55 % Höhe. Für jede Farbpaarung
         * und Glasstufe zählt die Hintergrundstelle mit dem kleinsten Kontrast.
         *
         * Gemessen wird die Glastönung bei aktivem LiquidGlassHost mit allen drei
         * Stufen. Bei STRONG wird der unverwischte Hintergrund als konservative
         * Näherung verwendet. Der hellste Glanzpunkt (8 % Weiß) wird auf dunklem
         * Glas konservativ an jeder Stelle aufgetragen. Fallback und Rand sind ausgenommen.
         * withLiquidGlassContainers ändert keine der hier gemessenen Farbrollen.
         */
        private const val SAMPLE_STEPS = 100
        private val DARK_BASE = Color(0xFF07080C).asRgb()
        private val LIGHT_BASE = Color(0xFFEEF0F6).asRgb()
        private val TEAL = Color(0xFF2EC4B6).asRgb()
        private val VIOLET = Color(0xFF6D5BFF).asRgb()

        private val fieldPrefix = mapOf(
            ThemeScheme.AMBER to "Amber",
            ThemeScheme.DEEP_CYAN to "Cyan",
            ThemeScheme.NEON_RED to "Red",
            ThemeScheme.FORGE to "Forge",
            ThemeScheme.RASTER to "Raster",
            ThemeScheme.TIDE to "Tide",
            ThemeScheme.PULSE to "Pulse"
        )
    }

    @Test
    fun kontrasttabelleOhneFehlschlag() {
        println("Schema | Modus | Glasstufe | Rolle/Farbpaar | Kontrastverhältnis | Status")
        checks().forEach { check ->
            val status = if (check.ratio >= check.threshold) "ok" else "unter Vorgabe"
            val ratio = String.format(Locale.ROOT, "%.3f:1", check.ratio)
            println("${check.scheme} | ${check.mode} | ${check.glassLevel} | " +
                "${check.pair} | $ratio | $status")
        }
    }

    @Test
    fun alleKontrasteErfuellenDieWcagSchwellen() {
        checks().forEach { check ->
            assertTrue(
                "${check.scheme} ${check.mode} ${check.glassLevel} ${check.pair}: " +
                    "${check.ratio}:1 < ${check.threshold}:1" +
                    (check.worstAt?.let { " bei x=${it.x}, y=${it.y}" } ?: ""),
                check.ratio >= check.threshold
            )
        }
    }

    private fun checks(): List<ContrastCheck> = buildList {
        for (theme in ThemeScheme.entries) {
            for (dark in listOf(false, true)) {
                val mode = if (dark) "Dunkel" else "Hell"
                val colors = colorScheme(theme, dark)
                val primary = colors.primary.asRgb()
                val backdrop = backdropSamples(primary, dark)
                val semanticText = semanticTextFor(dark)
                val rpeCases = rpeTextAndFill(theme, dark, semanticText)
                val textColors = listOf(
                    "onSurface / Glas [Text 4,5]" to colors.onSurface.asRgb(),
                    "onSurfaceVariant / Glas [Text 4,5]" to colors.onSurfaceVariant.asRgb(),
                    "accentText / Glas [Text 4,5]" to accentTextFor(theme, dark).asRgb(),
                    "successText / Glas [Text 4,5]" to semanticText.success.asRgb(),
                    "dangerText / Glas [Text 4,5]" to semanticText.danger.asRgb(),
                    "warningText / Glas [Text 4,5]" to semanticText.warning.asRgb(),
                    "roseText / Glas [Text 4,5]" to semanticText.rose.asRgb(),
                    "skyText / Glas [Text 4,5]" to semanticText.sky.asRgb(),
                    "violetText / Glas [Text 4,5]" to semanticText.violet.asRgb(),
                    "tealText / Glas [Text 4,5]" to semanticText.teal.asRgb()
                )

                for (level in GlassLevel.entries) {
                    val tint = glassSurfaceTint(level, dark, colors.primary)
                    val glass = Rgba(tint.asRgb(), tint.alpha.toDouble())
                    for ((pair, text) in textColors) {
                        var worst = backdrop.first()
                        var worstRatio = Double.POSITIVE_INFINITY
                        for (sample in backdrop) {
                            val surface = compositeOver(glass, sample.color)
                            val withSheen = if (dark) {
                                compositeOver(Rgba(Rgb(1.0, 1.0, 1.0), 0.08), surface)
                            } else {
                                surface
                            }
                            val ratio = contrastRatio(text, withSheen)
                            if (ratio < worstRatio) {
                                worst = sample
                                worstRatio = ratio
                            }
                        }
                        add(ContrastCheck(theme, mode, level.name, pair, worstRatio, NORMAL_TEXT,
                            worstAt = worst))
                    }
                    for ((name, ink, fill) in rpeCases) {
                        val selectedSurface = compositeOver(
                            Rgba(fill.asRgb(), fill.alpha.toDouble()), colors.surface.asRgb()
                        )
                        val selectedInk = if (relativeLuminance(selectedSurface) > 0.179) {
                            Color.Black.asRgb()
                        } else {
                            Color.White.asRgb()
                        }
                        var worstOverlay = Double.POSITIVE_INFINITY
                        var worstSelected = Double.POSITIVE_INFINITY
                        for (sample in backdrop) {
                            val surface = compositeOver(glass, sample.color)
                            val withSheen = if (dark) {
                                compositeOver(Rgba(Rgb(1.0, 1.0, 1.0), 0.08), surface)
                            } else {
                                surface
                            }
                            worstOverlay = minOf(worstOverlay, contrastRatio(
                                ink.asRgb(), compositeOver(Rgba(fill.asRgb(), 0.15), withSheen)
                            ))
                            worstSelected = minOf(worstSelected, contrastRatio(
                                selectedInk,
                                compositeOver(Rgba(fill.asRgb(), fill.alpha.toDouble()), withSheen)
                            ))
                        }
                        add(ContrastCheck(theme, mode, level.name, "$name / RPE-Fläche 15 % [Text 4,5]",
                            worstOverlay, NORMAL_TEXT))
                        add(ContrastCheck(theme, mode, level.name, "$name / RPE-Auswahl [Text 4,5]",
                            worstSelected, NORMAL_TEXT))
                    }
                }

                val onPrimaryRatio = contrastRatio(colors.onPrimary.asRgb(), primary)
                add(ContrastCheck(theme, mode, "–", "onPrimary / primary [Text 4,5]",
                    onPrimaryRatio, NORMAL_TEXT))
                add(ContrastCheck(theme, mode, "–", "onPrimary / primary [groß/Bedienelement 3,0]",
                    onPrimaryRatio, LARGE_TEXT_OR_CONTROL))
                add(ContrastCheck(theme, mode, "–", "FilterChip selected label / container [Text 4,5]",
                    contrastRatio(colors.onSecondaryContainer.asRgb(), colors.secondaryContainer.asRgb()),
                    NORMAL_TEXT))
            }
        }
    }

    private fun rpeTextAndFill(
        theme: ThemeScheme,
        dark: Boolean,
        ink: SemanticTextColors
    ): List<Triple<String, Color, Color>> {
        val success = if (!dark) EmberSuccessDeep else when (theme) {
            ThemeScheme.FORGE -> ForgeTertiary
            ThemeScheme.RASTER -> RasterTertiary
            ThemeScheme.TIDE -> TidePrimary
            else -> EmberSuccess
        }
        val warning = if (dark && theme == ThemeScheme.FORGE) ForgePrimary
            else if (dark) EmberWarning else EmberWarningDeep
        val rose = when {
            dark && theme == ThemeScheme.FORGE -> ForgePrimary
            dark && theme == ThemeScheme.PULSE -> PulsePrimary
            else -> EmberRose
        }
        val danger = if (dark && theme == ThemeScheme.PULSE) PulsePrimary else EmberDanger
        return listOf(
            Triple("RPE 7", ink.success, success),
            Triple("RPE 8", ink.warning, warning),
            Triple("RPE 9", ink.rose, rose.copy(alpha = 0.85f)),
            Triple("RPE 10", ink.danger, danger)
        )
    }

    private fun colorScheme(theme: ThemeScheme, dark: Boolean): ColorScheme {
        // Die Produktions-Schemes und die private Container-Anpassung direkt verwenden,
        // ohne die Farbbelegung im Test ein zweites Mal nachzubauen.
        val themeClass = Class.forName("com.ironlog.app.presentation.theme.ThemeKt")
        val fieldName = fieldPrefix.getValue(theme) + if (dark) "DarkColorScheme" else "LightColorScheme"
        val field = themeClass.getDeclaredField(fieldName).apply { isAccessible = true }
        val scheme = field.get(null) as ColorScheme
        val withContainers = themeClass.getDeclaredMethod(
            "withLiquidGlassContainers", ColorScheme::class.java, Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }
        return withContainers.invoke(null, scheme, dark) as ColorScheme
    }

    private fun backdropSamples(primary: Rgb, dark: Boolean): List<BackdropSample> = buildList {
        for (yi in 0..SAMPLE_STEPS) {
            for (xi in 0..SAMPLE_STEPS) {
                val x = xi.toDouble() / SAMPLE_STEPS
                val y = yi.toDouble() / SAMPLE_STEPS
                add(BackdropSample(x, y, backdropAt(x, y, primary, dark)))
            }
        }
    }

    private fun backdropAt(x: Double, y: Double, primary: Rgb, dark: Boolean): Rgb {
        val base = if (dark) DARK_BASE else LIGHT_BASE
        val strength = if (dark) 1.0 else 0.55
        var background = base

        fun blob(cx: Double, cy: Double, radius: Double, color: Rgb, peakAlpha: Double) {
            // y ist auf die Viewport-Höhe normiert; diese beträgt zwei Viewport-Breiten.
            val fraction = hypot(x - cx, 2 * (y - cy)) / radius
            if (fraction < 1.0) {
                background = compositeOver(
                    Rgba(color, peakAlpha * strength * (1 - fraction)), background
                )
            }
        }

        blob(0.25, 0.06, 1.0, primary, 0.85)
        blob(0.98, 0.48, 0.9, TEAL, 0.5)
        blob(0.12, 0.96, 0.95, VIOLET, 0.5)
        if (y > 0.55) {
            val veilAlpha = 0.6 * ((y - 0.55) / 0.45)
            background = compositeOver(Rgba(base, veilAlpha), background)
        }
        return background
    }
}
