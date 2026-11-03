package com.shikkhasathi.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str

enum class SsButtonVariant { Primary, Secondary, Ghost, Danger }

private val MinHeight = 48.dp
private const val PRESSED_SCALE = 0.98f

/**
 * Button with the shared anatomy (DESIGN_SYSTEM.md): min 48dp tall, radius 12, pressed scale .98,
 * and a loading state that keeps the label (screen readers hear the "অপেক্ষা করুন" state) instead of a spinner swap.
 */
@Composable
fun SsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SsButtonVariant = SsButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val palette = SsTheme.palette
    val waitLabel = Str.CommonPleaseWait.t()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scaled =
        modifier
            .defaultMinSize(minHeight = MinHeight)
            .graphicsLayer {
                val s = if (pressed) PRESSED_SCALE else 1f
                scaleX = s
                scaleY = s
            }.semantics { if (loading) stateDescription = waitLabel }
    val padding = PaddingValues(horizontal = SsSpace.s5, vertical = SsSpace.s3)
    val isEnabled = enabled && !loading
    val content: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                SkeletonBox(Modifier.size(width = 16.dp, height = 16.dp), shape = SsShape.chip)
                Spacer(Modifier.width(SsSpace.s2))
            } else if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(SsSpace.s2))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }

    when (variant) {
        SsButtonVariant.Primary, SsButtonVariant.Danger -> {
            val container = if (variant == SsButtonVariant.Danger) palette.dangerFg else palette.primary
            Button(
                onClick = onClick,
                modifier = scaled,
                enabled = isEnabled,
                shape = SsShape.control,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = container,
                        contentColor = palette.onPrimary,
                        disabledContainerColor = palette.surface2,
                        disabledContentColor = palette.muted,
                    ),
                elevation = null,
                contentPadding = padding,
                interactionSource = interaction,
            ) { content() }
        }
        SsButtonVariant.Secondary ->
            OutlinedButton(
                onClick = onClick,
                modifier = scaled,
                enabled = isEnabled,
                shape = SsShape.control,
                border = BorderStroke(1.dp, if (isEnabled) palette.borderStrong else palette.border),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = palette.surface,
                        contentColor = palette.text,
                        disabledContentColor = palette.muted,
                    ),
                contentPadding = padding,
                interactionSource = interaction,
            ) { content() }
        SsButtonVariant.Ghost ->
            TextButton(
                onClick = onClick,
                modifier = scaled.height(MinHeight),
                enabled = isEnabled,
                shape = SsShape.control,
                colors = ButtonDefaults.textButtonColors(contentColor = palette.primaryFg, disabledContentColor = palette.muted),
                contentPadding = padding,
                interactionSource = interaction,
            ) { content() }
    }
}
