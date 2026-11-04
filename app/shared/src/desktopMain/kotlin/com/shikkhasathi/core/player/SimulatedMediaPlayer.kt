package com.shikkhasathi.core.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Desktop dev harness only: no decoding, the position advances with the wall clock so the player screen,
 * heartbeats and takeover can be exercised against a local server.
 */
class SimulatedMediaPlayer(
    private val assumedDurationSec: Double = 600.0,
) : SsMediaPlayer {
    private val state = MutableStateFlow(PlayerSnapshot())
    override val snapshot: StateFlow<PlayerSnapshot> = state.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    override fun load(
        url: String,
        startAtSec: Int,
    ) {
        state.value = PlayerSnapshot(positionSec = startAtSec.toDouble(), durationSec = assumedDurationSec)
    }

    override fun play() {
        state.update { it.copy(isPlaying = true, ended = false) }
        ticker?.cancel()
        ticker =
            scope.launch {
                while (isActive) {
                    delay(TICK_MS)
                    state.update { s ->
                        val next = (s.positionSec + s.speed * TICK_MS / MS).coerceAtMost(s.durationSec)
                        s.copy(positionSec = next, ended = next >= s.durationSec, isPlaying = next < s.durationSec)
                    }
                    if (!state.value.isPlaying) break
                }
            }
    }

    override fun pause() {
        ticker?.cancel()
        state.update { it.copy(isPlaying = false) }
    }

    override fun seekTo(positionSec: Double) = state.update { it.copy(positionSec = positionSec.coerceIn(0.0, it.durationSec)) }

    override fun setSpeed(speed: Float) = state.update { it.copy(speed = speed) }

    override fun release() {
        ticker?.cancel()
        scope.coroutineContext[Job]?.cancel()
    }

    private companion object {
        const val TICK_MS = 500L
        const val MS = 1000.0
    }
}
