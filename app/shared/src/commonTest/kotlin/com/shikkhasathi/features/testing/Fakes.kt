package com.shikkhasathi.features.testing

import com.shikkhasathi.core.model.Assignment
import com.shikkhasathi.core.model.AssignmentFilter
import com.shikkhasathi.core.model.CalendarEvent
import com.shikkhasathi.core.model.ClassCourse
import com.shikkhasathi.core.model.ClassroomStatus
import com.shikkhasathi.core.model.ClientPlatform
import com.shikkhasathi.core.model.Dashboard
import com.shikkhasathi.core.model.FeedItem
import com.shikkhasathi.core.model.NotificationItem
import com.shikkhasathi.core.model.Page
import com.shikkhasathi.core.model.PreferenceRow
import com.shikkhasathi.core.model.StreamResponse
import com.shikkhasathi.core.network.ApiError
import com.shikkhasathi.core.network.ApiException
import com.shikkhasathi.core.network.SsJson
import com.shikkhasathi.features.classroom.data.ClassroomRepository
import com.shikkhasathi.features.home.data.CachedDashboard
import com.shikkhasathi.features.home.data.DashboardRepository
import com.shikkhasathi.features.notifications.data.NotificationsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

fun dashboardFixture(json: String = DASHBOARD_JSON): Dashboard = SsJson.decodeFromString(Dashboard.serializer(), json)

fun apiError(
    code: String,
    status: Int = 409,
    retryAfterSec: Int? = null,
) = ApiException(
    ApiError(
        code = code,
        message = "সার্ভার বার্তা",
        status = status,
        details = retryAfterSec?.let { JsonObject(mapOf("retryAfterSec" to JsonPrimitive(it))) },
    ),
)

val networkError get() = ApiException(ApiError.network())

class FakeDashboardRepository(
    var cachedEntry: CachedDashboard? = null,
    var next: () -> Dashboard = { dashboardFixture() },
) : DashboardRepository {
    val fetches = mutableListOf<Boolean>()

    /** When set, fetch suspends until completed (to observe the cached-first paint). */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun cached(): CachedDashboard? = cachedEntry

    override suspend fun fetch(fresh: Boolean): Dashboard {
        fetches += fresh
        gate?.await()
        return next()
    }

    override suspend fun clearCache() {
        cachedEntry = null
    }
}

open class FakeClassroomRepository : ClassroomRepository {
    var statusValue = ClassroomStatus(connected = true, lastSyncAt = "2026-11-03T03:58:00.000Z")
    var syncError: ApiException? = null
    var connectError: ApiException? = null
    var markReadError: ApiException? = null
    var assignmentsValue: List<Assignment> = emptyList()
    var calendarValue: List<CalendarEvent> = emptyList()
    var calendarError: ApiException? = null
    val calls = mutableListOf<String>()
    val calendarRanges = mutableListOf<Pair<Instant, Instant>>()

    override suspend fun status(): ClassroomStatus = statusValue

    override suspend fun connect(
        code: String,
        platform: ClientPlatform,
    ): ClassroomStatus {
        calls += "connect:$code:${platform.name}"
        connectError?.let { throw it }
        return statusValue
    }

    override suspend fun sync(): ClassroomStatus {
        calls += "sync"
        syncError?.let { throw it }
        return statusValue
    }

    override suspend fun disconnect() {
        calls += "disconnect"
    }

    override suspend fun courses(): List<ClassCourse> = emptyList()

    override suspend fun stream(courseId: String): StreamResponse = error("not used")

    override suspend fun assignments(
        filter: AssignmentFilter,
        courseId: String?,
        from: Instant?,
        to: Instant?,
    ): List<Assignment> {
        calls += "assignments:${filter.wire}"
        return assignmentsValue
    }

    override suspend fun assignment(id: String): Assignment = assignmentsValue.first { it.id == id }

    override suspend fun calendar(
        from: Instant,
        to: Instant,
    ): List<CalendarEvent> {
        calendarRanges += from to to
        calendarError?.let { throw it }
        return calendarValue
    }

    override suspend fun feed(
        cursor: String?,
        limit: Int,
        courseId: String?,
        unreadOnly: Boolean,
    ): Page<FeedItem> = Page()

    override suspend fun markFeedRead(
        ids: List<String>,
        all: Boolean,
    ) {
        calls += if (all) "feedRead:all" else "feedRead:${ids.joinToString()}"
        markReadError?.let { throw it }
    }
}

class FakeNotificationsRepository(
    private val pages: Map<String?, Page<NotificationItem>>,
) : NotificationsRepository {
    val requestedCursors = mutableListOf<String?>()
    val markReadCalls = mutableListOf<String>()
    var markReadError: ApiException? = null
    var listError: ApiException? = null

    override suspend fun list(
        cursor: String?,
        limit: Int,
        unreadOnly: Boolean,
    ): Page<NotificationItem> {
        requestedCursors += cursor
        listError?.let { throw it }
        return pages[cursor] ?: Page()
    }

    override suspend fun markRead(
        ids: List<String>,
        all: Boolean,
    ) {
        markReadCalls += if (all) "all" else ids.joinToString()
        markReadError?.let { throw it }
    }

    override suspend fun unreadCount(): Int = 0

    override suspend fun preferences(): List<PreferenceRow> = emptyList()

    override suspend fun updatePreferences(items: List<PreferenceRow>): List<PreferenceRow> = items

    val pushCalls = mutableListOf<String>()
    var pushError: ApiException? = null

    override suspend fun registerPush(
        platform: ClientPlatform,
        token: String,
    ) {
        pushCalls += "register:${platform.name}:$token"
        pushError?.let { throw it }
    }

    override suspend fun unregisterPush(token: String) {
        pushCalls += "unregister:$token"
    }
}
