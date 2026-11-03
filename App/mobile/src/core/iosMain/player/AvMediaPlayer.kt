package com.shikkhasathi.core.player

import kotlinx.cinterop.ExperimentalForeignApi
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
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.playImmediatelyAtRate
import platform.AVFoundation.rate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.setRate
import platform.AVFoundation.timeControlStatus
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL

/**
 * AVPlayer behind [SsMediaPlayer] (HLS natively). Sampled every 500 ms on the main queue.
 * Written without Xcode in the cloud container — compile on macOS (docs/MOBILE_NOTES.md).
 */
@OptIn(ExperimentalForeignApi::class)
class AvMediaPlayer : SsMediaPlayer {
    val avPlayer = AVPlayer()
    private val state = MutableStateFlow(PlayerSnapshot())
    override val snapshot: StateFlow<PlayerSnapshot> = state.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var speed = 1f

    init {
        scope.launch {
            while (isActive) {
                publish()
                delay(SAMPLE_MS)
            }
        }
    }

    override fun load(
        url: String,
        startAtSec: Int,
    ) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        avPlayer.replaceCurrentItemWithPlayerItem(AVPlayerItem(uRL = nsUrl))
        if (startAtSec > 0) avPlayer.seekToTime(CMTimeMakeWithSeconds(startAtSec.toDouble(), TIMESCALE))
    }

    override fun play() = avPlayer.playImmediatelyAtRate(speed)

    override fun pause() = avPlayer.pause()

    override fun seekTo(positionSec: Double) = avPlayer.seekToTime(CMTimeMakeWithSeconds(positionSec, TIMESCALE))

    override fun setSpeed(speed: Float) {
        this.speed = speed
        if (avPlayer.rate > 0f) avPlayer.setRate(speed)
    }

    override fun release() {
        scope.cancel()
        avPlayer.pause()
        avPlayer.replaceCurrentItemWithPlayerItem(null)
    }

    private fun publish() {
        val item = avPlayer.currentItem
        val position = CMTimeGetSeconds(avPlayer.currentTime()).takeIf { !it.isNaN() } ?: 0.0
        val duration = item?.let { CMTimeGetSeconds(it.duration) }?.takeIf { !it.isNaN() && it.isFinite() } ?: 0.0
        val status = avPlayer.timeControlStatus
        state.value =
            PlayerSnapshot(
                positionSec = position,
                durationSec = duration,
                isPlaying = status == AVPlayerTimeControlStatusPlaying,
                isBuffering = status == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate,
                ended = duration > 0 && position >= duration - END_TOLERANCE_SEC,
                speed = speed,
                error = if (item?.status == AVPlayerItemStatusFailed) item.error?.localizedDescription else null,
            )
    }

    private companion object {
        const val SAMPLE_MS = 500L
        const val TIMESCALE = 600
        const val END_TOLERANCE_SEC = 0.5
    }
}
