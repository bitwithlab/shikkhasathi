package com.shikkhasathi.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme

/** Card: radius 16, 1px border, elevation-1 soft shadow in light mode, border only in dark (DESIGN_SYSTEM.md). */
@Composable
fun SsCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    container: Color = SsTheme.palette.surface,
    contentPadding: PaddingValues = PaddingValues(SsSpace.s4),
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = SsTheme.palette
    Surface(
        modifier = modifier,
        shape = SsShape.card,
        color = container,
        contentColor = palette.text,
        border = BorderStroke(1.dp, palette.border),
        shadowElevation = if (palette.isDark) 0.dp else 1.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                    .padding(contentPadding),
            content = content,
        )
    }
}
