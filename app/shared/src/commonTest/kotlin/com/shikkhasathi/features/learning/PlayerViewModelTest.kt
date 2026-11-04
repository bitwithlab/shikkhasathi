package com.shikkhasathi.features.learning

import com.shikkhasathi.core.common.LoadState
import com.shikkhasathi.core.common.dataOrNull
import com.shikkhasathi.core.model.ProgressResponse
import com.shikkhasathi.features.learning.presentation.PlayerTab
import com.shikkhasathi.features.learning.presentation.PlayerViewModel
import com.shikkhasathi.features.learning.presentation.VideoState
import com.shikkhasathi.features.learning.presentation.pickStartLesson
import com.shikkhasathi.features.testing.FakeLearningRepository
import com.shikkhasathi.features.testing.FakeMediaPlayer
import com.shikkhasathi.features.testing.apiError
import com.shikkhasathi.features.testing.lesson
import com.shikkhasathi.features.testing.outline
import com.shikkhasathi.features.testing.uid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    private fun TestScope.vm(
        repo: FakeLearningRepository,
        player: FakeMediaPlayer,
        lessonId: String? = null,
        background: CoroutineScope = this,
    ) = PlayerViewModel(
        courseId = uid(100),
        initialLessonId = lessonId,
        repository = repo,
        playerFactory = player.factory(),
        deviceId = "install-1234abcd",
        deviceName = "Xiaomi Redmi Note 12",
        backgroundScope = background,
    )

    private fun TestScope.playFor(
        player: FakeMediaPlayer,
        seconds: Int,
    ) = repeat(seconds) {
        player.step()
        runCurrent()
    }

    @Test
    fun resumesTheLastLessonWithTheInstallDeviceId() =
        test {
            val repo = FakeLearningRepository()
            val player = FakeMediaPlayer()
            val vm = vm(repo, player)
            runCurrent()
            assertEquals(uid(202), vm.state.value.currentLessonId, "resume lesson from the outline")
            assertEquals(listOf(uid(202) to "install-1234abcd"), repo.playbacks)
            assertEquals(listOf("https://video.test/${uid(202)}.m3u8" to 120), player.loads)
            assertEquals(120, vm.state.value.resumedFromSec)
            assertEquals("রাফি · 01712", (vm.state.value.video as VideoState.Ready).watermark)
            assertFalse(player.snapshot.value.isPlaying, "never autoplays on open")
            vm.clear()
        }

    @Test
    fun heartbeatEvery15sCountsOnlyRealPlayback() =
        test {
            val repo = FakeLearningRepository()
            val player = FakeMediaPlayer()
            val vm = vm(repo, player)
            runCurrent()
            vm.togglePlay()
            runCurrent()
            playFor(player, 15)
            advanceTimeBy(PlayerViewModel.HEARTBEAT_MS + 1)
            val first = repo.progress.single().second
            assertEquals(15, first.deltaSec)
            assertEquals(135, first.positionSec)
            assertEquals("st-1", first.streamToken)

            // A seek is not watching: +300 s jump counts 0, then 10 s at 2× counts 5 s.
            vm.seekBy(300)
            runCurrent()
            vm.setSpeed(2f)
            runCurrent()
            playFor(player, 5)
            advanceTimeBy(PlayerViewModel.HEARTBEAT_MS)
            val second = repo.progress.last().second
            assertEquals(5, second.deltaSec)
            assertEquals(445, second.positionSec)

            vm.togglePlay() // pause flushes immediately (nothing new → no request)
            runCurrent()
            assertEquals(2, repo.progress.size)
            vm.clear()
        }

    @Test
    fun takeoverPausesAndPlayHereGetsANewStream() =
        test {
            val repo = FakeLearningRepository().apply { progressAnswer = { ProgressResponse(streamActive = false, progressPercent = 33) } }
            val player = FakeMediaPlayer()
            val vm = vm(repo, player)
            runCurrent()
            vm.togglePlay()
            runCurrent()
            playFor(player, 10)
            advanceTimeBy(PlayerViewModel.HEARTBEAT_MS + 1)
            assertEquals(VideoState.TakenOver, vm.state.value.video)
            assertFalse(player.snapshot.value.isPlaying)

            repo.progressAnswer = { ProgressResponse(streamActive = true) }
            vm.playHere()
            runCurrent()
            assertEquals(2, repo.playbacks.size)
            assertIs<VideoState.Ready>(vm.state.value.video)
            assertTrue(player.snapshot.value.isPlaying)
            assertEquals(130, player.loads.last().second, "continues where it was, not the server's old position")
            vm.togglePlay()
            vm.clear()
        }

    @Test
    fun lockedLessonsAreNotOpenedAndCompleteUpdatesTheOutline() =
        test {
            val repo = FakeLearningRepository()
            val vm = vm(repo, FakeMediaPlayer())
            runCurrent()
            vm.selectLesson(uid(203))
            runCurrent()
            assertEquals(uid(202), vm.state.value.currentLessonId)
            assertNotNull(vm.state.value.message)

            vm.toggleComplete()
            runCurrent()
            assertEquals(listOf(uid(202) to true), repo.completes)
            val outline = (vm.state.value.outline as LoadState.Ready).data
            assertEquals(66, outline.progressPercent)
            assertEquals(2, outline.completedLessons)
            assertTrue(
                vm.state.value.lesson
                    ?.dataOrNull
                    ?.completed == true
            )

            vm.previous()
            runCurrent()
            assertEquals(uid(201), vm.state.value.currentLessonId)
            vm.clear()
        }

    @Test
    fun deviceLimitErrorIsShownOnTheVideo() =
        test {
            val repo = FakeLearningRepository().apply { playbackError = apiError("FORBIDDEN", status = 403) }
            val vm = vm(repo, FakeMediaPlayer())
            runCurrent()
            assertIs<VideoState.Error>(vm.state.value.video)
            vm.clear()
        }

    @Test
    fun leavingTheScreenSendsTheLastHeartbeatFromTheBackgroundScope() =
        test {
            val repo = FakeLearningRepository()
            val player = FakeMediaPlayer()
            val vm = vm(repo, player, background = this)
            runCurrent()
            vm.togglePlay()
            runCurrent()
            playFor(player, 7)
            vm.clear()
            runCurrent()
            assertEquals(
                7,
                repo.progress
                    .single()
                    .second.deltaSec
            )
            assertTrue(player.released)
        }

    @Test
    fun startLessonRules() {
        assertEquals(uid(202), pickStartLesson(outline(), preferred = null))
        assertEquals(uid(201), pickStartLesson(outline(), preferred = uid(201)))
        assertEquals(uid(202), pickStartLesson(outline(), preferred = uid(203)), "locked → resume")
        assertEquals(uid(202), pickStartLesson(outline(resume = null), preferred = null), "first not completed")
        assertEquals(null, pickStartLesson(outline(lessons = listOf(lesson(1, locked = true))), preferred = null))
    }

    @Test
    fun linkTabOpensTheQaPanel() =
        test {
            val vm =
                PlayerViewModel(
                    uid(100),
                    uid(201),
                    FakeLearningRepository(),
                    FakeMediaPlayer().factory(),
                    "install-1234abcd",
                    null,
                    this,
                    PlayerTab.Qa
                )
            runCurrent()
            assertEquals(PlayerTab.Qa, vm.state.value.tab)
            assertEquals(uid(201), vm.state.value.currentLessonId)
            vm.clear()
        }
}
