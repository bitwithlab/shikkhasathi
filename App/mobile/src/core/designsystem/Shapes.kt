package com.shikkhasathi.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

/** Radius tokens: card 16 · control (buttons, inputs, thumbnails) 12 · chip full. */
object SsShape {
    val card = RoundedCornerShape(SsRadius.card)
    val control = RoundedCornerShape(SsRadius.control)
    val small = RoundedCornerShape(SsRadius.sm)
    val chip = RoundedCornerShape(percent = 50)
}

internal val SsShapes =
    Shapes(
        extraSmall = SsShape.small,
        small = SsShape.control,
        medium = SsShape.control,
        large = SsShape.card,
        extraLarge = SsShape.card,
    )
