package com.shikkhasathi.core.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.shikkhasathi.core.player.SsMediaPlayer

@Composable
actual fun VideoSurface(
    player: SsMediaPlayer,
    modifier: Modifier,
) {
    Box(modifier.background(Color.Black))
}
