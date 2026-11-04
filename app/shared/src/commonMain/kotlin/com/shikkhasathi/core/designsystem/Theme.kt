package com.shikkhasathi.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Full token palette (Tokens.kt is generated from website/src/styles/tokens/tokens.json — never edit it by hand).
 * Material's ColorScheme only has room for part of it; components read the rest from [SsTheme.palette].
 */
@Immutable
data class SsPalette(
    val isDark: Boolean,
    val primary: Color,
    val primaryStrong: Color,
    val primarySoft: Color,
    val primaryFg: Color,
    val onPrimary: Color,
    val violet: Color,
    val success: Color,
    val successSoft: Color,
    val successFg: Color,
    val warning: Color,
    val warningSoft: Color,
    val warningFg: Color,
    val danger: Color,
    val dangerSoft: Color,
    val dangerFg: Color,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val borderStrong: Color,
)

val SsLightPalette =
    with(SsLightColors) {
        SsPalette(
            isDark = false,
            primary = primary,
            primaryStrong = primaryStrong,
            primarySoft = primarySoft,
            primaryFg = primaryFg,
            onPrimary = onPrimary,
            violet = violet,
            success = success,
            successSoft = successSoft,
            successFg = successFg,
            warning = warning,
            warningSoft = warningSoft,
            warningFg = warningFg,
            danger = danger,
            dangerSoft = dangerSoft,
            dangerFg = dangerFg,
            bg = bg,
            surface = surface,
            surface2 = surface2,
            text = text,
            muted = muted,
            border = border,
            borderStrong = borderStrong,
        )
    }

val SsDarkPalette =
    with(SsDarkColors) {
        SsPalette(
            isDark = true,
            primary = primary,
            primaryStrong = primaryStrong,
            primarySoft = primarySoft,
            primaryFg = primaryFg,
            onPrimary = onPrimary,
            violet = violet,
            success = success,
            successSoft = successSoft,
            successFg = successFg,
            warning = warning,
            warningSoft = warningSoft,
            warningFg = warningFg,
            danger = danger,
            dangerSoft = dangerSoft,
            dangerFg = dangerFg,
            bg = bg,
            surface = surface,
            surface2 = surface2,
            text = text,
            muted = muted,
            border = border,
            borderStrong = borderStrong,
        )
    }

/**
 * Every Material role is assigned explicitly, so no default M3 purple or tonal tint can leak in.
 * Text-bearing roles use the `*-fg` tokens in light mode so emerald/amber/red copy passes WCAG AA.
 */
@Suppress("LongMethod") // one named argument per Material role, on purpose
internal fun SsPalette.toColorScheme(): ColorScheme =
    if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primarySoft,
            onPrimaryContainer = primaryFg,
            inversePrimary = primaryStrong,
            secondary = success,
            onSecondary = onPrimary,
            secondaryContainer = successSoft,
            onSecondaryContainer = successFg,
            tertiary = warning,
            onTertiary = onPrimary,
            tertiaryContainer = warningSoft,
            onTertiaryContainer = warningFg,
            background = bg,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surface2,
            onSurfaceVariant = muted,
            surfaceTint = surface,
            inverseSurface = text,
            inverseOnSurface = bg,
            error = danger,
            onError = onPrimary,
            errorContainer = dangerSoft,
            onErrorContainer = dangerFg,
            outline = borderStrong,
            outlineVariant = border,
            scrim = Color.Black.copy(alpha = 0.5f),
            surfaceBright = surface2,
            surfaceDim = bg,
            surfaceContainerLowest = bg,
            surfaceContainerLow = surface,
            surfaceContainer = surface,
            surfaceContainerHigh = surface2,
            surfaceContainerHighest = surface2,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primarySoft,
            onPrimaryContainer = primaryFg,
            inversePrimary = primaryStrong,
            secondary = successFg,
            onSecondary = onPrimary,
            secondaryContainer = successSoft,
            onSecondaryContainer = successFg,
            tertiary = warningFg,
            onTertiary = onPrimary,
            tertiaryContainer = warningSoft,
            onTertiaryContainer = warningFg,
            background = bg,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surface2,
            onSurfaceVariant = muted,
            // Same as surface: tonal elevation never tints cards purple.
            surfaceTint = surface,
            inverseSurface = text,
            inverseOnSurface = bg,
            error = dangerFg,
            onError = onPrimary,
            errorContainer = dangerSoft,
            onErrorContainer = dangerFg,
            outline = borderStrong,
            outlineVariant = border,
            scrim = Color.Black.copy(alpha = 0.4f),
            surfaceBright = surface,
            surfaceDim = bg,
            surfaceContainerLowest = surface,
            surfaceContainerLow = surface,
            surfaceContainer = surface,
            surfaceContainerHigh = surface2,
            surfaceContainerHighest = surface2,
        )
    }

val LocalSsPalette = staticCompositionLocalOf { SsLightPalette }

@Composable
fun SsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) SsDarkPalette else SsLightPalette
    CompositionLocalProvider(LocalSsPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = ssTypography(),
            shapes = SsShapes,
            content = content,
        )
    }
}

object SsTheme {
    val palette: SsPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalSsPalette.current
}
