package com.shikkhasathi.core.player

import kotlin.test.Test
import kotlin.test.assertEquals

class WatchTimeTrackerTest {
    private fun playing(
        pos: Double,
        speed: Float = 1f,
        buffering: Boolean = false,
    ) = PlayerSnapshot(positionSec = pos, isPlaying = true, speed = speed, isBuffering = buffering)

    @Test
    fun countsForwardPlaybackOnly() {
        val t = WatchTimeTracker()
        (0..20).forEach { t.onSample(playing(it.toDouble())) }
        assertEquals(20, t.drain())
        t.onSample(playing(19.0)) // backwards
        t.onSample(playing(120.0)) // a jump is a seek
        t.onSample(playing(120.5))
        assertEquals(0, t.drain(), "half a second is not reported")
    }

    @Test
    fun scalesBySpeedAndIgnoresPausedOrBufferingSamples() {
        val t = WatchTimeTracker()
        var p = 0.0
        t.onSample(playing(p, 2f))
        repeat(10) {
            p += 2.0
            t.onSample(playing(p, 2f))
        }
        assertEquals(10, t.drain(), "20 s of video at 2× = 10 s watched")
        t.onSample(PlayerSnapshot(positionSec = p + 1, isPlaying = false))
        t.onSample(playing(p + 2, buffering = true))
        assertEquals(0, t.drain())
    }

    @Test
    fun capsAtTheContractMaximum() {
        val t = WatchTimeTracker()
        (0..200).forEach { t.onSample(playing(it.toDouble())) }
        assertEquals(WatchTimeTracker.MAX_DELTA_SEC, t.drain())
    }

    @Test
    fun seekResetsTheReference() {
        val t = WatchTimeTracker()
        t.onSample(playing(10.0))
        t.onSeek(300.0)
        t.onSample(playing(301.0))
        assertEquals(1, t.drain())
    }
}
