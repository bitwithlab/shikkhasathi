package com.shikkhasathi.core.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Media3 ExoPlayer behind [SsMediaPlayer]. HLS comes from `media3-exoplayer-hls` (picked by MIME type).
 * All calls happen on the main thread (ExoPlayer's application looper); the position is sampled every
 * 500 ms while playing so the heartbeat and the seek bar see real playback.
 *
 * Written against Media3 1.8 without an SDK in the cloud container — compile on a dev machine (docs/MOBILE_NOTES.md).
 */
class ExoMediaPlayer(context: Context) : SsMediaPlayer {
    val exo: ExoPlayer =
        ExoPlayer
            .Builder(context.applicationContext)
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
            .build()

    private val state = MutableStateFlow(PlayerSnapshot())
    override val snapshot: StateFlow<PlayerSnapshot> = state.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val listener =
        object : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) = publish()
        }

    init {
        exo.addListener(listener)
        scope.launch {
            while (isActive) {
                if (exo.isPlaying) publish()
                delay(SAMPLE_MS)
            }
        }
    }

    override fun load(
        url: String,
        startAtSec: Int,
    ) {
        val item =
            MediaItem
                .Builder()
                .setUri(url)
                .apply { if (url.substringBefore('?').endsWith(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8) }
                .build()
        exo.setMediaItem(item, startAtSec * MS)
        exo.prepare()
        publish()
    }

    override fun play() = exo.play()

    override fun pause() = exo.pause()

    override fun seekTo(positionSec: Double) {
        exo.seekTo((positionSec * MS).toLong())
        publish()
    }

    override fun setSpeed(speed: Float) {
        exo.playbackParameters = PlaybackParameters(speed)
        publish()
    }

    override fun release() {
        scope.cancel()
        exo.removeListener(listener)
        exo.release()
    }

    private fun publish() {
        val duration = exo.duration
        state.value =
            PlayerSnapshot(
                positionSec = exo.currentPosition / MS.toDouble(),
                durationSec = if (duration == C.TIME_UNSET) 0.0 else duration / MS.toDouble(),
                isPlaying = exo.isPlaying,
                isBuffering = exo.playbackState == Player.STATE_BUFFERING,
                ended = exo.playbackState == Player.STATE_ENDED,
                speed = exo.playbackParameters.speed,
                error = exo.playerError?.errorCodeName,
            )
    }

    private companion object {
        const val MS = 1000L
        const val SAMPLE_MS = 500L
        const val SEEK_STEP_MS = 10_000L
    }
}
