package com.shikkhasathi.features.home

import com.shikkhasathi.core.datastore.InMemoryTokenStore
import com.shikkhasathi.core.datastore.StoredSession
import com.shikkhasathi.core.model.AssignmentFilter
import com.shikkhasathi.core.model.AssignmentStatus
import com.shikkhasathi.core.model.ClassroomConnectionStatus
import com.shikkhasathi.core.model.ClientPlatform
import com.shikkhasathi.core.model.Compatibility
import com.shikkhasathi.core.model.Dashboard
import com.shikkhasathi.core.model.DueGroupKey
import com.shikkhasathi.core.model.FeedKind
import com.shikkhasathi.core.model.MaterialKind
import com.shikkhasathi.core.network.ApiClient
import com.shikkhasathi.core.network.ApiConfig
import com.shikkhasathi.core.network.ApiError
import com.shikkhasathi.core.network.ApiException
import com.shikkhasathi.core.network.SsJson
import com.shikkhasathi.core.network.retryAfterSec
import com.shikkhasathi.features.classroom.data.ApiClassroomRepository
import com.shikkhasathi.features.home.data.ApiDashboardRepository
import com.shikkhasathi.features.home.data.InMemoryDashboardCache
import com.shikkhasathi.features.testing.DASHBOARD_JSON
import com.shikkhasathi.features.testing.DASHBOARD_NOT_CONNECTED_JSON
import com.shikkhasathi.features.testing.FIXTURE_NOW
import com.shikkhasathi.features.testing.FixedClock
import com.shikkhasathi.features.testing.assignment
import com.shikkhasathi.features.testing.dashboardFixture
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class DashboardDecodingTest {
    @Test
    fun decodesRealisticDashboard() {
        val d = dashboardFixture()
        assertEquals("রাফি আহমেদ", d.header.name)
        assertEquals(5, d.header.semester)
        assertEquals(ClassroomConnectionStatus.Active, d.header.classroom.status)
        assertEquals("2026-11-03T03:58:00.000Z", d.header.classroom.lastSyncAt)

        val due = assertNotNull(d.dueSoon.data)
        assertEquals(listOf(DueGroupKey.Overdue, DueGroupKey.Today, DueGroupKey.Tomorrow, DueGroupKey.ThisWeek), due.groups.map { it.key })
        assertEquals(AssignmentStatus.Missing, due.groups[0].items[0].status)
        assertEquals(AssignmentStatus.TurnedIn, due.groups[1].items[1].status)
        assertEquals(20.0, due.groups[0].items[0].maxPoints)
        assertEquals(7, due.total)

        val feed = assertNotNull(d.feed.data)
        assertEquals(FeedKind.Material, feed.items[0].kind)
        assertEquals(
            MaterialKind.Drive,
            feed.items[0]
                .materials
                .single()
                .kind
        )
        assertEquals(18.0, feed.items[1].grade?.assigned)

        assertEquals(12, d.exam.data?.daysLeft)
        assertNull(d.continueLearning.data)
        assertNull(d.continueLearning.error)
        assertEquals(7, d.week.data?.size)
        assertNull(d.recommendations.data)
        assertEquals("unavailable", d.recommendations.error)
    }

    @Test
    fun notConnectedAndUnknownEnumValuesDecode() {
        val d = dashboardFixture(DASHBOARD_NOT_CONNECTED_JSON)
        assertFalse(d.header.classroom.connected)
        assertNull(d.dueSoon.data)
        assertEquals(emptyList(), d.recommendations.data)

        // A status the app doesn't know yet falls back instead of failing the whole dashboard.
        val future = assignment("cw-x", "New kind", null, "reclaimed")
        val a =
            SsJson.decodeFromString(
                com.shikkhasathi.core.model.Assignment
                    .serializer(),
                future
            )
        assertEquals(AssignmentStatus.Pending, a.status)
    }

    @Test
    fun roundTripsThroughTheCacheFormat() {
        val d = dashboardFixture()
        val again = SsJson.decodeFromString(Dashboard.serializer(), SsJson.encodeToString(Dashboard.serializer(), d))
        assertEquals(d, again)
        assertEquals(
            Compatibility.Match,
            SsJson
                .decodeFromString(
                    com.shikkhasathi.core.model.RecommendedCourse
                        .serializer(),
                    """{"id":"c1","slug":"ds-mid","title":"Data Structures Midterm","sellerName":"তানভীর স্যার","examType":"midterm","price":299,"compareAtPrice":499,"compatibility":"match"}""",
                ).compatibility,
        )
    }
}

class ClassroomApiPathsTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val store = InMemoryTokenStore(StoredSession("access", "2026-11-03T05:00:00Z", "rt"))

    @Test
    fun dashboardRepositoryFetchesAndCaches() =
        runTest {
            val seen = mutableListOf<String>()
            val api =
                ApiClient(
                    MockEngine { req ->
                        seen += req.url.encodedPathAndQuery
                        respond(DASHBOARD_JSON, HttpStatusCode.OK, jsonHeaders)
                    },
                    ApiConfig("https://api.test/api/v1", ClientPlatform.Android),
                    store,
                )
            val cache = InMemoryDashboardCache()
            val repo = ApiDashboardRepository(api, cache, FixedClock())
            assertNull(repo.cached())
            repo.fetch()
            repo.fetch(fresh = true)
            assertEquals(listOf("/api/v1/dashboard", "/api/v1/dashboard?fresh=1"), seen)
            assertEquals(FIXTURE_NOW, repo.cached()?.savedAt)
            assertEquals(
                "রাফি আহমেদ",
                repo
                    .cached()
                    ?.dashboard
                    ?.header
                    ?.name
            )
        }

    @Test
    fun classroomQueriesBodiesAndErrors() =
        runTest {
            val seen = mutableListOf<String>()
            val api =
                ApiClient(
                    MockEngine { req ->
                        val body = req.body.toByteArray().decodeToString()
                        seen += "${req.method.value} ${req.url.encodedPathAndQuery} $body".trim()
                        when (req.url.encodedPath) {
                            "/api/v1/classroom/assignments" -> respond("""{"items":[]}""", HttpStatusCode.OK, jsonHeaders)
                            "/api/v1/classroom/sync" ->
                                respond(
                                    """{"error":{"code":"RATE_LIMITED","message":"এইমাত্র সিঙ্ক হয়েছে","details":{"retryAfterSec":42}}}""",
                                    HttpStatusCode.TooManyRequests,
                                    jsonHeaders,
                                )
                            else -> respond("""{"ok":true}""", HttpStatusCode.OK, jsonHeaders)
                        }
                    },
                    ApiConfig("https://api.test/api/v1", ClientPlatform.Android),
                    store,
                )
            val repo = ApiClassroomRepository(api)
            repo.assignments(AssignmentFilter.Pending, courseId = "gc 22/03", from = Instant.parse("2026-11-01T00:00:00Z"))
            repo.markFeedRead(all = true)
            repo.disconnect()
            val e = assertFailsWith<ApiException> { repo.sync() }

            assertEquals("GET /api/v1/classroom/assignments?status=pending&courseId=gc%2022%2F03&from=2026-11-01T00%3A00%3A00Z", seen[0])
            assertEquals("""POST /api/v1/classroom/feed/read {"all":true}""", seen[1])
            assertEquals("DELETE /api/v1/classroom/connection", seen[2])
            assertEquals(ApiError.RATE_LIMITED, e.error.code)
            assertEquals(42, e.error.retryAfterSec)
            assertTrue(seen[3].startsWith("POST /api/v1/classroom/sync"))
        }
}
