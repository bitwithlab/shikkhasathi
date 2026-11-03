package com.shikkhasathi.shared.di

import com.shikkhasathi.core.network.ApiClient
import com.shikkhasathi.features.auth.data.AuthRepository
import com.shikkhasathi.features.auth.presentation.AuthViewModel
import com.shikkhasathi.features.classroom.presentation.AssignmentsViewModel
import com.shikkhasathi.features.classroom.presentation.CalendarViewModel
import com.shikkhasathi.features.classroom.presentation.ClassStreamViewModel
import com.shikkhasathi.features.classroom.presentation.ClassroomViewModel
import com.shikkhasathi.features.classroom.presentation.ConnectClassroomViewModel
import com.shikkhasathi.features.home.data.DashboardRepository
import com.shikkhasathi.features.home.presentation.HomeViewModel
import com.shikkhasathi.features.notifications.presentation.NotificationsViewModel
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
        assertEquals("http://localhost:3000/api/v1", koin.get<com.shikkhasathi.core.network.ApiConfig>().baseUrl)
    }
}
