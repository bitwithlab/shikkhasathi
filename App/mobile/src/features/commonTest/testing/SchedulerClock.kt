package com.shikkhasathi.features.testing

import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** A clock that follows the test scheduler's virtual time, starting at [start]. */
class SchedulerClock(
    private val scheduler: TestCoroutineScheduler,
    private val start: Instant = FIXTURE_NOW,
) : Clock {
    override fun now(): Instant = start + scheduler.currentTime.milliseconds
}
