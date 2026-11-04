package com.shikkhasathi.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shikkhasathi.core.designsystem.SsPalette
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.model.AssignmentStatus
import com.shikkhasathi.core.model.StatusTone
import com.shikkhasathi.core.model.chipSpec

@Immutable
data class ToneColors(val container: Color, val content: Color)

/** Tone → token colours. Soft tint background + `*-fg` text keeps every chip at WCAG AA. */
fun SsPalette.colorsFor(tone: StatusTone): ToneColors =
    when (tone) {
        StatusTone.Primary -> ToneColors(primarySoft, primaryFg)
        StatusTone.Success -> ToneColors(successSoft, successFg)
        StatusTone.Warning -> ToneColors(warningSoft, warningFg)
        StatusTone.Danger -> ToneColors(dangerSoft, dangerFg)
        StatusTone.Neutral -> ToneColors(surface2, muted)
    }

/** Pill chip (radius full) for academic info, filters and badges. */
@Composable
fun SsChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.Neutral,
    icon: ImageVector? = null,
) {
    val colors = SsTheme.palette.colorsFor(tone)
    Row(
        modifier =
            modifier
                .background(colors.container, SsShape.chip)
                .padding(horizontal = SsSpace.s3, vertical = SsSpace.s1),
        horizontalArrangement = Arrangement.spacedBy(SsSpace.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = colors.content, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = colors.content, maxLines = 1)
    }
}

/** Classroom submission status chip: Pending · Turned in · Graded · Missing (mapping in `core/model/AssignmentStatus`). */
@Composable
fun StatusChip(
    status: AssignmentStatus,
    modifier: Modifier = Modifier,
) {
    val spec = status.chipSpec()
    val label = spec.label.t()
    SsChip(text = label, tone = spec.tone, modifier = modifier.semantics { contentDescription = label })
}
