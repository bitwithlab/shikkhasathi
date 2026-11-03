package com.shikkhasathi.features.home

import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.model.DueGroupKey
import com.shikkhasathi.core.network.ApiError
import com.shikkhasathi.core.testing.sampleMe
import com.shikkhasathi.features.auth.FakeAuthRepository
import com.shikkhasathi.features.auth.data.SessionState
import com.shikkhasathi.features.classroom.presentation.Urgency
import com.shikkhasathi.features.home.data.CachedDashboard
import com.shikkhasathi.features.home.presentation.ClassroomCard
import com.shikkhasathi.features.home.presentation.HomeViewModel
import com.shikkhasathi.features.home.presentation.Widget
import com.shikkhasathi.features.testing.DASHBOARD_NOT_CONNECTED_JSON
import com.shikkhasathi.features.testing.FIXTURE_NOW
import com.shikkhasathi.features.testing.FakeClassroomRepository
import com.shikkhasathi.features.testing.FakeDashboardRepository
import com.shikkhasathi.features.testing.FixedClock
import com.shikkhasathi.features.testing.apiError
import com.shikkhasathi.features.testing.dashboardFixture
import com.shikkhasathi.features.testing.networkError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val clock = FixedClock()
    private val auth = FakeAuthRepository()
    private val classroom = FakeClassroomRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(repo: FakeDashboardRepository) = HomeViewModel(repo, classroom, auth, clock)

    @Test
    fun paintsCachedDashboardFirstThenNetwork() {
        val stale = dashboardFixture().let { it.copy(header = it.header.copy(name = "রাফি (ক্যাশ)", unreadNotifications = 9)) }
        val repo = FakeDashboardRepository(cachedEntry = CachedDashboard(stale, FIXTURE_NOW - 3.hours))
        repo.gate = CompletableDeferred()

        val vm = vm(repo)
        // Network still in flight: the cached copy is on screen, no skeleton.
        assertFalse(vm.state.value.loading)
        assertEquals(9, vm.state.value.header.unreadNotifications)
        assertIs<Widget.Ready<*>>(vm.state.value.dueSoon)
        assertEquals(listOf(false), repo.fetches)

        repo.gate!!.complete(Unit)
        assertEquals(3, vm.state.value.header.unreadNotifications)
        assertEquals("রাফি", vm.state.value.header.firstName)
        assertFalse(vm.state.value.offline)
        vm.clear()
    }

    @Test
    fun oneFailingWidgetDoesNotBlankTheOthers() {
        val vm = vm(FakeDashboardRepository())
        val s = vm.state.value

        assertEquals(Widget.Failed, s.recommendations, "recommendations section had error=unavailable")
        val due = assertIs<Widget.Ready<com.shikkhasathi.features.home.presentation.DueSoonUi>>(s.dueSoon).data
        assertEquals(listOf(DueGroupKey.Overdue, DueGroupKey.Today, DueGroupKey.Tomorrow, DueGroupKey.ThisWeek), due.groups.map { it.key })
        assertEquals(HomeViewModel.MAX_DUE_ROWS, due.shown)
        assertEquals(7, due.total)
        assertTrue(due.hasMore)
        assertEquals(
            Urgency.Danger,
            due.groups
                .first()
                .rows
                .single()
                .urgency,
            "missing work is red"
        )
        assertEquals(Str.DueOverdue, due.groups.first().label)

        val feed = assertIs<Widget.Ready<com.shikkhasathi.features.home.presentation.FeedUi>>(s.feed).data
        assertEquals(1, feed.unread)
        assertTrue(feed.items.first().unread)
        assertIs<Widget.Ready<*>>(s.exam)
        assertEquals(Widget.Ready(null), s.continueLearning, "no course yet → placeholder, not an error")
        val week = assertIs<Widget.Ready<List<com.shikkhasathi.features.home.presentation.WeekDayUi>>>(s.week).data
        assertEquals(7, week.size)
        assertTrue(week.first().isToday)
        assertEquals("মঙ্গল", week.first().weekday.resolve())
        assertEquals(ClassroomCard.None, s.classroomCard)
        vm.clear()
    }

    @Test
    fun syncLabelUsesBanglaDigits() {
        val vm = vm(FakeDashboardRepository())
        assertEquals(
            "সিঙ্ক হয়েছে ২ মিনিট আগে",
            vm.state.value.syncLabel
                ?.resolve()
        )

        clock.now = FIXTURE_NOW + 3.hours
        vm.tick()
        assertEquals(
            "সিঙ্ক হয়েছে ৩ ঘণ্টা আগে",
            vm.state.value.syncLabel
                ?.resolve()
        )
        vm.clear()
    }

    @Test
    fun notConnectedShowsConnectCardAndKeepsCourseWidgets() {
        val vm = vm(FakeDashboardRepository(next = { dashboardFixture(DASHBOARD_NOT_CONNECTED_JSON) }))
        val s = vm.state.value

        assertEquals(ClassroomCard.NotConnected, s.classroomCard)
        assertFalse(s.classroomConnected)
        assertEquals(Widget.Hidden, s.dueSoon)
        assertEquals(Widget.Hidden, s.feed)
        assertNull(s.syncLabel)
        assertEquals(Widget.Ready(null), s.exam, "exam prompt still renders")
        assertIs<Widget.Ready<*>>(s.week)
        assertFalse(s.loading, "never an empty page")

        vm.refresh()
        assertFalse("sync" in classroom.calls, "no sync without a connection")
        vm.clear()
    }

    @Test
    fun needsReauthStatusShowsReconnectCard() {
        val reauth =
            dashboardFixture().let {
                it.copy(
                    header =
                        it.header.copy(
                            classroom =
                                it.header.classroom.copy(
                                    status = com.shikkhasathi.core.model.ClassroomConnectionStatus.NeedsReauth
                                ),
                        ),
                )
            }
        val vm = vm(FakeDashboardRepository(next = { reauth }))
        assertEquals(ClassroomCard.NeedsReauth, vm.state.value.classroomCard)
        vm.clear()
    }

    @Test
    fun refreshSyncsThenReloadsFresh() {
        val repo = FakeDashboardRepository()
        val vm = vm(repo)
        vm.refresh()

        assertEquals(listOf("sync"), classroom.calls)
        assertEquals(listOf(false, true), repo.fetches, "reload bypasses the server's 60 s cache")
        assertFalse(vm.state.value.refreshing)
        assertNull(vm.state.value.message)
        vm.clear()
    }

    @Test
    fun refreshRateLimitedShowsFriendlyBanglaAndStillReloads() {
        val repo = FakeDashboardRepository()
        val vm = vm(repo)
        classroom.syncError = apiError(ApiError.RATE_LIMITED, status = 429, retryAfterSec = 42)

        vm.refresh()

        assertEquals(
            "এইমাত্র সিঙ্ক হয়েছে — এক মিনিট পর আবার চেষ্টা করুন",
            vm.state.value.message
                ?.resolve()
        )
        assertEquals(listOf(false, true), repo.fetches)
        assertFalse(vm.state.value.refreshing)
        assertIs<Widget.Ready<*>>(vm.state.value.dueSoon, "data stays on screen")

        vm.messageShown()
        assertNull(vm.state.value.message)
        vm.clear()
    }

    @Test
    fun refreshQuotaShowsLastSyncedTime() {
        val vm = vm(FakeDashboardRepository())
        classroom.syncError = apiError(ApiError.CLASSROOM_QUOTA, status = 429, retryAfterSec = 300)
        vm.refresh()
        val text = assertNotNull(vm.state.value.message).resolve()
        assertTrue("২ মিনিট আগে" in text, text)
        vm.clear()
    }

    @Test
    fun firstLoadFailureWithoutCacheShowsErrorThenRetryRecovers() {
        var fail = true
        val repo = FakeDashboardRepository(next = { if (fail) throw networkError else dashboardFixture() })
        val vm = vm(repo)

        assertEquals(Str.ErrorNetwork, (vm.state.value.error as com.shikkhasathi.core.i18n.UiText.Res).str)
        fail = false
        vm.retry()
        assertNull(vm.state.value.error)
        assertIs<Widget.Ready<*>>(vm.state.value.dueSoon)
        vm.clear()
    }

    @Test
    fun networkFailureWithCacheKeepsDataAndFlagsOffline() {
        val repo = FakeDashboardRepository(cachedEntry = CachedDashboard(dashboardFixture(), FIXTURE_NOW), next = { throw networkError })
        val vm = vm(repo)
        assertTrue(vm.state.value.offline)
        assertNull(vm.state.value.error)
        assertIs<Widget.Ready<*>>(vm.state.value.feed)
        vm.clear()
    }

    @Test
    fun markAllFeedReadIsOptimisticAndRevertsOnFailure() {
        val vm = vm(FakeDashboardRepository())
        classroom.markReadError = networkError
        vm.markAllFeedRead()

        val feed = assertIs<Widget.Ready<com.shikkhasathi.features.home.presentation.FeedUi>>(vm.state.value.feed).data
        assertEquals(1, feed.unread, "reverted")
        assertNotNull(vm.state.value.message)

        classroom.markReadError = null
        vm.markAllFeedRead()
        val after = assertIs<Widget.Ready<com.shikkhasathi.features.home.presentation.FeedUi>>(vm.state.value.feed).data
        assertEquals(0, after.unread)
        assertTrue(after.items.none { it.unread })
        assertEquals(listOf("feedRead:all", "feedRead:all"), classroom.calls)
        vm.clear()
    }

    @Test
    fun greetingComesFromProfileWhileDashboardLoads() {
        auth.sessionFlow.value = SessionState.SignedIn(sampleMe("রাফি আহমেদ"))
        val repo = FakeDashboardRepository().apply { gate = CompletableDeferred() }
        val vm = vm(repo)

        assertEquals("রাফি", vm.state.value.header.firstName)
        assertEquals(
            listOf("MU", "CSE", "সেমিস্টার ৫"),
            vm.state.value.header.academicParts
                .map { it.resolve() }
        )
        assertTrue(vm.state.value.loading)
        vm.clear()
    }
}
