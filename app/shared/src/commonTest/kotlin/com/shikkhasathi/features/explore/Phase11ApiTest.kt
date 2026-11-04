package com.shikkhasathi.features.explore

import com.shikkhasathi.core.datastore.InMemoryTokenStore
import com.shikkhasathi.core.datastore.StoredSession
import com.shikkhasathi.core.model.ClientPlatform
import com.shikkhasathi.core.model.MyCoursesFilter
import com.shikkhasathi.core.model.PaymentMethod
import com.shikkhasathi.core.model.PriceFilter
import com.shikkhasathi.core.model.SaveAnswerBody
import com.shikkhasathi.core.model.SearchFilters
import com.shikkhasathi.core.model.SearchSort
import com.shikkhasathi.core.network.ApiClient
import com.shikkhasathi.core.network.ApiConfig
import com.shikkhasathi.features.checkout.data.ApiOrdersRepository
import com.shikkhasathi.features.exam.data.ApiExamRepository
import com.shikkhasathi.features.explore.data.ApiCatalogRepository
import com.shikkhasathi.features.learning.data.ApiLearningRepository
import com.shikkhasathi.features.notifications.data.ApiNotificationsRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Paths, query strings and bodies the new repositories send, and that contract-shaped answers decode. */
class Phase11ApiTest {
    private val calls = mutableListOf<String>()
    private val bodies = mutableListOf<String>()

    private fun api(answer: (String) -> String) =
        ApiClient(
            MockEngine { req ->
                calls += "${req.method.value} ${req.url.encodedPathAndQuery}"
                bodies += req.body.toByteArray().decodeToString()
                respond(answer(req.url.encodedPath), headers = headersOf(HttpHeaders.ContentType, "application/json"))
            },
            ApiConfig("https://api.test/api/v1", ClientPlatform.Android),
            InMemoryTokenStore(StoredSession("a", "2026-11-03T10:00:00Z", "r")),
        )

    @Test
    fun searchSendsContractQueryNames() =
        runTest {
            val repo =
                ApiCatalogRepository(
                    api {
                        """{"items":[],"total":0,"nextCursor":null,"interpreted":[{"term":"DS","meaning":"Data Structures"}],"engine":"meilisearch"}"""
                    }
                )
            val r =
                repo.search(
                    SearchFilters(query = " DS ", price = PriceFilter.Free, sort = SearchSort.PriceAsc, examType = "midterm"),
                    cursor = "c2",
                    limit = 20
                )
            assertEquals("GET /api/v1/search?q=DS&examType=midterm&price=free&sort=price_asc&cursor=c2&limit=20", calls.single())
            assertEquals("Data Structures", r.interpreted.single().meaning)
        }

    @Test
    fun checkoutPlaybackAndMockBodies() =
        runTest {
            val orderJson =
                """{"id":"o1","status":"pending","items":[],"subtotal":349,"discount":0,"total":349,"couponCode":null,""" +
                    """"paymentMethod":null,"createdAt":"x","paidAt":null,"invoiceNumber":null,"refund":null,""" +
                    """"refundEligible":false,"firstLessonPath":null}"""
            val playbackJson =
                """{"kind":"hls","url":"https://v/1.m3u8","expiresAt":"x",""" +
                    """"streamToken":"st","watermark":"w","startAtSec":30}"""
            val api =
                api { path ->
                    when {
                        path.endsWith("/pay") -> """{"redirectUrl":"https://gw.test/1","gateway":"fake"}"""
                        path.endsWith("/orders") -> orderJson
                        path.endsWith("/playback") -> playbackJson
                        path.startsWith("/api/v1/me/courses") -> """{"items":[]}"""
                        else -> """{"ok":true}"""
                    }
                }
            ApiOrdersRepository(api).create("c1", null, "key-12345678")
            ApiOrdersRepository(api).pay("o1", PaymentMethod.Bkash)
            ApiLearningRepository(api).playback("l1", "install-1234", "Pixel 8")
            ApiLearningRepository(api).myCourses(MyCoursesFilter.Expiring)
            ApiExamRepository(api).saveAnswer("a1", SaveAnswerBody("q1", null, flagged = true))
            ApiNotificationsRepository(api).unregisterPush("fcm-token-xyz")
            assertEquals(
                listOf(
                    "POST /api/v1/orders",
                    "POST /api/v1/orders/o1/pay",
                    "POST /api/v1/lessons/l1/playback",
                    "GET /api/v1/me/courses?filter=expiring",
                    "PUT /api/v1/mock-attempts/a1/answers",
                    "DELETE /api/v1/me/push-tokens",
                ),
                calls,
            )
            assertEquals("""{"courseId":"c1","idempotencyKey":"key-12345678"}""", bodies[0], "no coupon → field omitted")
            assertEquals("""{"method":"bkash"}""", bodies[1])
            assertEquals("""{"deviceId":"install-1234","deviceName":"Pixel 8"}""", bodies[2])
            assertTrue(bodies[4].contains("\"optionId\"").not() && bodies[4].contains("\"flagged\":true"), bodies[4])
            assertEquals("""{"token":"fcm-token-xyz"}""", bodies[5])
        }
}
