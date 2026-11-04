package com.shikkhasathi.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Mirrors of website/src/api/contracts/discovery.ts (search, suggest, course details, wishlist).
 * Enum-like strings that grow on the server (examType, courseType, lesson type) stay strings or carry a default,
 * so a new value never fails a whole page.
 */

/** Course card everywhere (search, browse, storefront): `CourseCardDto` = RecommendedCourse + listing facts. */
@Serializable
data class CourseCard(
    val id: String,
    val slug: String,
    val title: String,
    val courseCode: String? = null,
    val institutionShort: String? = null,
    val sellerName: String,
    val sellerVerified: Boolean = false,
    val sellerSlug: String? = null,
    val examType: String = "general",
    val courseType: String = "full",
    /** Whole taka. */
    val price: Int = 0,
    val compareAtPrice: Int? = null,
    val ratingAvg: Double = 0.0,
    val ratingCount: Int = 0,
    val coverUrl: String? = null,
    val coverColor: String? = null,
    val compatibility: Compatibility = Compatibility.General,
    val shortDescription: String = "",
    val lessonCount: Int = 0,
    val totalDurationSec: Int = 0,
    val enrollmentCount: Int = 0,
)

/** `SEARCH_SORTS`. */
enum class SearchSort(val wire: String) {
    Best("best"),
    Newest("newest"),
    Rating("rating"),
    PriceAsc("price_asc"),
    PriceDesc("price_desc"),
    Popular("popular"),
}

/** `price` filter of `GET /search`. */
enum class PriceFilter(val wire: String) {
    Free("free"),
    Paid("paid"),
}

/** The filters of `GET /search` that the app exposes. Null = not filtered. */
data class SearchFilters(
    val query: String = "",
    val examType: String? = null,
    val price: PriceFilter? = null,
    val sort: SearchSort = SearchSort.Best,
    val institutionId: String? = null,
    val subjectId: String? = null,
    val courseCode: String? = null,
) {
    val activeCount: Int
        get() = listOfNotNull(examType, price, institutionId, subjectId, courseCode).size + if (sort != SearchSort.Best) 1 else 0

    /** Query parameters in contract names (nulls are dropped by `withQuery`). */
    fun toQuery(
        cursor: String?,
        limit: Int,
    ): List<Pair<String, Any?>> =
        listOf(
            "q" to query.trim().ifEmpty { null },
            "examType" to examType,
            "price" to price?.wire,
            "sort" to sort.wire,
            "institutionId" to institutionId,
            "subjectId" to subjectId,
            "courseCode" to courseCode,
            "cursor" to cursor,
            "limit" to limit,
        )
}

@Serializable
data class SearchInterpretation(
    val term: String,
    val meaning: String,
)

@Serializable
data class SearchResponse(
    val items: List<CourseCard> = emptyList(),
    val total: Int = 0,
    val nextCursor: String? = null,
    /** "DS" → "Data Structures" — shown as a hint under the search box. */
    val interpreted: List<SearchInterpretation> = emptyList(),
    val engine: String = "postgres",
)

@Serializable
enum class SuggestionType {
    @SerialName("institution")
    Institution,

    @SerialName("subject")
    Subject,

    @SerialName("code")
    Code,

    @SerialName("teacher")
    Teacher,

    @SerialName("course")
    Course,
}

@Serializable
data class Suggestion(
    val type: SuggestionType = SuggestionType.Course,
    val id: String,
    val label: String,
    val sub: String? = null,
    /** Web path, e.g. `/c/ds-midterm-cse-2203` or `/explore?subjectId=…`. */
    val href: String,
)

@Serializable
data class SuggestResponse(
    val items: List<Suggestion> = emptyList(),
)

@Serializable
data class PublicLesson(
    val id: String,
    val title: String,
    val type: String = "video",
    val durationSec: Int = 0,
    val isPreview: Boolean = false,
    val isImportant: Boolean = false,
)

@Serializable
data class PublicChapter(
    val id: String,
    val title: String,
    val lessons: List<PublicLesson> = emptyList(),
)

@Serializable
data class PublicSection(
    val id: String,
    val title: String,
    val chapters: List<PublicChapter> = emptyList(),
)

@Serializable
data class CourseFaq(
    val q: String,
    val a: String,
)

@Serializable
data class CourseTarget(
    val label: String,
    val institutionSlug: String? = null,
    val courseCode: String? = null,
    val semester: Int? = null,
    val batches: List<String> = emptyList(),
)

@Serializable
data class CourseSeller(
    val id: String,
    val slug: String,
    val name: String,
    val headline: String? = null,
    val verified: Boolean = false,
    val ratingAvg: Double = 0.0,
    val studentCount: Int = 0,
    val courseCount: Int = 0,
)

@Serializable
data class CourseResources(
    val videos: Int = 0,
    val notes: Int = 0,
    val previousQuestionYears: List<Int> = emptyList(),
    val mockTests: Int = 0,
    val flashcards: Int = 0,
)

@Serializable
data class CourseViewer(
    val enrolled: Boolean = false,
    val wishlisted: Boolean = false,
    val firstLessonId: String? = null,
    val resumeLessonId: String? = null,
    val isOwner: Boolean = false,
)

@Serializable
data class Breadcrumb(
    val label: String,
    val href: String,
)

/** `GET /courses/:slug`. */
@Serializable
data class CourseDetail(
    val id: String,
    val slug: String,
    val title: String,
    val courseCode: String? = null,
    val institutionShort: String? = null,
    val sellerName: String,
    val sellerVerified: Boolean = false,
    val examType: String = "general",
    val courseType: String = "full",
    val price: Int = 0,
    val compareAtPrice: Int? = null,
    val ratingAvg: Double = 0.0,
    val ratingCount: Int = 0,
    val coverColor: String? = null,
    val compatibility: Compatibility = Compatibility.General,
    val shortDescription: String = "",
    val lessonCount: Int = 0,
    val totalDurationSec: Int = 0,
    val enrollmentCount: Int = 0,
    val description: String = "",
    val learnings: List<String> = emptyList(),
    val notFor: List<String> = emptyList(),
    val faq: List<CourseFaq> = emptyList(),
    val language: String = "bn",
    val accessType: String = "lifetime",
    val accessDays: Int? = null,
    val updatedAt: String = "",
    val breadcrumb: List<Breadcrumb> = emptyList(),
    val targets: List<CourseTarget> = emptyList(),
    val seller: CourseSeller,
    val sections: List<PublicSection> = emptyList(),
    val resources: CourseResources = CourseResources(),
    val ratingDistribution: List<Int> = emptyList(),
    val viewer: CourseViewer = CourseViewer(),
    val status: String = "published",
) {
    val isFree: Boolean get() = price == 0
}
