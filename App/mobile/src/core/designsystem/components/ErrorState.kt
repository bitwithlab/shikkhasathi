package com.shikkhasathi.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str

/** Sympathetic Bangla error with a retry, inside a danger-tinted card. Announced politely to TalkBack/VoiceOver. */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    title: String = Str.ErrorTitle.t(),
    onRetry: (() -> Unit)? = null,
) {
    val palette = SsTheme.palette
    SsCard(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        container = palette.dangerSoft,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(SsSpace.s3), verticalAlignment = Alignment.Top) {
            Icon(Lucide.CircleAlert, contentDescription = null, tint = palette.dangerFg, modifier = Modifier.size(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(SsSpace.s1), modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = palette.dangerFg)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = palette.text)
                if (onRetry != null) {
                    SsButton(
                        text = Str.CommonRetry.t(),
                        onClick = onRetry,
                        variant = SsButtonVariant.Ghost,
                        leadingIcon = Lucide.RefreshCw,
                    )
                }
            }
        }
    }
}
