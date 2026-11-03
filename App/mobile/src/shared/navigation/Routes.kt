package com.shikkhasathi.shared.navigation

import com.shikkhasathi.core.i18n.Str
import io.ktor.http.decodeURLPart
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

/** One Classroom class (stream / assignments / materials). [name] shows in the header while it loads. */
@Serializable
data class ClassStreamRoute(
    val courseId: String,
    val name: String = "",
) : SsRoute

/** All assignments, optionally pre-filtered to one class. */
@Serializable
data class AssignmentsRoute(
    val courseId: String? = null,
) : SsRoute

@Serializable
data object CalendarRoute : SsRoute

/**
 * Maps a web path from the API (`NotificationItem.link`, `CalendarEvent.link`, e.g. `/classroom/assignments?id=…`,
 * `/classroom/{courseId}?tab=materials`) to an in-app route. Null = no screen for it yet; open it on the web.
 */
fun routeForLink(link: String): SsRoute? {
    val path = link.substringBefore('?').substringBefore('#').trimEnd('/')
    val segments = path.split('/').filter { it.isNotEmpty() }
    return when {
        segments.isEmpty() || segments == listOf("home") -> HomeRoute
        segments == listOf("classroom") -> ClassroomRoute
        segments == listOf("classroom", "assignments") -> AssignmentsRoute()
        segments == listOf("classroom", "calendar") -> CalendarRoute
        segments.size == 2 && segments[0] == "classroom" -> ClassStreamRoute(courseId = decodePathSegment(segments[1]))
        segments == listOf("notifications") -> NotificationsRoute
        segments == listOf("exam-mode") -> ExamModeRoute
        segments == listOf("my-courses") -> MyCoursesRoute
        segments == listOf("explore") -> ExploreRoute
        else -> null
    }
}

/** Web origin for links the app has no screen for yet. */
const val WEB_ORIGIN = "https://shikkhasathi.com"

private fun decodePathSegment(value: String): String =
    runCatching { value.decodeURLPart() }.getOrDefault(value)

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
    Assignments(AssignmentsRoute(), Str.NavAssignments),
    Calendar(CalendarRoute, Str.NavCalendar),
    Notifications(NotificationsRoute, Str.NavNotifications),
    Settings(SettingsRoute, Str.NavSettings),
    TeacherStudio(TeacherStudioRoute, Str.NavTeacherStudio, sellerOnly = true),
    ;

    companion object {
        fun visibleFor(isSeller: Boolean): List<DrawerDestination> = entries.filter { !it.sellerOnly || isSeller }
    }
}
