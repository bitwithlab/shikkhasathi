package com.shikkhasathi.shared.navigation

import com.shikkhasathi.core.i18n.Str
import kotlinx.serialization.Serializable

/** Type-safe Navigation Compose destinations (`composable<HomeRoute> { … }`). */
@Serializable
sealed interface SsRoute

@Serializable
data object HomeRoute : SsRoute

@Serializable
data object ClassroomRoute : SsRoute

@Serializable
data object MyCoursesRoute : SsRoute

@Serializable
data object ExamModeRoute : SsRoute

@Serializable
data object ProfileRoute : SsRoute

@Serializable
data object ExploreRoute : SsRoute

@Serializable
data object MockTestsRoute : SsRoute

@Serializable
data object NotesRoute : SsRoute

@Serializable
data object RequestsRoute : SsRoute

@Serializable
data object NotificationsRoute : SsRoute

@Serializable
data object SettingsRoute : SsRoute

@Serializable
data object TeacherStudioRoute : SsRoute

/** Bottom bar, in order: হোম · ক্লাসরুম · কোর্স · Exam · প্রোফাইল (CLAUDE.md §0A, spec §5.3). */
enum class TopDestination(val route: SsRoute, val label: Str) {
    Home(HomeRoute, Str.NavHome),
    Classroom(ClassroomRoute, Str.NavClassroom),
    Courses(MyCoursesRoute, Str.NavCourses),
    Exam(ExamModeRoute, Str.NavExam),
    Profile(ProfileRoute, Str.NavProfile),
}

/** Drawer opened from the home header. Teacher Studio is shown only to sellers. */
enum class DrawerDestination(val route: SsRoute, val label: Str, val sellerOnly: Boolean = false) {
    Explore(ExploreRoute, Str.NavExplore),
    MockTests(MockTestsRoute, Str.NavMockTests),
    Notes(NotesRoute, Str.NavNotes),
    Requests(RequestsRoute, Str.NavRequests),
    Notifications(NotificationsRoute, Str.NavNotifications),
    Settings(SettingsRoute, Str.NavSettings),
    TeacherStudio(TeacherStudioRoute, Str.NavTeacherStudio, sellerOnly = true),
    ;

    companion object {
        fun visibleFor(isSeller: Boolean): List<DrawerDestination> = entries.filter { !it.sellerOnly || isSeller }
    }
}
