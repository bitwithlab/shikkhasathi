package com.shikkhasathi.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Mirrors of Web/shared/src/contracts/learning.ts (My Courses, player, notes, bookmarks, Q&A).

/** `filter` of `GET /me/courses`. */
enum class MyCoursesFilter(val wire: String) {
    All("all"),
    InProgress("in_progress"),
    Completed("completed"),
    Expiring("expiring"),
}

@Serializable
data class MyCourse(
    val courseId: String,
    val slug: String,
    val title: String,
    val courseCode: String? = null,
    val institutionShort: String? = null,
    val examType: String = "general",
    val sellerName: String,
    val coverColor: String? = null,
    val progressPercent: Int = 0,
    val completedLessons: Int = 0,
    val totalLessons: Int = 0,
    val resumeLessonId: String? = null,
    val resumeLessonTitle: String? = null,
    val lastAccessedAt: String? = null,
    val completedAt: String? = null,
    /** Null = lifetime access. */
    val accessUntil: String? = null,
    val expiringSoon: Boolean = false,
    val expired: Boolean = false,
)

/** `LESSON_TYPES`. */
@Serializable
enum class LessonType {
    @SerialName("video")
    Video,

    @SerialName("pdf")
    Pdf,

    @SerialName("text")
    Text,

    @SerialName("quiz")
    Quiz,

    @SerialName("mock")
    Mock,
}

@Serializable
data class PlayerLesson(
    val id: String,
    val title: String,
    val type: LessonType = LessonType.Video,
    val durationSec: Int = 0,
    val isPreview: Boolean = false,
    val isImportant: Boolean = false,
    val completed: Boolean = false,
    val lastPositionSec: Int = 0,
    /** Not enrolled and not a preview. */
    val locked: Boolean = false,
)

@Serializable
data class PlayerChapter(
    val id: String,
    val title: String,
    val lessons: List<PlayerLesson> = emptyList(),
)

@Serializable
data class PlayerSection(
    val id: String,
    val title: String,
    val chapters: List<PlayerChapter> = emptyList(),
)

@Serializable
data class PlayerCourse(
    val id: String,
    val slug: String,
    val title: String,
    val courseCode: String? = null,
    val sellerId: String = "",
    val sellerName: String = "",
    val sellerSlug: String? = null,
)

@Serializable
data class PlayerResource(
    val id: String,
    val kind: String,
    val title: String,
    val year: Int? = null,
)

@Serializable
data class MyReview(
    val id: String,
    val rating: Int,
    val body: String? = null,
)

/** `GET /learn/:courseId`. */
@Serializable
data class PlayerOutline(
    val course: PlayerCourse,
    val enrolled: Boolean = false,
    val isOwner: Boolean = false,
    val accessUntil: String? = null,
    val progressPercent: Int = 0,
    val completedLessons: Int = 0,
    val totalLessons: Int = 0,
    val resumeLessonId: String? = null,
    val sections: List<PlayerSection> = emptyList(),
    val resources: List<PlayerResource> = emptyList(),
    val myReview: MyReview? = null,
    val canReview: Boolean = false,
) {
    /** Lessons in curriculum order. */
    val lessons: List<PlayerLesson> get() = sections.flatMap { s -> s.chapters.flatMap { it.lessons } }
}

@Serializable
data class LessonDocument(
    val id: String,
    val title: String,
    val url: String,
    val pages: Int? = null,
)

@Serializable
enum class VideoKind {
    @SerialName("hls")
    Hls,

    @SerialName("youtube")
    Youtube,

    @SerialName("external")
    External,
}

@Serializable
data class LessonVideo(
    val kind: VideoKind = VideoKind.Hls,
    val status: String = "ready",
)

/** `GET /lessons/:id`. */
@Serializable
data class LessonDetail(
    val id: String,
    val courseId: String,
    val title: String,
    val type: LessonType = LessonType.Video,
    val durationSec: Int = 0,
    val isImportant: Boolean = false,
    val body: String? = null,
    val document: LessonDocument? = null,
    val video: LessonVideo? = null,
    val completed: Boolean = false,
    val lastPositionSec: Int = 0,
    val prevLessonId: String? = null,
    val nextLessonId: String? = null,
)

@Serializable
data class PlaybackBody(
    /** Stable per install (`InstallId`), counted against the 3-device limit. */
    val deviceId: String,
    val deviceName: String? = null,
)

/** `POST /lessons/:id/playback`. */
@Serializable
data class Playback(
    val kind: VideoKind = VideoKind.Hls,
    val url: String,
    val expiresAt: String = "",
    /** One active stream per user — sent with every heartbeat. */
    val streamToken: String,
    val watermark: String = "",
    val startAtSec: Int = 0,
)

@Serializable
data class ProgressBody(
    val positionSec: Int,
    /** Seconds of real playback since the last heartbeat (0–60). */
    val deltaSec: Int,
    val streamToken: String? = null,
)

@Serializable
data class ProgressResponse(
    val completed: Boolean = false,
    val progressPercent: Int = 0,
    /** False when another device/tab took over the stream: pause and offer "এখানে চালান". */
    val streamActive: Boolean = true,
)

@Serializable
data class CompleteBody(
    val completed: Boolean,
)

@Serializable
data class DocumentUrl(
    val url: String,
    val title: String = "",
)

@Serializable
data class Note(
    val id: String,
    val courseId: String,
    val lessonId: String,
    val lessonTitle: String = "",
    val courseTitle: String = "",
    val timestampSec: Int? = null,
    val body: String,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class CreateNoteBody(
    val lessonId: String,
    val timestampSec: Int? = null,
    val body: String,
)

@Serializable
data class UpdateNoteBody(
    val body: String,
)

@Serializable
data class Bookmark(
    val id: String,
    val courseId: String,
    val lessonId: String,
    val lessonTitle: String = "",
    val courseTitle: String = "",
    val timestampSec: Int? = null,
    val body: String? = null,
    val createdAt: String = "",
)

@Serializable
data class CreateBookmarkBody(
    val lessonId: String,
    val timestampSec: Int? = null,
    val body: String? = null,
)

@Serializable
data class Answer(
    val id: String,
    val body: String,
    val authorName: String = "",
    val isSeller: Boolean = false,
    val createdAt: String = "",
)

@Serializable
data class Question(
    val id: String,
    val lessonId: String,
    val lessonTitle: String? = null,
    val body: String,
    val timestampSec: Int? = null,
    val authorName: String = "",
    val mine: Boolean = false,
    val status: String = "open",
    val createdAt: String = "",
    val answers: List<Answer> = emptyList(),
)

@Serializable
data class AskBody(
    val body: String,
    val timestampSec: Int? = null,
)
