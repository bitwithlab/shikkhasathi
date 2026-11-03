package com.shikkhasathi.shared.di

import com.shikkhasathi.core.datastore.InstallId
import com.shikkhasathi.core.model.DeviceInfo
import com.shikkhasathi.core.network.ApiClient
import com.shikkhasathi.features.auth.data.AuthRepository
import com.shikkhasathi.features.auth.data.SignOut
import com.shikkhasathi.features.auth.presentation.AuthViewModel
import com.shikkhasathi.features.checkout.presentation.CheckoutResultViewModel
import com.shikkhasathi.features.checkout.presentation.CheckoutViewModel
import com.shikkhasathi.features.classroom.presentation.AssignmentsViewModel
import com.shikkhasathi.features.classroom.presentation.CalendarViewModel
import com.shikkhasathi.features.classroom.presentation.ClassStreamViewModel
import com.shikkhasathi.features.classroom.presentation.ClassroomViewModel
import com.shikkhasathi.features.classroom.presentation.ConnectClassroomViewModel
import com.shikkhasathi.features.downloads.data.DownloadManager
import com.shikkhasathi.features.downloads.presentation.DownloadsViewModel
import com.shikkhasathi.features.exam.presentation.ExamModeViewModel
import com.shikkhasathi.features.exam.presentation.FlashcardsViewModel
import com.shikkhasathi.features.exam.presentation.MockAttemptViewModel
import com.shikkhasathi.features.exam.presentation.MockResultViewModel
import com.shikkhasathi.features.exam.presentation.MockTestsViewModel
import com.shikkhasathi.features.explore.presentation.CourseDetailViewModel
import com.shikkhasathi.features.explore.presentation.ExploreViewModel
import com.shikkhasathi.features.home.data.DashboardRepository
import com.shikkhasathi.features.home.presentation.HomeViewModel
import com.shikkhasathi.features.learning.presentation.LessonNotesViewModel
import com.shikkhasathi.features.learning.presentation.LessonQaViewModel
import com.shikkhasathi.features.learning.presentation.MyCoursesViewModel
import com.shikkhasathi.features.learning.presentation.NotesViewModel
import com.shikkhasathi.features.learning.presentation.PlayerViewModel
import com.shikkhasathi.features.notifications.data.PushRegistrar
import com.shikkhasathi.features.notifications.presentation.NotificationsViewModel
import com.shikkhasathi.shared.navigation.AppLinkBus
import com.shikkhasathi.shared.navigation.CheckoutResultRoute
import com.shikkhasathi.shared.navigation.CheckoutRoute
import com.shikkhasathi.shared.navigation.MockAttemptRoute
import com.shikkhasathi.shared.navigation.PlayerRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/** The real desktop graph (platform module + app module) resolves every binding the UI asks for. */
@OptIn(ExperimentalCoroutinesApi::class)
class KoinGraphTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun graphResolves() {
        val koin = initKoin(AppConfig(apiBaseUrl = "http://localhost:3000/api/v1")).koin

        assertSame(koin.get<ApiClient>(), koin.get<ApiClient>())
        assertSame(koin.get<AuthRepository>(), koin.get<AuthRepository>())
        assertNotSame(koin.get<AuthViewModel>(), koin.get<AuthViewModel>(), "ViewModels are factories")
        koin.get<HomeViewModel>().clear()
        koin.get<ClassroomViewModel>().clear()
        koin.get<ClassStreamViewModel> { parametersOf("gc-2203") }.clear()
        koin.get<AssignmentsViewModel>().clear()
        koin.get<AssignmentsViewModel> { parametersOf("gc-2203") }.clear()
        koin.get<CalendarViewModel>().clear()
        koin.get<NotificationsViewModel>().clear()
        koin.get<ConnectClassroomViewModel>().clear()
        assertSame(koin.get<DashboardRepository>(), koin.get<DashboardRepository>())

        // Phase 11
        koin.get<ExploreViewModel>().clear()
        koin.get<CourseDetailViewModel> { parametersOf("ds-midterm-cse-2203") }.clear()
        koin.get<CheckoutViewModel> { parametersOf(CheckoutRoute(courseId = "c-1", slug = "ds")) }.clear()
        koin.get<CheckoutResultViewModel> { parametersOf(CheckoutResultRoute(orderId = null)) }.clear()
        koin.get<MyCoursesViewModel>().clear()
        koin.get<PlayerViewModel> { parametersOf(PlayerRoute(courseId = "c-1", tab = "qa")) }.clear()
        koin.get<LessonNotesViewModel> { parametersOf("l-1") }.clear()
        koin.get<LessonQaViewModel> { parametersOf("l-1") }.clear()
        koin.get<NotesViewModel>().clear()
        koin.get<ExamModeViewModel>().clear()
        koin.get<ExamModeViewModel> { parametersOf("p-1") }.clear()
        koin.get<FlashcardsViewModel> { parametersOf("p-1") }.clear()
        koin.get<MockTestsViewModel>().clear()
        koin.get<MockAttemptViewModel> { parametersOf(MockAttemptRoute(testId = "t-1")) }.clear()
        koin.get<MockResultViewModel> { parametersOf("a-1") }.clear()
        koin.get<DownloadsViewModel>().clear()
        koin.get<SignOut>()
        assertSame(koin.get<PushRegistrar>(), koin.get<PushRegistrar>())
        assertSame(koin.get<AppLinkBus>(), koin.get<AppLinkBus>())
        assertSame(koin.get<DownloadManager>(), koin.get<DownloadManager>())
        val id = koin.get<InstallId>().value
        assertEquals(id, koin.get<DeviceInfo>().deviceId, "sign-in and playback use the same install id")
        assertEquals("http://localhost:3000/api/v1", koin.get<com.shikkhasathi.core.network.ApiConfig>().baseUrl)
    }
}
