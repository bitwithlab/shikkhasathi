package com.shikkhasathi.features.classroom.data

import com.shikkhasathi.core.model.Assignment
import com.shikkhasathi.core.model.AssignmentFilter
import com.shikkhasathi.core.model.CalendarEvent
import com.shikkhasathi.core.model.ClassCourse
import com.shikkhasathi.core.model.ClassroomStatus
import com.shikkhasathi.core.model.ClientPlatform
import com.shikkhasathi.core.model.ConnectBody
import com.shikkhasathi.core.model.FeedItem
import com.shikkhasathi.core.model.ItemsResponse
import com.shikkhasathi.core.model.MarkReadBody
import com.shikkhasathi.core.model.OkResponse
import com.shikkhasathi.core.model.Page
import com.shikkhasathi.core.model.StreamResponse
import com.shikkhasathi.core.network.ApiClient
import com.shikkhasathi.core.network.segment
import com.shikkhasathi.core.network.withQuery
import kotlin.time.Instant

/**
 * Google Classroom, read-only, through our API (`/api/v1/classroom/...`, contracts/classroom.ts).
 * Tokens never reach the app: mobile only hands the server a one-time auth code ([connect]).
 * Every method throws `ApiException` on failure (CLASSROOM_REAUTH, CLASSROOM_WORKSPACE_BLOCKED, CLASSROOM_QUOTA,
 * RATE_LIMITED with `details.retryAfterSec`, NETWORK …).
 */
interface ClassroomRepository {
    suspend fun status(): ClassroomStatus

    /** `POST /classroom/connect` with the server auth code from [GoogleAuthorization]; starts the full sync. */
    suspend fun connect(
        code: String,
        platform: ClientPlatform,
    ): ClassroomStatus

    /** Manual refresh, rate-limited server-side to once a minute. */
    suspend fun sync(): ClassroomStatus

    /** Revokes at Google and deletes every cached Classroom row server-side. */
    suspend fun disconnect()

    suspend fun courses(): List<ClassCourse>

    suspend fun stream(courseId: String): StreamResponse

    suspend fun assignments(
        filter: AssignmentFilter = AssignmentFilter.All,
        courseId: String? = null,
        from: Instant? = null,
        to: Instant? = null,
    ): List<Assignment>

    suspend fun assignment(id: String): Assignment

    suspend fun calendar(
        from: Instant,
        to: Instant,
    ): List<CalendarEvent>

    suspend fun feed(
        cursor: String? = null,
        limit: Int = DEFAULT_PAGE,
        courseId: String? = null,
        unreadOnly: Boolean = false,
    ): Page<FeedItem>

    /** Marks [ids] read, or every feed item when [all]. */
    suspend fun markFeedRead(
        ids: List<String> = emptyList(),
        all: Boolean = false,
    )

    companion object {
        const val DEFAULT_PAGE = 20
    }
}

class ApiClassroomRepository(private val api: ApiClient) : ClassroomRepository {
    override suspend fun status(): ClassroomStatus = api.get("classroom/status")

    override suspend fun connect(
        code: String,
        platform: ClientPlatform,
    ): ClassroomStatus = api.post("classroom/connect", ConnectBody(code = code, platform = platform))

    override suspend fun sync(): ClassroomStatus = api.postEmpty("classroom/sync")

    override suspend fun disconnect() {
        api.delete<OkResponse>("classroom/connection")
    }

    override suspend fun courses(): List<ClassCourse> = api.get<ItemsResponse<ClassCourse>>("classroom/courses").items

    override suspend fun stream(courseId: String): StreamResponse = api.get("classroom/courses/${segment(courseId)}/stream")

    override suspend fun assignments(
        filter: AssignmentFilter,
        courseId: String?,
        from: Instant?,
        to: Instant?,
    ): List<Assignment> =
        api
            .get<ItemsResponse<Assignment>>(
                withQuery("classroom/assignments", "status" to filter.wire, "courseId" to courseId, "from" to from, "to" to to),
            ).items

    override suspend fun assignment(id: String): Assignment = api.get("classroom/assignments/${segment(id)}")

    override suspend fun calendar(
        from: Instant,
        to: Instant,
    ): List<CalendarEvent> = api.get<ItemsResponse<CalendarEvent>>(withQuery("classroom/calendar", "from" to from, "to" to to)).items

    override suspend fun feed(
        cursor: String?,
        limit: Int,
        courseId: String?,
        unreadOnly: Boolean,
    ): Page<FeedItem> =
        api.get(
            withQuery(
                "classroom/feed",
                "cursor" to cursor,
                "limit" to limit,
                "courseId" to courseId,
                "unread" to if (unreadOnly) true else null,
            ),
        )

    override suspend fun markFeedRead(
        ids: List<String>,
        all: Boolean,
    ) {
        api.post<MarkReadBody, OkResponse>("classroom/feed/read", if (all) MarkReadBody(all = true) else MarkReadBody(ids = ids))
    }
}
