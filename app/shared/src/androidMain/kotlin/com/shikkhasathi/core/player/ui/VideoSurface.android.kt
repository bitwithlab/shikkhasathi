package com.shikkhasathi.core.player.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.SurfaceView
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.shikkhasathi.core.player.ExoMediaPlayer
import com.shikkhasathi.core.player.SsMediaPlayer

/**
 * SurfaceView bound to the ExoPlayer. While a lesson video is on screen the window is FLAG_SECURE, so
 * screenshots and screen recordings of paid content come out black (video protection, spec §8 / ADR-022).
 */
@Composable
actual fun VideoSurface(
    player: SsMediaPlayer,
    modifier: Modifier,
) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val exo = (player as? ExoMediaPlayer)?.exo
    if (exo == null) {
        Box(modifier.background(Color.Black))
        return
    }
    AndroidView(
        factory = { context -> SurfaceView(context).also { exo.setVideoSurfaceView(it) } },
        modifier = modifier.background(Color.Black),
        onRelease = { exo.clearVideoSurfaceView(it) },
    )
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
