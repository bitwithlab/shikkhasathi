package com.shikkhasathi.shared.di

import com.shikkhasathi.core.common.corePlatformModule
import com.shikkhasathi.core.model.DeviceInfo
import com.shikkhasathi.core.network.ApiClient
import com.shikkhasathi.core.network.ApiConfig
import com.shikkhasathi.features.auth.data.ApiAuthRepository
import com.shikkhasathi.features.auth.data.AuthRepository
import com.shikkhasathi.features.auth.data.featuresPlatformModule
import com.shikkhasathi.features.auth.presentation.AuthViewModel
import com.shikkhasathi.features.classroom.data.ApiClassroomRepository
import com.shikkhasathi.features.classroom.data.ClassroomRepository
import com.shikkhasathi.features.classroom.data.GoogleClientConfig
import com.shikkhasathi.features.classroom.presentation.AssignmentsViewModel
import com.shikkhasathi.features.classroom.presentation.CalendarViewModel
import com.shikkhasathi.features.classroom.presentation.ClassStreamViewModel
import com.shikkhasathi.features.classroom.presentation.ClassroomViewModel
import com.shikkhasathi.features.classroom.presentation.ConnectClassroomViewModel
import com.shikkhasathi.features.home.data.ApiDashboardRepository
import com.shikkhasathi.features.home.data.DashboardCacheJanitor
import com.shikkhasathi.features.home.data.DashboardRepository
import com.shikkhasathi.features.home.presentation.HomeViewModel
import com.shikkhasathi.features.notifications.data.ApiNotificationsRepository
import com.shikkhasathi.features.notifications.data.NotificationsRepository
import com.shikkhasathi.features.notifications.presentation.NotificationsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

/** Build-time configuration handed in by the host app (Android BuildConfig / iOS Info.plist). */
data class AppConfig(
    val apiBaseUrl: String,
    val debug: Boolean = false,
    /** Google OAuth Web client id for the Classroom server auth code (Android); null = connect shows "unavailable". */
    val googleServerClientId: String? = null,
)

fun appModule(config: AppConfig): Module =
    module {
        single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        single { ApiConfig(baseUrl = config.apiBaseUrl, platform = get<DeviceInfo>().platform, enableLogging = config.debug) }
        single { ApiClient(engine = get(), config = get(), tokenStore = get()) }
        single<AuthRepository> { ApiAuthRepository(api = get(), tokenStore = get(), device = get(), appScope = get()) }
        // ViewModels are factories; the Compose layer scopes each instance to a NavBackStackEntry / screen.
        single { GoogleClientConfig(serverClientId = config.googleServerClientId) }
        single<ClassroomRepository> { ApiClassroomRepository(api = get()) }
        single<NotificationsRepository> { ApiNotificationsRepository(api = get()) }
        single<DashboardRepository> { ApiDashboardRepository(api = get(), cache = get()) }
        single(createdAtStart = true) { DashboardCacheJanitor(auth = get(), cache = get(), scope = get()) }

        factory { AuthViewModel(repository = get(), googleAuth = get()) }
        factory { HomeViewModel(dashboard = get(), classroom = get(), auth = get()) }
        factory { ClassroomViewModel(repository = get()) }
        factory { (courseId: String) -> ClassStreamViewModel(courseId = courseId, repository = get()) }
        factory { params -> AssignmentsViewModel(repository = get(), initialCourseId = params.getOrNull<String>()) }
        factory { CalendarViewModel(repository = get()) }
        factory { NotificationsViewModel(repository = get()) }
        factory { ConnectClassroomViewModel(repository = get(), google = get(), device = get()) }
    }

fun allModules(config: AppConfig): List<Module> = listOf(corePlatformModule(), featuresPlatformModule(), appModule(config))

/** Called once per process: `Application.onCreate` on Android, `MainViewController()` on iOS. */
fun initKoin(
    config: AppConfig,
    extra: List<Module> = emptyList(),
): KoinApplication = startKoin { modules(extra + allModules(config)) }
