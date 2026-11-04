package com.shikkhasathi.core.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shikkhasathi.core.player.SsMediaPlayer

/** Draws [player]'s video (Android SurfaceView + ExoPlayer, iOS AVPlayerLayer, desktop a dark placeholder). */
@Composable
expect fun VideoSurface(
    player: SsMediaPlayer,
    modifier: Modifier = Modifier,
)
