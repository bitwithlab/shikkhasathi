package com.shikkhasathi.core.player

import kotlinx.coroutines.flow.StateFlow

/** What the player UI and the heartbeat need to know, sampled by the platform player (~2×/s while playing). */
data class PlayerSnapshot(
    val positionSec: Double = 0.0,
    /** 0 until the stream's duration is known. */
    val durationSec: Double = 0.0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val ended: Boolean = false,
    val speed: Float = 1f,
    /** Platform error text (for logs); the UI shows its own Bangla copy. */
    val error: String? = null,
)

/**
 * The one video player interface of the app (CLAUDE.md §6 "behind a common player interface").
 *
 * - Android: `ExoMediaPlayer` — Media3 ExoPlayer with the HLS module (`core/androidMain/player`).
 * - iOS: `AvMediaPlayer` — AVPlayer (`core/iosMain/player`), HLS natively.
 * - Desktop (dev harness): `SimulatedMediaPlayer`, a clock-driven stand-in without video.
 *
 * The surface is drawn by the expect/actual composable `VideoSurface(player)`; the moving watermark is a
 * Compose overlay on top of it, so it is identical on every platform.
 */
interface SsMediaPlayer {
    val snapshot: StateFlow<PlayerSnapshot>

    /** Loads an HLS (or progressive) URL and seeks to [startAtSec]; does not start playing. */
    fun load(
        url: String,
        startAtSec: Int = 0,
    )

    fun play()

    fun pause()

    fun seekTo(positionSec: Double)

    fun setSpeed(speed: Float)

    /** Frees the decoder; the instance is not used afterwards. */
    fun release()

    companion object {
        /** Speeds offered in the player menu (same as web). */
        val SPEEDS: List<Float> = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    }
}

/** Creates a player per screen (bound per platform in `corePlatformModule`). */
fun interface MediaPlayerFactory {
    fun create(): SsMediaPlayer
}

/**
 * Counts **real playback** between heartbeats, the same rule as the web player (ADR-022):
 * only forward progress while playing, a jump bigger than 2× the speed per sample is a seek and does not count,
 * and time is divided by the speed (2× playback of 10 s of video = 5 s watched).
 */
class WatchTimeTracker {
    private var accumulated = 0.0
    private var last: Double? = null

    /** Feed every snapshot (or at least every second while playing). */
    fun onSample(snapshot: PlayerSnapshot) {
        val prev = last
        val speed = snapshot.speed.coerceAtLeast(MIN_SPEED)
        if (prev != null && snapshot.isPlaying && !snapshot.isBuffering) {
            val d = snapshot.positionSec - prev
            if (d > 0 && d < 2 * maxOf(1f, speed)) accumulated += d / speed
        }
        last = snapshot.positionSec
    }

    /** A user seek: the next sample starts from here and the jump is not counted. */
    fun onSeek(positionSec: Double) {
        last = positionSec
    }

    /** Whole seconds watched since the last call, capped to the heartbeat contract (≤ 60). */
    fun drain(): Int {
        val whole = accumulated.toInt().coerceIn(0, MAX_DELTA_SEC)
        accumulated = 0.0
        return whole
    }

    val pendingSec: Double get() = accumulated

    companion object {
        const val MAX_DELTA_SEC = 60
        private const val MIN_SPEED = 0.25f
    }
}
