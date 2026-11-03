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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shikkhasathi.core.designsystem.SsShape
import com.shikkhasathi.core.designsystem.SsSpace
import com.shikkhasathi.core.designsystem.SsTheme
import com.shikkhasathi.core.designsystem.t
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.model.Me
import com.shikkhasathi.features.home.ui.HomeScreen
import com.shikkhasathi.features.placeholder.ui.PlaceholderScreens
import com.shikkhasathi.features.profile.ui.ProfileScreen
import com.shikkhasathi.shared.navigation.ClassroomRoute
import com.shikkhasathi.shared.navigation.DrawerDestination
import com.shikkhasathi.shared.navigation.ExamModeRoute
import com.shikkhasathi.shared.navigation.ExploreRoute
import com.shikkhasathi.shared.navigation.HomeRoute
import com.shikkhasathi.shared.navigation.MockTestsRoute
import com.shikkhasathi.shared.navigation.MyCoursesRoute
import com.shikkhasathi.shared.navigation.NotesRoute
import com.shikkhasathi.shared.navigation.NotificationsRoute
import com.shikkhasathi.shared.navigation.ProfileRoute
import com.shikkhasathi.shared.navigation.RequestsRoute
import com.shikkhasathi.shared.navigation.SettingsRoute
import com.shikkhasathi.shared.navigation.SsRoute
import com.shikkhasathi.shared.navigation.TeacherStudioRoute
import com.shikkhasathi.shared.navigation.TopDestination
import kotlinx.coroutines.launch

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
    NavHost(navController = nav, startDestination = HomeRoute, modifier = Modifier.padding(padding)) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenDrawer = onOpenDrawer,
                onOpenNotifications = { nav.navigate(NotificationsRoute) },
                onConnectClassroom = { nav.navigateTopLevel(ClassroomRoute) },
                onExploreCourses = { nav.navigate(ExploreRoute) },
            )
        }
        composable<ClassroomRoute> { PlaceholderScreens.Classroom() }
        composable<MyCoursesRoute> { PlaceholderScreens.MyCourses(onExplore = { nav.navigate(ExploreRoute) }) }
        composable<ExamModeRoute> { PlaceholderScreens.ExamMode() }
        composable<ProfileRoute> { ProfileScreen(user = user) }
        composable<ExploreRoute> { PlaceholderScreens.Explore(onBack = back) }
        composable<MockTestsRoute> { PlaceholderScreens.MockTests(onBack = back) }
        composable<NotesRoute> { PlaceholderScreens.Notes(onBack = back) }
        composable<RequestsRoute> { PlaceholderScreens.Requests(onBack = back) }
        composable<NotificationsRoute> { PlaceholderScreens.Notifications(onBack = back) }
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
