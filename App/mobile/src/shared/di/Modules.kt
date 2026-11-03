package com.shikkhasathi.shared.di

import com.shikkhasathi.core.common.corePlatformModule
import com.shikkhasathi.core.datastore.InstallId
import com.shikkhasathi.core.model.DeviceInfo
import com.shikkhasathi.core.network.ApiClient
import com.shikkhasathi.core.network.ApiConfig
import com.shikkhasathi.features.auth.data.ApiAuthRepository
import com.shikkhasathi.features.auth.data.AuthRepository
import com.shikkhasathi.features.auth.data.SessionState
import com.shikkhasathi.features.auth.data.SignOut
import com.shikkhasathi.features.auth.data.featuresPlatformModule
import com.shikkhasathi.features.auth.presentation.AuthViewModel
import com.shikkhasathi.features.checkout.data.ApiOrdersRepository
import com.shikkhasathi.features.checkout.data.OrdersRepository
import com.shikkhasathi.features.checkout.presentation.CheckoutResultViewModel
import com.shikkhasathi.features.checkout.presentation.CheckoutViewModel
import com.shikkhasathi.features.classroom.data.ApiClassroomRepository
import com.shikkhasathi.features.classroom.data.ClassroomRepository
import com.shikkhasathi.features.classroom.data.GoogleClientConfig
import com.shikkhasathi.features.classroom.presentation.AssignmentsViewModel
import com.shikkhasathi.features.classroom.presentation.CalendarViewModel
import com.shikkhasathi.features.classroom.presentation.ClassStreamViewModel
import com.shikkhasathi.features.classroom.presentation.ClassroomViewModel
import com.shikkhasathi.features.classroom.presentation.ConnectClassroomViewModel
import com.shikkhasathi.features.downloads.data.ContentKeyStore
import com.shikkhasathi.features.downloads.data.DownloadManager
import com.shikkhasathi.features.downloads.data.DownloadTransport
import com.shikkhasathi.features.downloads.data.DownloadsSessionWatcher
import com.shikkhasathi.features.downloads.data.KtorDownloadTransport
import com.shikkhasathi.features.downloads.data.OfflineDownloadManager
import com.shikkhasathi.features.downloads.presentation.DownloadsViewModel
import com.shikkhasathi.features.exam.data.ApiExamRepository
import com.shikkhasathi.features.exam.data.ExamRepository
import com.shikkhasathi.features.exam.presentation.ExamModeViewModel
import com.shikkhasathi.features.exam.presentation.FlashcardsViewModel
import com.shikkhasathi.features.exam.presentation.MockAttemptViewModel
import com.shikkhasathi.features.exam.presentation.MockResultViewModel
import com.shikkhasathi.features.exam.presentation.MockTestsViewModel
import com.shikkhasathi.features.explore.data.ApiCatalogRepository
import com.shikkhasathi.features.explore.data.CatalogRepository
import com.shikkhasathi.features.explore.presentation.CourseDetailViewModel
import com.shikkhasathi.features.explore.presentation.ExploreViewModel
import com.shikkhasathi.features.home.data.ApiDashboardRepository
import com.shikkhasathi.features.home.data.DashboardCacheJanitor
import com.shikkhasathi.features.home.data.DashboardRepository
import com.shikkhasathi.features.home.presentation.HomeViewModel
import com.shikkhasathi.features.learning.data.ApiLearningRepository
import com.shikkhasathi.features.learning.data.LearningRepository
import com.shikkhasathi.features.learning.presentation.LessonNotesViewModel
import com.shikkhasathi.features.learning.presentation.LessonQaViewModel
import com.shikkhasathi.features.learning.presentation.MyCoursesViewModel
import com.shikkhasathi.features.learning.presentation.NotesViewModel
import com.shikkhasathi.features.learning.presentation.PlayerTab
import com.shikkhasathi.features.learning.presentation.PlayerViewModel
import com.shikkhasathi.features.notifications.data.ApiNotificationsRepository
import com.shikkhasathi.features.notifications.data.NotificationsRepository
import com.shikkhasathi.features.notifications.data.PushRegistrar
import com.shikkhasathi.features.notifications.presentation.NotificationsViewModel
import com.shikkhasathi.shared.navigation.AppLinkBus
import com.shikkhasathi.shared.navigation.CheckoutResultRoute
import com.shikkhasathi.shared.navigation.CheckoutRoute
import com.shikkhasathi.shared.navigation.MockAttemptRoute
import com.shikkhasathi.shared.navigation.PlayerRoute
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
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

/** Discovery, checkout, learning, exam, offline downloads and push (Phase 11): data layer. */
fun learningDataModule(): Module =
    module {
        single { AppLinkBus() }
        single<CatalogRepository> { ApiCatalogRepository(api = get()) }
        single<OrdersRepository> { ApiOrdersRepository(api = get()) }
        single<LearningRepository> { ApiLearningRepository(api = get()) }
        single<ExamRepository> { ApiExamRepository(api = get()) }
        single(createdAtStart = true) {
            PushRegistrar(repository = get(), auth = get(), prefs = get(), platform = get<DeviceInfo>().platform, scope = get())
        }
        single { ContentKeyStore(prefs = get(), wrapper = get(), cipher = get()) }
        // Signed storage URLs: a plain client without the API's auth/refresh logic.
        single<DownloadTransport> { KtorDownloadTransport(HttpClient(get<HttpClientEngine>())) }
        single<DownloadManager> {
            val auth = get<AuthRepository>()
            OfflineDownloadManager(
                registry = get(),
                files = get(),
                transport = get(),
                keys = get(),
                cipher = get(),
                currentUserId = { (auth.session.value as? SessionState.SignedIn)?.user?.id },
                installId = get<InstallId>().value,
                scope = get(),
                videos = get(),
            )
        }
        single(createdAtStart = true) { DownloadsSessionWatcher(auth = get(), manager = get(), scope = get()) }
        factory { SignOut(auth = get(), push = get(), downloads = get()) }
    }

/** Phase 11 ViewModels (factories; the Compose layer scopes each one to its NavBackStackEntry). */
fun learningViewModelModule(): Module =
    module {
        factory { params -> ExploreViewModel(repository = get(), initialQuery = params.getOrNull<String>()) }
        factory { (slug: String) -> CourseDetailViewModel(slug = slug, repository = get()) }
        factory { (route: CheckoutRoute) ->
            CheckoutViewModel(courseId = route.courseId, slug = route.slug, orders = get(), catalog = get(), browser = get())
        }
        factory { (route: CheckoutResultRoute) -> CheckoutResultViewModel(orderId = route.orderId, orders = get()) }
        factory { MyCoursesViewModel(repository = get()) }
        factory { (route: PlayerRoute) ->
            val device = get<DeviceInfo>()
            PlayerViewModel(
                courseId = route.courseId,
                initialLessonId = route.lessonId,
                repository = get(),
                playerFactory = get(),
                deviceId = get<InstallId>().value,
                deviceName = device.deviceName,
                backgroundScope = get(),
                initialTab = PlayerTab.entries.firstOrNull { it.name.equals(route.tab, ignoreCase = true) } ?: PlayerTab.Lessons,
                downloads = get(),
            )
        }
        factory { (lessonId: String) -> LessonNotesViewModel(lessonId = lessonId, repository = get()) }
        factory { (lessonId: String) -> LessonQaViewModel(lessonId = lessonId, repository = get()) }
        factory { NotesViewModel(repository = get()) }
        factory { params -> ExamModeViewModel(repository = get(), requestedPlanId = params.getOrNull<String>()) }
        factory { (planId: String) -> FlashcardsViewModel(planId = planId, repository = get()) }
        factory { MockTestsViewModel(repository = get()) }
        factory { (route: MockAttemptRoute) ->
            MockAttemptViewModel(testId = route.testId, attemptId = route.attemptId, repository = get())
        }
        factory { (attemptId: String) -> MockResultViewModel(attemptId = attemptId, repository = get()) }
        factory { DownloadsViewModel(manager = get()) }
    }

fun allModules(config: AppConfig): List<Module> =
    listOf(corePlatformModule(), featuresPlatformModule(), appModule(config), learningDataModule(), learningViewModelModule())

/** Called once per process: `Application.onCreate` on Android, `MainViewController()` on iOS. */
fun initKoin(
    config: AppConfig,
    extra: List<Module> = emptyList(),
): KoinApplication = startKoin { modules(extra + allModules(config)) }
