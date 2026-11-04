package com.shikkhasathi.features.notifications.presentation

import com.shikkhasathi.core.common.SsViewModel
import com.shikkhasathi.core.common.time.relativeTime
import com.shikkhasathi.core.common.time.toInstantOrNull
import com.shikkhasathi.core.i18n.Str
import com.shikkhasathi.core.i18n.UiText
import com.shikkhasathi.core.i18n.asUiText
import com.shikkhasathi.core.model.NotificationItem
import com.shikkhasathi.core.network.ApiException
import com.shikkhasathi.features.auth.presentation.toUiText
import com.shikkhasathi.features.notifications.data.NotificationsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/** Icon family of a notification, from its `type` prefix / name. */
enum class NotificationCategory { Assignment, Material, Announcement, Grade, Reminder, Exam, Course, Order, Other }

private val COURSE_PREFIXES = listOf("course.", "request.", "seller.")

fun categoryOf(type: String): NotificationCategory =
    when (type) {
        "classroom.assignment_new" -> NotificationCategory.Assignment
        "classroom.material_new" -> NotificationCategory.Material
        "classroom.announcement", "admin.broadcast" -> NotificationCategory.Announcement
        "classroom.graded" -> NotificationCategory.Grade
        "classroom.due_24h", "classroom.due_2h", "classroom.overdue" -> NotificationCategory.Reminder
        "exam.daily_plan" -> NotificationCategory.Exam
        "order.paid", "order.refunded", "seller.payout_paid" -> NotificationCategory.Order
        else -> if (COURSE_PREFIXES.any { type.startsWith(it) }) NotificationCategory.Course else NotificationCategory.Other
    }

data class NotificationRowUi(
    val id: String,
    val category: NotificationCategory,
    val title: String,
    val body: String,
    val timeAgo: UiText,
    val unread: Boolean,
    /** App/web path to open (`/classroom/assignments/…`), or null. */
    val link: String?,
)

data class NotificationsUiState(
    val items: List<NotificationRowUi> = emptyList(),
    /** First page loading (skeleton). */
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    /** First page failed: full error state with retry. */
    val error: UiText? = null,
    /** Next page or mark-read failed: inline message, list stays. */
    val message: UiText? = null,
) {
    val unreadCount: Int get() = items.count { it.unread }
    val isEmpty: Boolean get() = !loading && error == null && items.isEmpty()
    val canLoadMore: Boolean get() = !(loading || loadingMore || endReached) && error == null
}

/** Notification center: cursor paging (`GET /notifications?cursor`), tap → mark one read, "সব পড়া হয়েছে". */
class NotificationsViewModel(
    private val repository: NotificationsRepository,
    private val clock: Clock = Clock.System,
    private val pageSize: Int = NotificationsRepository.DEFAULT_PAGE,
) : SsViewModel() {
    private val mutableState = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = mutableState.asStateFlow()

    private var cursor: String? = null
    private val raw = mutableListOf<NotificationItem>()

    init {
        loadFirst()
    }

    fun retry() = loadFirst()

    fun refresh() = loadFirst(keepItems = true)

    fun messageShown() = mutableState.update { it.copy(message = null) }

    /** Called when the list nears its end. No-op while loading or after the last page. */
    fun loadMore() {
        if (!state.value.canLoadMore) return
        mutableState.update { it.copy(loadingMore = true) }
        scope.launch {
            try {
                val page = repository.list(cursor = cursor, limit = pageSize)
                append(page.items, page.nextCursor)
                mutableState.update { it.copy(loadingMore = false) }
            } catch (e: ApiException) {
                mutableState.update { it.copy(loadingMore = false, message = e.error.toUiText()) }
            }
        }
    }

    fun markAllRead() {
        if (state.value.unreadCount == 0) return
        val before = raw.toList()
        replaceAll { it.copy(read = true) }
        scope.launch {
            try {
                repository.markRead(all = true)
            } catch (e: ApiException) {
                raw.clear()
                raw += before
                publish()
                mutableState.update { it.copy(message = e.error.toUiText()) }
            }
        }
    }

    /** Tap on a row: mark it read (optimistic, silent on failure — the next load shows the truth). */
    fun open(id: String) {
        if (raw.none { it.id == id && !it.read }) return
        replaceAll { if (it.id == id) it.copy(read = true) else it }
        scope.launch { runCatching { repository.markRead(ids = listOf(id)) } }
    }

    private fun loadFirst(keepItems: Boolean = false) {
        mutableState.update { it.copy(loading = !keepItems || raw.isEmpty(), error = null, endReached = false) }
        scope.launch {
            try {
                val page = repository.list(cursor = null, limit = pageSize)
                raw.clear()
                cursor = null
                append(page.items, page.nextCursor)
                mutableState.update { it.copy(loading = false) }
            } catch (e: ApiException) {
                if (raw.isEmpty()) {
                    mutableState.update { it.copy(loading = false, error = e.error.toUiText()) }
                } else {
                    mutableState.update { it.copy(loading = false, message = e.error.toUiText()) }
                }
            }
        }
    }

    private fun append(
        items: List<NotificationItem>,
        next: String?,
    ) {
        val known = raw.mapTo(HashSet()) { it.id }
        raw += items.filter { it.id !in known } // pages can overlap when new notifications arrive meanwhile
        cursor = next
        mutableState.update { it.copy(endReached = next == null) }
        publish()
    }

    private fun replaceAll(transform: (NotificationItem) -> NotificationItem) {
        val updated = raw.map(transform)
        raw.clear()
        raw += updated
        publish()
    }

    private fun publish() {
        val now = clock.now()
        mutableState.update { s -> s.copy(items = raw.map { it.toRowUi(now) }) }
    }
}

private fun NotificationItem.toRowUi(now: Instant): NotificationRowUi =
    NotificationRowUi(
        id = id,
        category = categoryOf(type),
        title = title,
        body = body,
        timeAgo = createdAt.toInstantOrNull()?.let { relativeTime(it, now) } ?: Str.TimeJustNow.asUiText(),
        unread = !read,
        link = link,
    )
