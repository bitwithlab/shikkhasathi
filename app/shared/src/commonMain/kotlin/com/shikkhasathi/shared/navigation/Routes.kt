package com.shikkhasathi.shared.navigation

import com.shikkhasathi.core.i18n.Str
import io.ktor.http.decodeURLPart
import io.ktor.http.decodeURLQueryComponent
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

/** Course page (`/c/:slug`). */
@Serializable
data class CourseDetailRoute(
    val slug: String,
) : SsRoute

/** Checkout for one course (`/checkout/:courseId`). [slug] lets the summary load from the course page data. */
@Serializable
data class CheckoutRoute(
    val courseId: String,
    val slug: String? = null,
) : SsRoute

/** Where the payment gateway returns (`/checkout/result?order=…&status=…`, via App Link). */
@Serializable
data class CheckoutResultRoute(
    val orderId: String? = null,
) : SsRoute

/** Lesson player (`/learn/:courseId/:lessonId?tab=qa`). Null lesson = resume. [tab] is `lessons` · `notes` · `qa`. */
@Serializable
data class PlayerRoute(
    val courseId: String,
    val lessonId: String? = null,
    val tab: String? = null,
) : SsRoute

/** One exam plan in Exam Mode (`/exam-mode/:planId`). */
@Serializable
data class ExamPlanRoute(
    val planId: String,
) : SsRoute

@Serializable
data class FlashcardsRoute(
    val planId: String,
) : SsRoute

/** Timed attempt of a mock test (`/mock-test/:testId`); starts or resumes. */
@Serializable
data class MockAttemptRoute(
    val testId: String,
    val attemptId: String? = null,
) : SsRoute

/** Result + review (`/mock-test/:attemptId/result`). */
@Serializable
data class MockResultRoute(
    val attemptId: String,
) : SsRoute

@Serializable
data object DownloadsRoute : SsRoute

/** Hosts whose links open inside the app (App Links / Universal Links + links in API payloads). */
val APP_LINK_HOSTS: Set<String> = setOf("shikkhasathi.com", "www.shikkhasathi.com")

/**
 * Maps a link to an in-app route. Accepts web paths from the API (`NotificationItem.link`, `CalendarEvent.link`,
 * push `data.link`: `/classroom/assignments?id=…`, `/learn/{courseId}/{lessonId}?tab=qa`, `/exam-mode/{planId}`…)
 * and full `https://shikkhasathi.com/...` App Links. Null = no screen for it; open it on the web.
 */
@Suppress("CyclomaticComplexMethod") // one flat `when` branch per web route
fun routeForLink(link: String): SsRoute? {
    val path = appPath(link) ?: return null
    val query = parseQuery(path.substringAfter('?', ""))
    val segments =
        path
            .substringBefore('?')
            .substringBefore('#')
            .trimEnd('/')
            .split('/')
            .filter { it.isNotEmpty() }
            .map(::decodePathSegment)
    val first = segments.firstOrNull()
    return when {
        segments.isEmpty() || segments == listOf("home") -> HomeRoute
        segments == listOf("classroom") -> ClassroomRoute
        segments == listOf("classroom", "assignments") -> AssignmentsRoute()
        segments == listOf("classroom", "calendar") -> CalendarRoute
        segments.size == 2 && first == "classroom" -> ClassStreamRoute(courseId = segments[1])
        segments == listOf("notifications") -> NotificationsRoute
        segments == listOf("exam-mode") -> ExamModeRoute
        segments.size == 2 && first == "exam-mode" -> ExamPlanRoute(planId = segments[1])
        segments == listOf("my-courses") -> MyCoursesRoute
        segments == listOf("explore") -> ExploreRoute
        segments == listOf("mock-tests") -> MockTestsRoute
        segments.size == 2 && first == "mock-test" -> MockAttemptRoute(testId = segments[1])
        segments.size == RESULT_PATH_SIZE && first == "mock-test" && segments[2] == "result" -> MockResultRoute(attemptId = segments[1])
        segments.size in 2..RESULT_PATH_SIZE && first == "learn" ->
            PlayerRoute(
                courseId = segments[1],
                lessonId = segments.getOrNull(2),
                tab = query["tab"]
            )
        segments.size == 2 && first == "c" -> CourseDetailRoute(slug = segments[1])
        segments == listOf("checkout", "result") -> CheckoutResultRoute(orderId = query["order"])
        segments.size == 2 && first == "checkout" -> CheckoutRoute(courseId = segments[1])
        segments == listOf("notes") -> NotesRoute
        segments == listOf("requests") -> RequestsRoute
        segments == listOf("profile") -> ProfileRoute
        segments == listOf("downloads") -> DownloadsRoute
        else -> null
    }
}

/** `https://shikkhasathi.com/x?y` → `/x?y`; `/x` stays; any other origin or scheme → null. */
private fun appPath(link: String): String? {
    val trimmed = link.trim()
    if (trimmed.startsWith("/")) return trimmed
    val scheme = trimmed.substringBefore("://", "")
    val rest = trimmed.substringAfter("://")
    val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
    val host = authority.substringBefore(':').lowercase()
    val path = rest.drop(authority.length)
    return when {
        scheme != "https" && scheme != "http" -> null
        host !in APP_LINK_HOSTS -> null
        path.startsWith("/") -> path
        else -> "/$path"
    }
}

/** `/learn/{course}/{lesson}` and `/mock-test/{id}/result` have three segments. */
private const val RESULT_PATH_SIZE = 3

private fun parseQuery(query: String): Map<String, String> =
    query
        .substringBefore('#')
        .split('&')
        .filter { it.isNotEmpty() }
        .associate { part -> decodeQuery(part.substringBefore('=')) to decodeQuery(part.substringAfter('=', "")) }

/** Web origin for links the app has no screen for yet. */
const val WEB_ORIGIN = "https://shikkhasathi.com"

private fun decodePathSegment(value: String): String =
    runCatching { value.decodeURLPart() }.getOrDefault(value)

private fun decodeQuery(value: String): String =
    runCatching { value.decodeURLQueryComponent(plusIsSpace = true) }.getOrDefault(value)

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
    Downloads(DownloadsRoute, Str.NavDownloads),
    Settings(SettingsRoute, Str.NavSettings),
    TeacherStudio(TeacherStudioRoute, Str.NavTeacherStudio, sellerOnly = true),
    ;

    companion object {
        fun visibleFor(isSeller: Boolean): List<DrawerDestination> = entries.filter { !it.sellerOnly || isSeller }
    }
}
