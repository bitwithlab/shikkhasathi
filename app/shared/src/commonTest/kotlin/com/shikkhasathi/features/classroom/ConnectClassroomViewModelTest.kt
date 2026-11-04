package com.shikkhasathi.features.classroom

import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.model.ClassroomConnectionStatus
import com.shikkhasathi.core.model.ClassroomStatus
import com.shikkhasathi.core.model.ClientPlatform
import com.shikkhasathi.core.model.DeviceInfo
import com.shikkhasathi.core.network.ApiError
import com.shikkhasathi.features.classroom.data.FakeGoogleAuthorization
import com.shikkhasathi.features.classroom.data.GoogleAuthorization
import com.shikkhasathi.features.classroom.data.GoogleAuthorizationResult
import com.shikkhasathi.features.classroom.presentation.ConnectClassroomViewModel
import com.shikkhasathi.features.classroom.presentation.ConnectPhase
import com.shikkhasathi.features.classroom.presentation.ConnectProblem
import com.shikkhasathi.features.classroom.presentation.connectCopy
import com.shikkhasathi.features.classroom.presentation.toClassroomUiText
import com.shikkhasathi.features.testing.FIXTURE_NOW
import com.shikkhasathi.features.testing.FakeClassroomRepository
import com.shikkhasathi.features.testing.FixedClock
import com.shikkhasathi.features.testing.apiError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectClassroomViewModelTest {
    private val device = DeviceInfo(ClientPlatform.Android)
    private val notConnected = ClassroomStatus(connected = false)
    private val repo = FakeClassroomRepository().apply { statusValue = notConnected }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        google: GoogleAuthorization =
            FakeGoogleAuthorization(
                "fake-test"
            )
    ) = ConnectClassroomViewModel(repo, google, device, FixedClock())

    @Test
    fun errorCodesMapToFriendlyBangla() {
        val last = FIXTURE_NOW - 10.minutes
        val reauth = connectCopy(null, ApiError.CLASSROOM_REAUTH, last, FIXTURE_NOW)
        assertEquals(ConnectProblem.NeedsReauth, reauth.problem)
        assertEquals("আবার সংযুক্ত করুন", reauth.cta?.text())

        val blocked = connectCopy(null, ApiError.CLASSROOM_WORKSPACE_BLOCKED, last, FIXTURE_NOW)
        assertEquals(ConnectProblem.WorkspaceBlocked, blocked.problem)
        assertEquals("এটা আপনার ভুল নয় — বিশ্ববিদ্যালয়ের Workspace অ্যাডমিন অনুমতি দিলে কাজ করবে।", blocked.body.resolve())
        val help = assertNotNull(blocked.help).resolve()
        assertEquals(true, "IT অফিস" in help && "ব্যক্তিগত Gmail" in help, help)

        val quota = connectCopy(null, ApiError.CLASSROOM_QUOTA, last, FIXTURE_NOW)
        assertEquals(ConnectProblem.Quota, quota.problem)
        assertEquals(true, "শেষ সিঙ্ক: ১০ মিনিট আগে" in quota.body.resolve(), quota.body.resolve())
        assertNull(quota.cta, "quota: nothing to press, the server backs off")

        val plain = connectCopy(notConnected, null, null, FIXTURE_NOW)
        assertEquals(ConnectProblem.None, plain.problem)
        assertEquals("সব অ্যাসাইনমেন্ট আর নোটিশ এক জায়গায়", plain.body.resolve())

        assertEquals(
            "এইমাত্র সিঙ্ক হয়েছে — এক মিনিট পর আবার চেষ্টা করুন",
            ApiError(ApiError.RATE_LIMITED, "x", 429).toClassroomUiText(last, FIXTURE_NOW).resolve(),
        )
    }

    @Test
    fun connectionStatusAloneDrivesTheCopy() {
        assertEquals(
            ConnectProblem.NeedsReauth,
            connectCopy(ClassroomStatus(connected = true, status = ClassroomConnectionStatus.NeedsReauth), null, null, FIXTURE_NOW).problem,
        )
        assertEquals(
            ConnectProblem.WorkspaceBlocked,
            connectCopy(
                ClassroomStatus(connected = false, status = ClassroomConnectionStatus.WorkspaceBlocked),
                null,
                null,
                FIXTURE_NOW
            ).problem,
        )
        assertEquals(
            ConnectProblem.Quota,
            connectCopy(
                ClassroomStatus(connected = true, status = ClassroomConnectionStatus.Active, errorCode = "quota"),
                null,
                null,
                FIXTURE_NOW
            ).problem,
        )
    }

    @Test
    fun connectSendsServerAuthCodeWithPlatform() {
        val vm = vm()
        vm.connect()
        assertEquals(listOf("connect:fake-test:Android"), repo.calls)
        assertEquals(ConnectPhase.Connected, vm.state.value.phase)
        assertEquals(
            Str.GcConnectedToast.text(),
            vm.state.value.message
                ?.resolve()
        )
        vm.clear()
    }

    @Test
    fun serverErrorDuringConnectSwitchesTheCard() {
        repo.connectError = apiError(ApiError.CLASSROOM_WORKSPACE_BLOCKED)
        val vm = vm()
        vm.connect()
        assertEquals(ConnectProblem.WorkspaceBlocked, vm.state.value.copy.problem)
        assertEquals(ConnectPhase.Idle, vm.state.value.phase)
        assertNull(vm.state.value.error, "the card explains it; no second error line")
        vm.clear()
    }

    @Test
    fun cancelledConsentIsCalmNotAnError() {
        val cancelled =
            object : GoogleAuthorization {
                override suspend fun authorize() = GoogleAuthorizationResult.Cancelled
            }
        val vm = vm(cancelled)
        vm.connect()
        assertEquals(
            Str.GcDeniedToast.text(),
            vm.state.value.error
                ?.resolve()
        )
        assertEquals(ConnectPhase.Idle, vm.state.value.phase)
        assertEquals(emptyList(), repo.calls)
        vm.clear()
    }
}
