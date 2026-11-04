package com.shikkhasathi.core.designsystem.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme

private const val PULSE_MS = 900

/** Loading placeholder (skeletons, never spinners, for content). A slow opacity pulse — no shimmer sweep. */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = SsShape.control,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(
        modifier
            .graphicsLayer { this.alpha = alpha }
            .background(SsTheme.palette.surface2, shape)
            .clearAndSetSemantics { },
    )
}

/** A card-shaped skeleton with title + two text lines. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    SsCard(modifier = modifier.fillMaxWidth()) {
        Column {
            SkeletonBox(Modifier.fillMaxWidth(0.5f).height(20.dp))
            Box(Modifier.height(SsSpace.s3))
            SkeletonBox(Modifier.fillMaxWidth().height(14.dp))
            Box(Modifier.height(SsSpace.s2))
            SkeletonBox(Modifier.fillMaxWidth(0.8f).height(14.dp))
        }
    }
}
