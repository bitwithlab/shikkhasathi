package com.shikkhasathi.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme

/**
 * Empty state: icon tile + specific Bangla title/body + the one next action.
 * Start-aligned (no centred paragraphs, CLAUDE.md §7).
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val palette = SsTheme.palette
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = SsSpace.s6),
        verticalArrangement = Arrangement.spacedBy(SsSpace.s3),
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(palette.primarySoft, SsShape.card),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = palette.primaryFg, modifier = Modifier.size(28.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = palette.text,
            modifier = Modifier.padding(top = SsSpace.s1).semantics { heading() },
        )
        Text(body, style = MaterialTheme.typography.bodyMedium, color = palette.muted)
        if (actionLabel != null && onAction != null) {
            SsButton(text = actionLabel, onClick = onAction, modifier = Modifier.padding(top = SsSpace.s2))
        }
    }
}
