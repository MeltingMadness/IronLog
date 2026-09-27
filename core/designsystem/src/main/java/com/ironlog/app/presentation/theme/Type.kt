package com.ironlog.app.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ironlog.core.designsystem.R

/**
 * Geist by Vercel (SIL Open Font License, `res/font/geist_*`), as static cuts so every
 * [FontWeight] maps to its own file. The scale follows the Material 3 defaults: body text
 * 14 sp and up, nothing below 11 sp.
 */
private val GeistFamily = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
    Font(R.font.geist_extrabold, FontWeight.ExtraBold),
    Font(R.font.geist_black, FontWeight.Black)
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Black,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-1.0).sp,
        fontFeatureSettings = "tnum"
    ),
    displayMedium = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Black,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.75).sp,
        fontFeatureSettings = "tnum"
    ),
    displaySmall = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "tnum"
    ),
    headlineLarge = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp,
        fontFeatureSettings = "tnum"
    ),
    headlineMedium = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    headlineSmall = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    titleLarge = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    titleMedium = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = "tnum"
    ),
    titleSmall = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        fontFeatureSettings = "tnum"
    ),
    bodyLarge = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontFeatureSettings = "tnum"
    ),
    bodyMedium = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum"
    ),
    bodySmall = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
        fontFeatureSettings = "tnum"
    ),
    labelLarge = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum"
    ),
    labelMedium = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum"
    ),
    labelSmall = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
        fontFeatureSettings = "tnum"
    )
)

val AthleticHero = TextStyle(
    fontFamily = GeistFamily,
    fontWeight = FontWeight.Black,
    fontSize = 34.sp,
    lineHeight = 38.sp,
    letterSpacing = (-0.75).sp,
    fontFeatureSettings = "tnum"
)

val AthleticNumber = TextStyle(
    fontFamily = GeistFamily,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 22.sp,
    lineHeight = 26.sp,
    fontFeatureSettings = "tnum"
)

/** Small caps-style label above values; 12 sp with a little tracking so it stays legible on glass. */
val AthleticLabel = TextStyle(
    fontFamily = GeistFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.6.sp
)
