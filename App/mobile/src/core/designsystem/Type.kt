package com.shikkhasathi.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Font families. Hind Siliguri (Bangla) + Inter (Latin, tabular numerals) per docs/DESIGN_SYSTEM.md.
 *
 * Fallback in effect: the font files are not in the repo yet (see App/mobile/assets/fonts/README.md), so both
 * families resolve to the platform default — Noto Sans Bengali on Android, Bangla Sangam MN / system on iOS —
 * which renders conjuncts (যুক্তাক্ষর) correctly. Once the TTFs are added as Compose resources, only these two
 * values change (`FontFamily(Font(Res.font.hind_siliguri_regular, FontWeight.Normal), …)`); every style below
 * already uses them.
 */
object SsFonts {
    val bangla: FontFamily = FontFamily.Default
    val latin: FontFamily = FontFamily.Default
}

/** Room above and below every line so Bangla ascenders/descenders and যুক্তাক্ষর are never clipped. */
private val NoTrim = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)

private fun style(
    size: TextUnit,
    weight: FontWeight,
    lineHeightFactor: Float,
    family: FontFamily = SsFonts.bangla,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size,
    lineHeight = (size.value * lineHeightFactor).sp,
    lineHeightStyle = NoTrim,
)

private const val HEADING_LH = 1.35f

/** Type scale 12/14/16/18/20/24/30/36 (tokens), body line-height 1.7 for Bangla. */
@Composable
internal fun ssTypography(): Typography =
    Typography(
        displaySmall = style(SsFontSize.x4xl, FontWeight.Bold, 1.25f),
        headlineLarge = style(SsFontSize.x3xl, FontWeight.Bold, HEADING_LH),
        headlineMedium = style(SsFontSize.x2xl, FontWeight.SemiBold, HEADING_LH),
        headlineSmall = style(SsFontSize.xl, FontWeight.SemiBold, HEADING_LH),
        titleLarge = style(SsFontSize.xl, FontWeight.SemiBold, HEADING_LH),
        titleMedium = style(SsFontSize.lg, FontWeight.SemiBold, 1.45f),
        titleSmall = style(SsFontSize.base, FontWeight.Medium, 1.5f),
        bodyLarge = style(SsFontSize.base, FontWeight.Normal, SS_BANGLA_LINE_HEIGHT),
        bodyMedium = style(SsFontSize.sm, FontWeight.Normal, SS_BANGLA_LINE_HEIGHT),
        bodySmall = style(SsFontSize.xs, FontWeight.Normal, SS_BANGLA_LINE_HEIGHT),
        labelLarge = style(SsFontSize.sm, FontWeight.SemiBold, 1.5f),
        labelMedium = style(SsFontSize.xs, FontWeight.Medium, 1.5f),
        labelSmall = style(SsFontSize.xs, FontWeight.Medium, 1.4f),
    )

/** Prices (৳২৯৯), course codes (CSE-2203), timers: Inter with tabular figures so digits don't jump. */
val TextStyle.numeric: TextStyle
    get() = copy(fontFamily = SsFonts.latin, fontFeatureSettings = "tnum")
