package com.shikkhasathi.features.testing

import com.shikkhasathi.core.common.ExternalBrowser
import com.shikkhasathi.core.model.Attempt
import com.shikkhasathi.core.model.AttemptOption
import com.shikkhasathi.core.model.AttemptQuestion
import com.shikkhasathi.core.model.AttemptResult
import com.shikkhasathi.core.model.Bookmark
import com.shikkhasathi.core.model.CouponValidateResponse
import com.shikkhasathi.core.model.CourseCard
import com.shikkhasathi.core.model.CourseDetail
import com.shikkhasathi.core.model.CourseSeller
import com.shikkhasathi.core.model.CourseViewer
import com.shikkhasathi.core.model.DocumentUrl
import com.shikkhasathi.core.model.ExamPlanDetail
import com.shikkhasathi.core.model.ExamPlanInput
import com.shikkhasathi.core.model.ExamPlanSummary
import com.shikkhasathi.core.model.Flashcard
import com.shikkhasathi.core.model.LessonDetail
import com.shikkhasathi.core.model.LessonType
import com.shikkhasathi.core.model.MockSummary
import com.shikkhasathi.core.model.MyCourse
import com.shikkhasathi.core.model.MyCoursesFilter
import com.shikkhasathi.core.model.Note
import com.shikkhasathi.core.model.Order
import com.shikkhasathi.core.model.OrderItem
import com.shikkhasathi.core.model.OrderStatus
import com.shikkhasathi.core.model.Page
import com.shikkhasathi.core.model.PayResponse
import com.shikkhasathi.core.model.PaymentMethod
import com.shikkhasathi.core.model.Playback
import com.shikkhasathi.core.model.PlayerChapter
import com.shikkhasathi.core.model.PlayerCourse
import com.shikkhasathi.core.model.PlayerLesson
import com.shikkhasathi.core.model.PlayerOutline
import com.shikkhasathi.core.model.PlayerSection
import com.shikkhasathi.core.model.ProgressBody
import com.shikkhasathi.core.model.ProgressResponse
import com.shikkhasathi.core.model.Question
import com.shikkhasathi.core.model.SaveAnswerBody
import com.shikkhasathi.core.model.SearchFilters
import com.shikkhasathi.core.model.SearchResponse
import com.shikkhasathi.core.model.Suggestion
import com.shikkhasathi.core.network.ApiException
import com.shikkhasathi.core.player.MediaPlayerFactory
import com.shikkhasathi.core.player.PlayerSnapshot
import com.shikkhasathi.core.player.SsMediaPlayer
import com.shikkhasathi.features.checkout.data.OrdersRepository
import com.shikkhasathi.features.exam.data.ExamRepository
import com.shikkhasathi.features.explore.data.CatalogRepository
import com.shikkhasathi.features.learning.data.LearningRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** uuid-looking test ids: `uid(7)` → `0190f0a3-0000-7000-8000-000000000007`. */
fun uid(n: Int): String = "0190f0a3-0000-7000-8000-${n.toString().padStart(12, '0')}"

fun card(
    n: Int,
    title: String = "Data Structures Midterm Crash — CSE-2203",
) = CourseCard(
    id = uid(n),
    slug = "ds-midterm-$n",
    title = title,
    courseCode = "CSE-2203",
    institutionShort = "MU",
    sellerName = "তানভীর হাসান",
    sellerVerified = true,
    examType = "midterm",
    price = 349,
    compareAtPrice = 499,
)

fun courseDetail(
    enrolled: Boolean = false,
    owner: Boolean = false,
    price: Int = 349,
    resume: String? = null,
) = CourseDetail(
    id = uid(100),
    slug = "ds-midterm-cse-2203",
    title = "Data Structures Midterm Crash — CSE-2203",
    courseCode = "CSE-2203",
    sellerName = "তানভীর হাসান",
    price = price,
    seller = CourseSeller(id = uid(900), slug = "tanvir", name = "তানভীর হাসান", verified = true),
    viewer = CourseViewer(enrolled = enrolled, isOwner = owner, firstLessonId = uid(201), resumeLessonId = resume, wishlisted = false),
)

class FakeCatalogRepository : CatalogRepository {
    val searches = mutableListOf<Pair<SearchFilters, String?>>()
    val suggestQueries = mutableListOf<String>()
    var pages: Map<String?, SearchResponse> = mapOf(null to SearchResponse(items = listOf(card(1), card(2)), total = 3, nextCursor = "c2"))
    var suggestions: List<Suggestion> = emptyList()
    var searchError: ApiException? = null
    var wishlistError: ApiException? = null
    val wishlistCalls = mutableListOf<Pair<String, Boolean>>()
    var detail: CourseDetail = courseDetail()

    /** When set, the next search suspends until completed (to test stale answers). */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun search(
        filters: SearchFilters,
        cursor: String?,
        limit: Int,
    ): SearchResponse {
        searches += filters to cursor
        gate?.let {
            gate = null
            it.await()
        }
        searchError?.let { throw it }
        return pages[cursor] ?: SearchResponse()
    }

    override suspend fun suggest(query: String): List<Suggestion> {
        suggestQueries += query
        return suggestions
    }

    override suspend fun course(slug: String): CourseDetail = detail

    override suspend fun setWishlisted(
        courseId: String,
        wishlisted: Boolean,
    ) {
        wishlistCalls += courseId to wishlisted
        wishlistError?.let { throw it }
    }
}

fun order(
    status: OrderStatus = OrderStatus.Pending,
    total: Int = 349,
    id: String = uid(500),
) = Order(
    id = id,
    status = status,
    items = listOf(OrderItem(id = uid(501), courseId = uid(100), title = "Data Structures Midterm Crash", price = 349)),
    subtotal = 349,
    total = total,
    firstLessonPath = if (status == OrderStatus.Paid) "/learn/${uid(100)}/${uid(201)}" else null,
)

class FakeOrdersRepository : OrdersRepository {
    val creates = mutableListOf<Triple<String, String?, String>>()
    val pays = mutableListOf<Pair<String, PaymentMethod>>()
    var created: Order = order()

    /** Answers of successive `GET /orders/:id`; the last one repeats. */
    var statuses: MutableList<OrderStatus> = mutableListOf(OrderStatus.Pending)
    var reads = 0
    var coupon: CouponValidateResponse = CouponValidateResponse(valid = true, discount = 50, total = 299, message = "৳৫০ ছাড়")
    var createError: ApiException? = null

    override suspend fun create(
        courseId: String,
        couponCode: String?,
        idempotencyKey: String,
    ): Order {
        creates += Triple(courseId, couponCode, idempotencyKey)
        createError?.let { throw it }
        return created
    }

    override suspend fun pay(
        orderId: String,
        method: PaymentMethod,
        phone: String?,
    ): PayResponse {
        pays += orderId to method
        return PayResponse(redirectUrl = "https://sandbox.gateway.test/pay/$orderId", gateway = "fake")
    }

    override suspend fun order(orderId: String): Order {
        val status = if (statuses.size > 1) statuses.removeAt(0) else statuses.first()
        reads++
        return created.copy(id = orderId, status = status, firstLessonPath = if (status.isPaid) "/learn/${uid(100)}/${uid(201)}" else null)
    }

    override suspend fun validateCoupon(
        code: String,
        courseId: String,
    ): CouponValidateResponse = coupon
}

class FakeBrowser(var opens: Boolean = true) : ExternalBrowser {
    val opened = mutableListOf<String>()

    override fun open(url: String): Boolean {
        opened += url
        return opens
    }
}

/** Controllable player: tests move the position and toggle playing like a real decoder would. */
class FakeMediaPlayer : SsMediaPlayer {
    private val state = MutableStateFlow(PlayerSnapshot(durationSec = 600.0))
    override val snapshot: StateFlow<PlayerSnapshot> = state
    val loads = mutableListOf<Pair<String, Int>>()
    var released = false

    override fun load(
        url: String,
        startAtSec: Int,
    ) {
        loads += url to startAtSec
        state.value = PlayerSnapshot(positionSec = startAtSec.toDouble(), durationSec = 600.0, speed = state.value.speed)
    }

    override fun play() = state.update { it.copy(isPlaying = true) }

    override fun pause() = state.update { it.copy(isPlaying = false) }

    override fun seekTo(positionSec: Double) = state.update { it.copy(positionSec = positionSec) }

    override fun setSpeed(speed: Float) = state.update { it.copy(speed = speed) }

    override fun release() {
        released = true
    }

    /** One second of playback at the current speed (tests call `runCurrent()` between steps, like real sampling). */
    fun step() = state.update { it.copy(positionSec = it.positionSec + it.speed) }

    fun factory() = MediaPlayerFactory { this }
}

fun lesson(
    n: Int,
    completed: Boolean = false,
    locked: Boolean = false,
    type: LessonType = LessonType.Video,
) = PlayerLesson(id = uid(200 + n), title = "লেসন $n", type = type, durationSec = 600, completed = completed, locked = locked)

fun outline(
    lessons: List<PlayerLesson> = listOf(lesson(1, completed = true), lesson(2), lesson(3, locked = true)),
    resume: String? = uid(202),
) = PlayerOutline(
    course = PlayerCourse(id = uid(100), slug = "ds-midterm-cse-2203", title = "Data Structures Midterm Crash", courseCode = "CSE-2203"),
    enrolled = true,
    progressPercent = 33,
    completedLessons = lessons.count { it.completed },
    totalLessons = lessons.size,
    resumeLessonId = resume,
    accessUntil = "2027-05-01T00:00:00.000Z",
    sections =
        listOf(
            PlayerSection(id = uid(300), title = "Trees", chapters = listOf(PlayerChapter(id = uid(301), title = "BST", lessons = lessons)))
        ),
)

@Suppress("TooManyFunctions")
class FakeLearningRepository : LearningRepository {
    var outlineValue: PlayerOutline = outline()
    val playbacks = mutableListOf<Pair<String, String>>()
    val progress = mutableListOf<Pair<String, ProgressBody>>()
    var progressAnswer: (
        ProgressBody
    ) -> ProgressResponse = { ProgressResponse(completed = false, progressPercent = 33, streamActive = true) }
    var startAt = 120
    var playbackError: ApiException? = null
    val completes = mutableListOf<Pair<String, Boolean>>()
    var courses: Map<MyCoursesFilter, List<MyCourse>> = emptyMap()
    val courseRequests = mutableListOf<MyCoursesFilter>()
    var notes = mutableListOf<Note>()
    val noteCalls = mutableListOf<String>()
    var noteError: ApiException? = null
    var questionsValue = mutableListOf<Question>()
    val asks = mutableListOf<Pair<String, Int?>>()
    var tokenCounter = 0

    override suspend fun myCourses(filter: MyCoursesFilter): List<MyCourse> {
        courseRequests += filter
        return courses[filter].orEmpty()
    }

    override suspend fun outline(courseId: String): PlayerOutline = outlineValue

    override suspend fun lesson(lessonId: String): LessonDetail {
        val l = outlineValue.lessons.first { it.id == lessonId }
        val i = outlineValue.lessons.indexOf(l)
        return LessonDetail(
            id = l.id,
            courseId = outlineValue.course.id,
            title = l.title,
            type = l.type,
            completed = l.completed,
            prevLessonId = outlineValue.lessons.getOrNull(i - 1)?.id,
            nextLessonId = outlineValue.lessons.getOrNull(i + 1)?.id,
        )
    }

    override suspend fun playback(
        lessonId: String,
        deviceId: String,
        deviceName: String?,
    ): Playback {
        playbacks += lessonId to deviceId
        playbackError?.let { throw it }
        tokenCounter++
        return Playback(
            url = "https://video.test/$lessonId.m3u8",
            streamToken = "st-$tokenCounter",
            watermark = "রাফি · 01712",
            startAtSec = startAt
        )
    }

    override suspend fun progress(
        lessonId: String,
        body: ProgressBody,
    ): ProgressResponse {
        progress += lessonId to body
        return progressAnswer(body)
    }

    override suspend fun complete(
        lessonId: String,
        completed: Boolean,
    ): ProgressResponse {
        completes += lessonId to completed
        return ProgressResponse(completed = completed, progressPercent = if (completed) 66 else 33)
    }

    override suspend fun documentUrl(documentId: String): DocumentUrl = DocumentUrl(url = "https://files.test/$documentId.pdf")

    override suspend fun notes(
        lessonId: String?,
        courseId: String?,
        query: String?,
        cursor: String?,
    ): Page<Note> {
        noteCalls += "list:${query.orEmpty()}"
        return Page(notes.filter { query.isNullOrBlank() || it.body.contains(query) })
    }

    override suspend fun addNote(
        lessonId: String,
        timestampSec: Int?,
        body: String,
    ): Note {
        noteCalls += "add:$timestampSec:$body"
        noteError?.let { throw it }
        return Note(id = uid(700 + notes.size), courseId = uid(100), lessonId = lessonId, timestampSec = timestampSec, body = body).also {
            notes +=
                it
        }
    }

    override suspend fun editNote(
        noteId: String,
        body: String,
    ): Note {
        noteCalls += "edit:$noteId"
        val n = notes.first { it.id == noteId }.copy(body = body)
        notes.replaceAll { if (it.id == noteId) n else it }
        return n
    }

    override suspend fun deleteNote(noteId: String) {
        noteCalls += "delete:$noteId"
        noteError?.let { throw it }
    }

    override suspend fun bookmarks(cursor: String?): Page<Bookmark> = Page()

    override suspend fun addBookmark(
        lessonId: String,
        timestampSec: Int?,
    ): Bookmark {
        noteCalls += "bookmark:$timestampSec"
        return Bookmark(id = uid(800), courseId = uid(100), lessonId = lessonId, timestampSec = timestampSec)
    }

    override suspend fun deleteBookmark(bookmarkId: String) = Unit

    override suspend fun questions(
        lessonId: String,
        cursor: String?,
    ): Page<Question> = Page(questionsValue)

    override suspend fun ask(
        lessonId: String,
        body: String,
        timestampSec: Int?,
    ): Question {
        asks += body to timestampSec
        return Question(id = uid(600), lessonId = lessonId, body = body, timestampSec = timestampSec, mine = true)
    }
}

fun planSummary(
    n: Int,
    past: Boolean = false,
    active: Boolean = true,
) = ExamPlanSummary(
    id = uid(400 + n),
    subjectName = "Data Structures",
    courseCode = "CSE-2203",
    examType = "midterm",
    examDate = "2026-11-20T10:00:00+06:00",
    daysLeft = 17,
    readiness = 40,
    active = active,
    past = past,
)

fun attempt(
    deadline: String = "2026-11-03T04:30:00Z",
    serverNow: String = "2026-11-03T04:00:00Z",
    answers: List<com.shikkhasathi.core.model.AttemptAnswer> = emptyList(),
    submitted: Boolean = false,
) = Attempt(
    id = uid(450),
    testId = uid(440),
    courseId = uid(100),
    title = "BST Mock Test",
    durationSec = 1800,
    startedAt = "2026-11-03T04:00:00Z",
    deadlineAt = deadline,
    serverNow = serverNow,
    submitted = submitted,
    questions =
        (1..3).map { q ->
            AttemptQuestion(
                id = uid(460 + q),
                body = "প্রশ্ন $q",
                options =
                    (1..4).map { o ->
                        AttemptOption(id = uid(470 + q * 10 + o), body = "অপশন $o")
                    }
            )
        },
    answers = answers,
)

@Suppress("TooManyFunctions")
class FakeExamRepository : ExamRepository {
    var plansValue: List<ExamPlanSummary> = listOf(planSummary(1, past = true), planSummary(2), planSummary(3))
    val planRequests = mutableListOf<String>()
    val created = mutableListOf<ExamPlanInput>()
    var cards: List<Flashcard> = (1..3).map { Flashcard(id = uid(480 + it), courseId = uid(100), front = "কার্ড $it", back = "উত্তর $it") }
    val reviews = mutableListOf<Pair<String, Boolean>>()
    var attemptValue: Attempt = attempt()
    val saves = mutableListOf<SaveAnswerBody>()
    var saveError: ApiException? = null
    var submits = 0
    var submitError: ApiException? = null

    override suspend fun plans(): List<ExamPlanSummary> = plansValue

    override suspend fun createPlan(input: ExamPlanInput): ExamPlanSummary {
        created += input
        val p = planSummary(9).copy(subjectName = input.subjectName)
        plansValue = plansValue + p
        return p
    }

    override suspend fun plan(planId: String): ExamPlanDetail {
        planRequests += planId
        val s = plansValue.first { it.id == planId }
        return ExamPlanDetail(id = s.id, subjectName = s.subjectName, examDate = s.examDate, daysLeft = s.daysLeft, readiness = s.readiness)
    }

    override suspend fun flashcards(planId: String): List<Flashcard> = cards

    override suspend fun reviewCard(
        cardId: String,
        known: Boolean,
    ) {
        reviews += cardId to known
    }

    override suspend fun mockTests(): List<MockSummary> =
        listOf(
            MockSummary(id = uid(440), courseId = uid(100), title = "BST Mock Test", questionCount = 3)
        )

    override suspend fun startAttempt(testId: String): Attempt = attemptValue

    override suspend fun attempt(attemptId: String): Attempt = attemptValue

    override suspend fun saveAnswer(
        attemptId: String,
        body: SaveAnswerBody,
    ) {
        saves += body
        saveError?.let { throw it }
    }

    override suspend fun submit(attemptId: String): AttemptResult {
        submits++
        submitError?.let { throw it }
        return AttemptResult(attemptId = attemptId, testId = uid(440), courseId = uid(100), title = "BST Mock Test", score = 67)
    }

    override suspend fun result(attemptId: String): AttemptResult =
        AttemptResult(
            attemptId = attemptId,
            testId = uid(440),
            courseId = uid(100),
            title = "BST Mock Test",
            score = 67,
            correct = 2,
            wrong = 1,
            total = 3
        )
}
