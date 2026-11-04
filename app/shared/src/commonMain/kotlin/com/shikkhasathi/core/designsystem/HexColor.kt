package com.shikkhasathi.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Course colours arrive from the API as `#RRGGBB` (Classroom course colour / seller cover colour).
 * Anything unparseable falls back to [fallback] (usually the primary token) instead of crashing a list.
 */
fun hexColor(
    hex: String?,
    fallback: Color,
): Color {
    val digits = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return fallback
    val rgb = digits.toLongOrNull(16) ?: return fallback
    return Color(0xFF000000 or rgb)
}
