package com.shikkhasathi.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str

/** Screen title row; shows a back arrow for drawer destinations (they sit on top of a tab). */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = SsSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SsSpace.s1),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Lucide.ArrowLeft, contentDescription = Str.CommonBack.t(), tint = SsTheme.palette.text)
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = SsTheme.palette.text,
            modifier = Modifier.padding(start = if (onBack == null) SsSpace.s2 else 0.dp).semantics { heading() },
        )
    }
}
