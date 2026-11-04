package com.shikkhasathi.core.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitView
import com.shikkhasathi.core.player.AvMediaPlayer
import com.shikkhasathi.core.player.SsMediaPlayer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerLayer
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoSurface(
    player: SsMediaPlayer,
    modifier: Modifier,
) {
    val av = (player as? AvMediaPlayer)?.avPlayer
    if (av == null) {
        Box(modifier.background(Color.Black))
        return
    }
    UIKitView(factory = { PlayerContainerView(av) }, modifier = modifier.background(Color.Black))
}

/** Keeps the AVPlayerLayer sized to the view on every layout pass. */
@OptIn(ExperimentalForeignApi::class)
private class PlayerContainerView(player: AVPlayer) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private val playerLayer = AVPlayerLayer.playerLayerWithPlayer(player)

    init {
        layer.addSublayer(playerLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        playerLayer.setFrame(bounds)
    }
}
