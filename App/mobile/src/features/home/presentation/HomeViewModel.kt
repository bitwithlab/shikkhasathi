@file:Suppress("TooManyFunctions") // HomeViewModel + the pure DTO → UI mappers of its seven widgets

package com.shikkhasathi.features.home.presentation

import com.shikkhasathi.core.common.SsViewModel
import com.shikkhasathi.core.common.time.dhakaDate
import com.shikkhasathi.core.common.time.syncLabel
import com.shikkhasathi.core.common.time.toInstantOrNull
import com.shikkhasathi.core.common.time.toLocalDateOrNull
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.i18n.asUiText
import com.shikkhasathi.core.model.ClassroomConnectionStatus
import com.shikkhasathi.core.model.ClassroomStatus
import com.shikkhasathi.core.model.ContinueWidget
import com.shikkhasathi.core.model.Dashboard
import com.shikkhasathi.core.model.DueGroupKey
import com.shikkhasathi.core.model.ExamWidget
import com.shikkhasathi.core.model.Me
import com.shikkhasathi.core.model.RecommendedCourse
import com.shikkhasathi.core.model.Section
import com.shikkhasathi.core.network.ApiError
import com.shikkhasathi.core.network.ApiException
import com.shikkhasathi.features.auth.data.AuthRepository
import com.shikkhasathi.features.auth.data.SessionState
import com.shikkhasathi.features.auth.presentation.toUiText
import com.shikkhasathi.features.classroom.data.ClassroomRepository
import com.shikkhasathi.features.classroom.presentation.AssignmentRowUi
import com.shikkhasathi.features.classroom.presentation.FeedRowUi
import com.shikkhasathi.features.classroom.presentation.quotaText
import com.shikkhasathi.features.classroom.presentation.toRowUi
import com.shikkhasathi.features.home.data.DashboardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * State of one dashboard widget. Each widget is independent (server `Section`): one failing shows its own
 * compact error card and never blanks the screen.
 */
sealed interface Widget<out T> {
    data object Loading : Widget<Nothing>

    /** Not applicable right now (e.g. Classroom widgets while not connected). */
    data object Hidden : Widget<Nothing>

    data object Failed : Widget<Nothing>

    data class Ready<T>(val data: T) : Widget<T>
}

/** What the Classroom card at the top shows. [None] = connected and healthy, so no card. */
enum class ClassroomCard { None, NotConnected, NeedsReauth, WorkspaceBlocked }

data class DueGroupUi(
    val key: DueGroupKey,
    val label: Str,
    val rows: List<AssignmentRowUi>,
)

data class DueSoonUi(
    val groups: List<DueGroupUi>,
    val total: Int,
    val shown: Int,
) {
    val hasMore: Boolean get() = total > shown
    val isEmpty: Boolean get() = shown == 0
}

data class FeedUi(
    val items: List<FeedRowUi>,
    val unread: Int,
)

data class WeekDayUi(
    val date: LocalDate,
    val weekday: UiText,
    val dayOfMonth: Int,
    val deadlines: Int,
    val exams: Int,
    val isToday: Boolean,
    /** TalkBack: "বৃহস্পতিবার: ২টি ডেডলাইন, ০টি পরীক্ষা". */
    val description: UiText,
)

data class HomeHeaderUi(
    /** First name for the greeting, or null to use the generic "শিক্ষার্থী". */
    val firstName: String? = null,
    /** "MU · CSE · সেমিস্টার ৫" parts; empty until onboarding is done. */
    val academicParts: List<UiText> = emptyList(),
    val unreadNotifications: Int = 0,
)

data class HomeUiState(
    val header: HomeHeaderUi = HomeHeaderUi(),
    val classroomCard: ClassroomCard = ClassroomCard.None,
    val classroomConnected: Boolean = false,
    /** "সিঙ্ক হয়েছে ২ মিনিট আগে"; null when not connected. */
    val syncLabel: UiText? = null,
    val dueSoon: Widget<DueSoonUi> = Widget.Loading,
    val feed: Widget<FeedUi> = Widget.Loading,
    /** Ready(null) = no exam set → "সামনের পরীক্ষার তারিখ দিন" prompt. */
    val exam: Widget<ExamWidget?> = Widget.Loading,
    /** Ready(null) = no course yet → placeholder with "কোর্স খুঁজুন". */
    val continueLearning: Widget<ContinueWidget?> = Widget.Loading,
    val week: Widget<List<WeekDayUi>> = Widget.Loading,
    val recommendations: Widget<List<RecommendedCourse>> = Widget.Loading,
    /** Pull-to-refresh / sync in flight. */
    val refreshing: Boolean = false,
    /** Showing the cached copy because the network load failed. */
    val offline: Boolean = false,
    /** Nothing cached and the first load failed: full-screen error with retry. */
    val error: UiText? = null,
    /** One-off message (snackbar), consumed with [HomeViewModel.messageShown]. */
    val message: UiText? = null,
) {
    /** True until there is something (cached or fresh) to draw: the screen shows its skeleton. */
    val loading: Boolean get() = dueSoon == Widget.Loading && week == Widget.Loading && error == null
}

@Suppress("TooManyFunctions")
class HomeViewModel(
    private val dashboard: DashboardRepository,
    private val classroom: ClassroomRepository,
    auth: AuthRepository,
    private val clock: Clock = Clock.System,
) : SsViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    private var last: Dashboard? = null

    init {
        // Greeting from the signed-in profile while the first dashboard load is in flight.
        (auth.session.value as? SessionState.SignedIn)?.user?.let { me ->
            mutableState.update { it.copy(header = me.toHeaderUi()) }
        }
        load(fresh = false, useCache = true)
    }

    fun retry() = load(fresh = false, useCache = false)

    /** Re-renders time labels ("২ মিনিট আগে"); the screen calls it once a minute while visible. */
    fun tick() {
        last?.let { apply(it) }
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    /**
     * Pull-to-refresh: `POST /classroom/sync` (when connected), then a fresh `GET /dashboard`.
     * A sync refusal (rate limit, quota) still reloads, and explains itself in Bangla.
     */
    fun refresh() {
        if (mutableState.value.refreshing) return
        mutableState.update { it.copy(refreshing = true) }
        scope.launch {
            var note: UiText? = null
            if (mutableState.value.classroomConnected) {
                try {
                    classroom.sync()
                } catch (e: ApiException) {
                    note = syncFailureText(e.error)
                }
            }
            val failure = fetch(fresh = true)
            mutableState.update { it.copy(refreshing = false, message = note ?: failure) }
        }
    }

    /** "সব পড়া হয়েছে": optimistic, reverted with a message if the server refuses. */
    fun markAllFeedRead() {
        val before = mutableState.value.feed as? Widget.Ready ?: return
        if (before.data.unread == 0 && before.data.items.none { it.unread }) return
        setFeed(Widget.Ready(FeedUi(before.data.items.map { it.copy(unread = false) }, unread = 0)))
        scope.launch {
            try {
                classroom.markFeedRead(all = true)
                last = last?.markFeedRead { true }
            } catch (e: ApiException) {
                setFeed(before)
                mutableState.update { it.copy(message = e.error.toUiText()) }
            }
        }
    }

    /** Tapping a feed row marks just that item read (fire-and-forget; the next load corrects any drift). */
    fun openFeedItem(id: String) {
        val current = mutableState.value.feed as? Widget.Ready ?: return
        val row = current.data.items.firstOrNull { it.id == id } ?: return
        if (!row.unread) return
        setFeed(
            Widget.Ready(
                FeedUi(
                    items = current.data.items.map { if (it.id == id) it.copy(unread = false) else it },
                    unread = (current.data.unread - 1).coerceAtLeast(0),
                ),
            ),
        )
        scope.launch {
            runCatching { classroom.markFeedRead(ids = listOf(id)) }
                .onSuccess { last = last?.markFeedRead { it == id } }
        }
    }

    private fun setFeed(feed: Widget<FeedUi>) = mutableState.update { it.copy(feed = feed) }

    private fun load(
        fresh: Boolean,
        useCache: Boolean,
    ) {
        mutableState.update { it.copy(error = null) }
        scope.launch {
            if (useCache) {
                runCatching { dashboard.cached() }.getOrNull()?.let { apply(it.dashboard) }
            }
            fetch(fresh)?.let { failure ->
                if (last == null) mutableState.update { it.copy(error = failure) }
            }
        }
    }

    /** Network load. Returns the failure text, or null on success. With cached data shown, a failure flips [HomeUiState.offline]. */
    private suspend fun fetch(fresh: Boolean): UiText? =
        try {
            apply(dashboard.fetch(fresh))
            mutableState.update { it.copy(offline = false) }
            null
        } catch (e: ApiException) {
            val text = e.error.toUiText()
            if (last != null) mutableState.update { it.copy(offline = e.error.isNetwork) }
            text
        }

    private fun syncFailureText(error: ApiError): UiText {
        val lastSync =
            last
                ?.header
                ?.classroom
                ?.lastSyncAt
                .toInstantOrNull()
        return when (error.code) {
            ApiError.RATE_LIMITED -> Str.GcSyncRateLimited.asUiText()
            ApiError.CLASSROOM_QUOTA -> quotaText(lastSync, clock.now())
            ApiError.CLASSROOM_REAUTH -> Str.GcReauthTitle.asUiText()
            ApiError.CLASSROOM_WORKSPACE_BLOCKED -> Str.GcBlockedTitle.asUiText()
            else -> error.toUiText()
        }
    }

    private fun apply(d: Dashboard) {
        last = d
        val now = clock.now()
        val status = d.header.classroom
        val card = status.toCard()
        val connected = card == ClassroomCard.None
        mutableState.update {
            it.copy(
                header = d.toHeaderUi(),
                classroomCard = card,
                classroomConnected = connected,
                syncLabel =
                    if (status.connected) {
                        syncLabel(status.lastSyncAt.toInstantOrNull(), now, syncing = status.status == ClassroomConnectionStatus.Syncing)
                    } else {
                        null
                    },
                dueSoon = d.dueSoon.toWidget { due -> due.toUi(now) },
                feed = d.feed.toWidget { f -> FeedUi(f.items.map { row -> row.toRowUi(now) }, f.unread) },
                exam = d.exam.toNullableWidget(),
                continueLearning = d.continueLearning.toNullableWidget(),
                week = d.week.toWidget { days -> days.mapNotNull { day -> day.toUi(now) } },
                recommendations = d.recommendations.toWidget { list -> list },
            )
        }
    }

    companion object {
        /** Home shows at most this many due rows, then "সব দেখুন" (spec §0A). */
        const val MAX_DUE_ROWS = 6
    }
}

private fun ClassroomStatus.toCard(): ClassroomCard =
    when {
        status == ClassroomConnectionStatus.NeedsReauth -> ClassroomCard.NeedsReauth
        status == ClassroomConnectionStatus.WorkspaceBlocked -> ClassroomCard.WorkspaceBlocked
        !connected -> ClassroomCard.NotConnected
        else -> ClassroomCard.None
    }

/** error → Failed; data null → Hidden (the server sends null for Classroom widgets while not connected). */
private inline fun <T, R> Section<T>.toWidget(map: (T) -> R): Widget<R> =
    when {
        error != null -> Widget.Failed
        data == null -> Widget.Hidden
        else -> Widget.Ready(map(data))
    }

private fun <T> Section<T>.toNullableWidget(): Widget<T?> = if (error != null) Widget.Failed else Widget.Ready(data)

private val GROUP_ORDER = listOf(DueGroupKey.Overdue, DueGroupKey.Today, DueGroupKey.Tomorrow, DueGroupKey.ThisWeek)

private fun DueGroupKey.label(): Str =
    when (this) {
        DueGroupKey.Overdue -> Str.DueOverdue
        DueGroupKey.Today -> Str.DueToday
        DueGroupKey.Tomorrow -> Str.DueTomorrow
        DueGroupKey.ThisWeek -> Str.DueThisWeek
    }

private fun com.shikkhasathi.core.model.DueSoon.toUi(now: Instant): DueSoonUi {
    var budget = HomeViewModel.MAX_DUE_ROWS
    val ordered = groups.sortedBy { GROUP_ORDER.indexOf(it.key) }
    val out =
        ordered.mapNotNull { group ->
            val rows = group.items.take(budget).map { it.toRowUi(now) }
            budget -= rows.size
            rows.takeIf { it.isNotEmpty() }?.let { DueGroupUi(group.key, group.key.label(), it) }
        }
    val shown = out.sumOf { it.rows.size }
    return DueSoonUi(groups = out, total = maxOf(total, groups.sumOf { it.items.size }), shown = shown)
}

private fun com.shikkhasathi.core.model.WeekDay.toUi(now: Instant): WeekDayUi? {
    val day = date.toLocalDateOrNull() ?: return null
    val weekday = UiText.Weekday(day.dayOfWeek.isoDayNumber)
    return WeekDayUi(
        date = day,
        weekday = UiText.Weekday(day.dayOfWeek.isoDayNumber, short = true),
        dayOfMonth = day.day,
        deadlines = deadlines,
        exams = exams,
        isToday = day == now.dhakaDate(),
        description =
            Str.CalendarDayA11y.asUiText(
                "date" to weekday,
                "summary" to Str.WeekSummary.asUiText("d" to deadlines, "e" to exams),
            ),
    )
}

private fun Dashboard.markFeedRead(which: (String) -> Boolean): Dashboard {
    val f = feed.data ?: return this
    val items = f.items.map { if (which(it.id)) it.copy(read = true) else it }
    return copy(feed = feed.copy(data = f.copy(items = items, unread = items.count { !it.read })))
}

private fun firstName(name: String): String? =
    name
        .trim()
        .split(Regex("\\s+"))
        .firstOrNull()
        ?.takeIf { it.isNotEmpty() }

private fun academicParts(
    institution: String?,
    department: String?,
    semester: Int?,
): List<UiText> =
    listOfNotNull(
        institution?.let { UiText.Raw(it) },
        department?.let { UiText.Raw(it) },
        semester?.let { Str.HomeSemester.asUiText("n" to it) },
    )

private fun Dashboard.toHeaderUi(): HomeHeaderUi =
    HomeHeaderUi(
        firstName = firstName(header.name),
        academicParts = academicParts(header.institutionShort, header.departmentShort, header.semester),
        unreadNotifications = header.unreadNotifications,
    )

internal fun Me.toHeaderUi(): HomeHeaderUi =
    HomeHeaderUi(
        firstName = firstName(name),
        academicParts =
            academicParts(
                academic.institutionShort ?: academic.institutionName,
                academic.departmentShort ?: academic.departmentName,
                academic.semester,
            ),
    )
