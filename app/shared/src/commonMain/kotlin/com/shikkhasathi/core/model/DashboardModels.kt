package com.shikkhasathi.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * `GET /dashboard` — mirrors `Dashboard` in website/src/api/contracts/classroom.ts.
 * Every widget is a `Section`: `{ data | null, error | null }`. The server fills the sections independently,
 * so one failing widget (error set, data null) never blanks the home screen.
 */

@Serializable
data class Section<T>(
    val data: T? = null,
    /** Machine code from the server (e.g. `CLASSROOM_QUOTA`) or a short message; null when the widget loaded. */
    val error: String? = null,
)

@Serializable
data class DashboardHeader(
    val name: String,
    val institutionShort: String? = null,
    val departmentShort: String? = null,
    val semester: Int? = null,
    val unreadNotifications: Int = 0,
    val classroom: ClassroomStatus = ClassroomStatus(),
)

@Serializable
enum class DueGroupKey {
    @SerialName("overdue")
    Overdue,

    @SerialName("today")
    Today,

    @SerialName("tomorrow")
    Tomorrow,

    @SerialName("this_week")
    ThisWeek,
}

@Serializable
data class DueGroup(
    val key: DueGroupKey = DueGroupKey.ThisWeek,
    val items: List<Assignment> = emptyList(),
)

@Serializable
data class DueSoon(
    val groups: List<DueGroup> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class DashboardFeed(
    val items: List<FeedItem> = emptyList(),
    val unread: Int = 0,
)

@Serializable
data class ExamWidget(
    val planId: String,
    val subjectName: String,
    val courseCode: String? = null,
    val examType: String,
    val examDate: String,
    val daysLeft: Int,
    /** 0–100. */
    val readiness: Int = 0,
)

@Serializable
data class ContinueWidget(
    val courseId: String,
    val courseSlug: String,
    val title: String,
    val courseCode: String? = null,
    val sellerName: String,
    val progressPercent: Int = 0,
    val completedLessons: Int = 0,
    val totalLessons: Int = 0,
    val lastLessonId: String? = null,
    val lastLessonTitle: String? = null,
    val coverColor: String? = null,
)

/** One day of the এই সপ্তাহ strip; [date] is a Dhaka calendar day `YYYY-MM-DD`. */
@Serializable
data class WeekDay(
    val date: String,
    val deadlines: Int = 0,
    val exams: Int = 0,
)

@Serializable
enum class Compatibility {
    @SerialName("match")
    Match,

    @SerialName("mismatch")
    Mismatch,

    @SerialName("general")
    General,
}

@Serializable
data class RecommendedCourse(
    val id: String,
    val slug: String,
    val title: String,
    val courseCode: String? = null,
    val institutionShort: String? = null,
    val sellerName: String,
    val sellerVerified: Boolean = false,
    val examType: String,
    /** Whole taka. */
    val price: Int,
    val compareAtPrice: Int? = null,
    val ratingAvg: Double = 0.0,
    val ratingCount: Int = 0,
    val coverUrl: String? = null,
    val coverColor: String? = null,
    val compatibility: Compatibility = Compatibility.General,
)

@Serializable
data class Dashboard(
    val header: DashboardHeader,
    val dueSoon: Section<DueSoon> = Section(),
    val feed: Section<DashboardFeed> = Section(),
    val exam: Section<ExamWidget> = Section(),
    val continueLearning: Section<ContinueWidget> = Section(),
    val week: Section<List<WeekDay>> = Section(),
    val recommendations: Section<List<RecommendedCourse>> = Section(),
    val generatedAt: String,
)
