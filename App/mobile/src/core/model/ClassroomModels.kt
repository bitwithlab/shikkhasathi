package com.shikkhasathi.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Mirrors of Web/shared/src/contracts/classroom.ts (Zod is the source of truth; OpenAPI in Web/shared/openapi.json).
 * Field names match the contract exactly. Enum-typed properties carry a default so `SsJson.coerceInputValues`
 * turns a value added on the server later into that default instead of failing the whole response.
 * Timestamps stay ISO-8601 strings on the wire; `core.common.time` parses them where the UI needs a date.
 */

/** Cursor page: `page(T)` in contracts/common.ts. */
@Serializable
data class Page<T>(
    val items: List<T> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class ItemsResponse<T>(
    val items: List<T> = emptyList(),
)

/** `CLASSROOM_CONNECTION_STATUSES`. */
@Serializable
enum class ClassroomConnectionStatus {
    @SerialName("active")
    Active,

    @SerialName("needs_reauth")
    NeedsReauth,

    @SerialName("workspace_blocked")
    WorkspaceBlocked,

    @SerialName("error")
    Error,

    @SerialName("syncing")
    Syncing,
}

@Serializable
data class ClassroomStatus(
    val connected: Boolean = false,
    val status: ClassroomConnectionStatus? = null,
    val email: String? = null,
    val lastSyncAt: String? = null,
    val lastFullSyncAt: String? = null,
    val nextManualSyncAt: String? = null,
    val errorCode: String? = null,
    val courseCount: Int = 0,
    val isFake: Boolean = false,
)

@Serializable
enum class MaterialKind {
    @SerialName("drive")
    Drive,

    @SerialName("link")
    Link,

    @SerialName("youtube")
    Youtube,

    @SerialName("form")
    Form,
}

@Serializable
data class Material(
    val kind: MaterialKind = MaterialKind.Link,
    val title: String,
    val url: String? = null,
    val thumbnailUrl: String? = null,
    val mime: String? = null,
)

@Serializable
data class ClassCourse(
    val id: String,
    val name: String,
    val section: String? = null,
    val room: String? = null,
    val teacherNames: List<String> = emptyList(),
    /** `#RRGGBB` course colour (stripe on rows). */
    val color: String,
    val alternateLink: String? = null,
    val unreadCount: Int = 0,
    val pendingCount: Int = 0,
    val nextDueAt: String? = null,
)

@Serializable
data class Assignment(
    val id: String,
    val courseId: String,
    val courseName: String,
    val courseColor: String,
    val title: String,
    val description: String? = null,
    val workType: String,
    val dueAt: String? = null,
    val maxPoints: Double? = null,
    val status: AssignmentStatus = AssignmentStatus.Pending,
    val late: Boolean = false,
    val grade: Double? = null,
    val materials: List<Material> = emptyList(),
    /** Deep link into Classroom ("Classroom-এ খুলুন"); we never post or submit (read-only, §0A). */
    val alternateLink: String? = null,
    val postedAt: String? = null,
)

/** `FEED_KINDS`. */
@Serializable
enum class FeedKind {
    @SerialName("assignment")
    Assignment,

    @SerialName("material")
    Material,

    @SerialName("announcement")
    Announcement,

    @SerialName("graded")
    Graded,
}

@Serializable
data class FeedGrade(
    val assigned: Double? = null,
    val max: Double? = null,
)

@Serializable
data class FeedItem(
    val id: String,
    val kind: FeedKind = FeedKind.Announcement,
    val refId: String,
    val courseId: String,
    val courseName: String,
    val courseColor: String,
    val title: String,
    val body: String? = null,
    val materials: List<Material> = emptyList(),
    val grade: FeedGrade? = null,
    val alternateLink: String? = null,
    val occurredAt: String,
    val read: Boolean = false,
)

@Serializable
enum class StreamKind {
    @SerialName("assignment")
    Assignment,

    @SerialName("material")
    Material,

    @SerialName("announcement")
    Announcement,
}

@Serializable
data class StreamItem(
    val id: String,
    val kind: StreamKind = StreamKind.Announcement,
    val title: String,
    val body: String? = null,
    val author: String? = null,
    val materials: List<Material> = emptyList(),
    val postedAt: String? = null,
    val alternateLink: String? = null,
    val assignment: Assignment? = null,
)

@Serializable
data class StreamResponse(
    val course: ClassCourse,
    val items: List<StreamItem> = emptyList(),
)

@Serializable
enum class CalendarEventKind {
    @SerialName("assignment")
    Assignment,

    @SerialName("exam")
    Exam,
}

@Serializable
data class CalendarEvent(
    val id: String,
    val kind: CalendarEventKind = CalendarEventKind.Assignment,
    val title: String,
    val at: String,
    val courseName: String? = null,
    val color: String,
    val status: AssignmentStatus? = null,
    val link: String,
)

/** `status` query of `GET /classroom/assignments` (`ASSIGNMENT_STATUSES` + all/upcoming). */
enum class AssignmentFilter(val wire: String) {
    All("all"),
    Upcoming("upcoming"),
    Pending("pending"),
    TurnedIn("turned_in"),
    Graded("graded"),
    Missing("missing"),
}

@Serializable
data class ConnectBody(
    val code: String,
    val platform: ClientPlatform,
    val redirectUri: String? = null,
)

/** Body of `POST /classroom/feed/read` and `POST /notifications/read`: either ids or `all`. */
@Serializable
data class MarkReadBody(
    val ids: List<String>? = null,
    val all: Boolean? = null,
)

// ---------------------------------------------------------------- notifications

@Serializable
data class NotificationItem(
    val id: String,
    /** One of `NOTIFICATION_TYPES` (`classroom.due_24h` …). Kept as a string: the list grows server-side. */
    val type: String,
    val title: String,
    val body: String,
    val link: String? = null,
    val createdAt: String,
    val read: Boolean = false,
)

@Serializable
data class UnreadCount(
    val count: Int = 0,
)

@Serializable
data class PreferenceRow(
    /** `NOTIFICATION_TYPES` value; string so unknown types round-trip unchanged on PUT. */
    val type: String,
    /** `NOTIFICATION_CHANNELS`: in_app · push · email · sms. */
    val channel: String,
    val enabled: Boolean,
)

@Serializable
data class PushTokenBody(
    val platform: ClientPlatform,
    val token: String,
)
