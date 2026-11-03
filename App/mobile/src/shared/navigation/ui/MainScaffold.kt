package com.shikkhasathi.shared.navigation.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.model.Me
import com.shikkhasathi.features.checkout.ui.CheckoutResultScreen
import com.shikkhasathi.features.checkout.ui.CheckoutScreen
import com.shikkhasathi.features.classroom.ui.AssignmentsScreen
import com.shikkhasathi.features.classroom.ui.CalendarScreen
import com.shikkhasathi.features.classroom.ui.ClassStreamScreen
import com.shikkhasathi.features.classroom.ui.ClassroomScreen
import com.shikkhasathi.features.downloads.ui.DownloadsScreen
import com.shikkhasathi.features.exam.ui.ExamModeScreen
import com.shikkhasathi.features.exam.ui.ExamNavigation
import com.shikkhasathi.features.exam.ui.FlashcardsScreen
import com.shikkhasathi.features.exam.ui.MockAttemptScreen
import com.shikkhasathi.features.exam.ui.MockResultScreen
import com.shikkhasathi.features.exam.ui.MockTestsScreen
import com.shikkhasathi.features.explore.ui.CourseDetailScreen
import com.shikkhasathi.features.explore.ui.ExploreScreen
import com.shikkhasathi.features.home.ui.HomeNavigation
import com.shikkhasathi.features.home.ui.HomeScreen
import com.shikkhasathi.features.learning.ui.MyCoursesScreen
import com.shikkhasathi.features.learning.ui.NotesScreen
import com.shikkhasathi.features.learning.ui.PlayerScreen
import com.shikkhasathi.features.notifications.ui.NotificationsScreen
import com.shikkhasathi.features.placeholder.ui.PlaceholderScreens
import com.shikkhasathi.features.profile.ui.ProfileScreen
import com.shikkhasathi.shared.navigation.AppLinkBus
import com.shikkhasathi.shared.navigation.AssignmentsRoute
import com.shikkhasathi.shared.navigation.CalendarRoute
import com.shikkhasathi.shared.navigation.CheckoutResultRoute
import com.shikkhasathi.shared.navigation.CheckoutRoute
import com.shikkhasathi.shared.navigation.ClassStreamRoute
import com.shikkhasathi.shared.navigation.ClassroomRoute
import com.shikkhasathi.shared.navigation.CourseDetailRoute
import com.shikkhasathi.shared.navigation.DownloadsRoute
import com.shikkhasathi.shared.navigation.DrawerDestination
import com.shikkhasathi.shared.navigation.ExamModeRoute
import com.shikkhasathi.shared.navigation.ExamPlanRoute
import com.shikkhasathi.shared.navigation.ExploreRoute
import com.shikkhasathi.shared.navigation.FlashcardsRoute
import com.shikkhasathi.shared.navigation.HomeRoute
import com.shikkhasathi.shared.navigation.MockAttemptRoute
import com.shikkhasathi.shared.navigation.MockResultRoute
import com.shikkhasathi.shared.navigation.MockTestsRoute
import com.shikkhasathi.shared.navigation.MyCoursesRoute
import com.shikkhasathi.shared.navigation.NotesRoute
import com.shikkhasathi.shared.navigation.NotificationsRoute
import com.shikkhasathi.shared.navigation.PlayerRoute
import com.shikkhasathi.shared.navigation.ProfileRoute
import com.shikkhasathi.shared.navigation.RequestsRoute
import com.shikkhasathi.shared.navigation.SettingsRoute
import com.shikkhasathi.shared.navigation.SsRoute
import com.shikkhasathi.shared.navigation.TeacherStudioRoute
import com.shikkhasathi.shared.navigation.TopDestination
import com.shikkhasathi.shared.navigation.WEB_ORIGIN
import com.shikkhasathi.shared.navigation.routeForLink
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Signed-in shell: bottom bar হোম · ক্লাসরুম · কোর্স · Exam · প্রোফাইল + a drawer opened from the home header. */
@Composable
fun MainScaffold(user: Me) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination
    val palette = SsTheme.palette

    fun openDrawerDestination(route: SsRoute) {
        scope.launch { drawer.close() }
        nav.navigate(route) { launchSingleTop = true }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = drawer.isOpen,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = palette.surface, drawerContentColor = palette.text) {
                Column(Modifier.padding(SsSpace.s3)) {
                    Text(
                        Str.AppName.t(),
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.primary,
                        modifier = Modifier.padding(horizontal = SsSpace.s3, vertical = SsSpace.s4),
                    )
                    HorizontalDivider(color = palette.border)
                    Spacer(Modifier.height(SsSpace.s2))
                    DrawerDestination.visibleFor(user.isSeller).forEach { item ->
                        NavigationDrawerItem(
                            label = { Text(item.label.t(), style = MaterialTheme.typography.titleSmall) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            selected = current.isOn(item.route),
                            onClick = { openDrawerDestination(item.route) },
                            shape = SsShape.control,
                            colors =
                                NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = palette.primarySoft,
                                    selectedTextColor = palette.primaryFg,
                                    selectedIconColor = palette.primaryFg,
                                    unselectedTextColor = palette.text,
                                    unselectedIconColor = palette.muted,
                                ),
                        )
                    }
                }
            }
        },
    ) {
        Scaffold(
            containerColor = palette.bg,
            bottomBar = {
                if (TopDestination.entries.any { current.isOn(it.route) }) {
                    BottomBar(current = current, onSelect = { nav.navigateTopLevel(it.route) })
                }
            },
        ) { padding ->
            SsNavHost(
                nav = nav,
                user = user,
                padding = padding,
                onOpenDrawer = { scope.launch { drawer.open() } },
            )
        }
    }
}

@Composable
private fun BottomBar(
    current: NavDestination?,
    onSelect: (TopDestination) -> Unit,
) {
    val palette = SsTheme.palette
    NavigationBar(containerColor = palette.surface, tonalElevation = 0.dp) {
        TopDestination.entries.forEach { item ->
            NavigationBarItem(
                selected = current.isOn(item.route),
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label.t(), style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = palette.primaryFg,
                        selectedTextColor = palette.primaryFg,
                        indicatorColor = palette.primarySoft,
                        unselectedIconColor = palette.muted,
                        unselectedTextColor = palette.muted,
                    ),
            )
        }
    }
}

@Composable
private fun SsNavHost(
    nav: NavHostController,
    user: Me,
    padding: PaddingValues,
    onOpenDrawer: () -> Unit,
) {
    val back: () -> Unit = { nav.popBackStack() }
    val uri = LocalUriHandler.current
    // API links are web paths (`/classroom/assignments?id=…`), App Links are full URLs: open the matching
    // screen, else the website.
    val openLink: (String) -> Unit = { link ->
        val route = routeForLink(link)
        when {
            route == null -> runCatching { uri.openUri(if (link.startsWith("http")) link else WEB_ORIGIN + link) }
            route in TopDestination.entries.map { it.route } -> nav.navigateTopLevel(route)
            else -> nav.navigate(route) { launchSingleTop = true }
        }
    }
    val openPlayer: (String, String?) -> Unit = { courseId, lessonId ->
        nav.navigate(PlayerRoute(courseId, lessonId)) {
            launchSingleTop =
                true
        }
    }
    val openCourse: (String) -> Unit = { slug -> nav.navigate(CourseDetailRoute(slug)) { launchSingleTop = true } }
    val examNavigation =
        ExamNavigation(
            openLesson = openPlayer,
            openMock = { testId -> nav.navigate(MockAttemptRoute(testId)) },
            openFlashcards = { planId -> nav.navigate(FlashcardsRoute(planId)) },
            openMockTests = { nav.navigate(MockTestsRoute) { launchSingleTop = true } },
        )

    // Links from outside the UI (App Links, push taps, payment return): handled once the shell is up.
    val links = koinInject<AppLinkBus>()
    LaunchedEffect(links) {
        links.pending.collect { link ->
            links.consumed()
            openLink(link)
        }
    }

    NavHost(navController = nav, startDestination = HomeRoute, modifier = Modifier.padding(padding)) {
        composable<HomeRoute> {
            HomeScreen(
                HomeNavigation(
                    openDrawer = onOpenDrawer,
                    openNotifications = { nav.navigate(NotificationsRoute) { launchSingleTop = true } },
                    openAssignments = { nav.navigate(AssignmentsRoute()) { launchSingleTop = true } },
                    openCalendar = { nav.navigate(CalendarRoute) { launchSingleTop = true } },
                    openExamMode = { nav.navigateTopLevel(ExamModeRoute) },
                    openMyCourses = { course -> openPlayer(course.courseId, course.lastLessonId) },
                    exploreCourses = { nav.navigate(ExploreRoute) },
                    openCourse = openCourse,
                ),
            )
        }
        composable<ClassroomRoute> {
            ClassroomScreen(
                onOpenClass = { id, name -> nav.navigate(ClassStreamRoute(courseId = id, name = name)) },
                onOpenAssignments = { nav.navigate(AssignmentsRoute()) },
                onOpenCalendar = { nav.navigate(CalendarRoute) },
            )
        }
        composable<ClassStreamRoute> { entry ->
            val route = entry.toRoute<ClassStreamRoute>()
            ClassStreamScreen(courseId = route.courseId, title = route.name, onBack = back)
        }
        composable<AssignmentsRoute> { entry ->
            AssignmentsScreen(courseId = entry.toRoute<AssignmentsRoute>().courseId, onBack = back)
        }
        composable<CalendarRoute> { CalendarScreen(onBack = back, onOpenLink = openLink) }
        composable<MyCoursesRoute> { MyCoursesScreen(onResume = openPlayer, onExplore = { nav.navigate(ExploreRoute) }) }
        composable<ExamModeRoute> { ExamModeScreen(planId = null, navigation = examNavigation) }
        composable<ExamPlanRoute> { entry ->
            ExamModeScreen(planId = entry.toRoute<ExamPlanRoute>().planId, navigation = examNavigation.copy(onBack = back))
        }
        composable<FlashcardsRoute> { entry -> FlashcardsScreen(planId = entry.toRoute<FlashcardsRoute>().planId, onBack = back) }
        composable<ProfileRoute> { ProfileScreen(user = user) }
        composable<ExploreRoute> { ExploreScreen(onBack = back, onOpenCourse = openCourse, onOpenLink = openLink) }
        composable<CourseDetailRoute> { entry ->
            CourseDetailScreen(
                slug = entry.toRoute<CourseDetailRoute>().slug,
                onBack = back,
                onCheckout = { courseId, slug -> nav.navigate(CheckoutRoute(courseId, slug)) },
                onLearn = openPlayer,
            )
        }
        composable<CheckoutRoute> { entry ->
            CheckoutScreen(
                route = entry.toRoute<CheckoutRoute>(),
                onBack = back,
                onStartLearning = { path ->
                    nav.popBackStack()
                    openLink(path ?: "/my-courses")
                },
            )
        }
        composable<CheckoutResultRoute> { entry ->
            CheckoutResultScreen(
                route = entry.toRoute<CheckoutResultRoute>(),
                onBack = back,
                onStartLearning = { path ->
                    nav.popBackStack()
                    openLink(path ?: "/my-courses")
                },
                onMyCourses = { nav.navigateTopLevel(MyCoursesRoute) },
            )
        }
        composable<PlayerRoute> { entry -> PlayerScreen(route = entry.toRoute<PlayerRoute>(), onBack = back) }
        composable<MockTestsRoute> {
            MockTestsScreen(
                onBack = back,
                onStart = { testId, attemptId -> nav.navigate(MockAttemptRoute(testId, attemptId)) },
                onExplore = { nav.navigate(ExploreRoute) },
            )
        }
        composable<MockAttemptRoute> { entry ->
            MockAttemptScreen(
                route = entry.toRoute<MockAttemptRoute>(),
                onBack = back,
                onFinished = { attemptId ->
                    nav.navigate(MockResultRoute(attemptId)) { popUpTo<MockAttemptRoute> { inclusive = true } }
                },
            )
        }
        composable<MockResultRoute> { entry ->
            MockResultScreen(
                attemptId = entry.toRoute<MockResultRoute>().attemptId,
                onBack = back,
                onRetake = { testId -> nav.navigate(MockAttemptRoute(testId)) { popUpTo<MockResultRoute> { inclusive = true } } },
            )
        }
        composable<NotesRoute> { NotesScreen(onBack = back, onOpenLesson = openPlayer) }
        composable<DownloadsRoute> { DownloadsScreen(onBack = back, onOpenLesson = openPlayer) }
        composable<RequestsRoute> { PlaceholderScreens.Requests(onBack = back) }
        composable<NotificationsRoute> { NotificationsScreen(onBack = back, onOpenLink = openLink) }
        composable<SettingsRoute> { PlaceholderScreens.Settings(onBack = back) }
        composable<TeacherStudioRoute> { PlaceholderScreens.TeacherStudio(onBack = back) }
    }
}

private fun NavDestination?.isOn(route: SsRoute): Boolean = this?.hierarchy?.any { it.hasRoute(route::class) } == true

/** Bottom-bar navigation: one copy of each tab, state saved/restored when switching (standard M3 behaviour). */
private fun NavHostController.navigateTopLevel(route: SsRoute) {
    navigate(route) {
        popUpTo(HomeRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
