package com.shikkhasathi.core.network

import com.shikkhasathi.core.datastore.InMemoryTokenStore
import com.shikkhasathi.core.datastore.StoredSession
import com.shikkhasathi.core.model.ClientPlatform
import com.shikkhasathi.core.model.Me
import com.shikkhasathi.core.model.OtpRequestBody
import com.shikkhasathi.core.model.OtpRequestResponse
import com.shikkhasathi.core.testing.sampleMe
import com.shikkhasathi.core.testing.sampleTokens
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiClientTest {
    private val base = "https://api.test/api/v1"
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val stored = StoredSession(accessToken = "old-access", accessTokenExpiresAt = "2026-11-03T10:00:00Z", refreshToken = "rt-1")

    private fun client(
        store: InMemoryTokenStore,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = ApiClient(MockEngine(handler), ApiConfig(base, ClientPlatform.Android), store)

    private fun MockRequestHandleScope.json(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(body, status, jsonHeaders)

    private fun meJson(name: String = "রাফি আহমেদ") = SsJson.encodeToString(Me.serializer(), sampleMe(name))

    private val unauthorized = """{"error":{"code":"UNAUTHORIZED","message":"লগইন প্রয়োজন"}}"""

    @Test
    fun refreshesOn401ThenReplaysWithNewToken() =
        runTest {
            val store = InMemoryTokenStore(stored)
            val seen = mutableListOf<String>()
            var refreshBody = ""
            val api =
                client(store) { req ->
                    seen += "${req.method.value} ${req.url.encodedPath} ${req.headers[HttpHeaders.Authorization]}"
                    when (req.url.encodedPath) {
                        "/api/v1/me" ->
                            if (req.headers[HttpHeaders.Authorization] ==
                                "Bearer new-access"
                            ) {
                                json(meJson())
                            } else {
                                json(unauthorized, HttpStatusCode.Unauthorized)
                            }
                        "/api/v1/auth/refresh" -> {
                            refreshBody = req.body.toByteArray().decodeToString()
                            json(
                                SsJson.encodeToString(
                                    com.shikkhasathi.core.model.AuthTokens
                                        .serializer(),
                                    sampleTokens("new-access", "rt-2")
                                )
                            )
                        }
                        else -> error("unexpected ${req.url}")
                    }
                }

            val me = api.get<Me>("me")

            assertEquals("রাফি আহমেদ", me.name)
            assertEquals(
                listOf("GET /api/v1/me Bearer old-access", "POST /api/v1/auth/refresh null", "GET /api/v1/me Bearer new-access"),
                seen,
            )
            assertEquals("""{"refreshToken":"rt-1"}""", refreshBody, "mobile sends the refresh token in the body")
            assertEquals("new-access", store.read()?.accessToken)
            assertEquals("rt-2", store.read()?.refreshToken, "rotated refresh token is stored")
        }

    @Test
    fun concurrent401sShareOneRefresh() =
        runTest {
            val store = InMemoryTokenStore(stored)
            var refreshCalls = 0
            val api =
                client(store) { req ->
                    when (req.url.encodedPath) {
                        "/api/v1/auth/refresh" -> {
                            refreshCalls++
                            json(
                                SsJson.encodeToString(
                                    com.shikkhasathi.core.model.AuthTokens
                                        .serializer(),
                                    sampleTokens("new-access", "rt-2")
                                )
                            )
                        }
                        else ->
                            if (req.headers[HttpHeaders.Authorization] ==
                                "Bearer new-access"
                            ) {
                                json(meJson())
                            } else {
                                json(unauthorized, HttpStatusCode.Unauthorized)
                            }
                    }
                }

            val results = List(3) { async { api.get<Me>("me") } }.awaitAll()

            assertEquals(3, results.size)
            assertEquals(1, refreshCalls)
        }

    @Test
    fun rejectedRefreshClearsSessionAndSignalsExpiry() =
        runTest {
            val store = InMemoryTokenStore(stored)
            val api =
                client(store) { req ->
                    when (req.url.encodedPath) {
                        "/api/v1/auth/refresh" ->
                            json(
                                """{"error":{"code":"UNAUTHORIZED","message":"সেশন শেষ"}}""",
                                HttpStatusCode.Unauthorized
                            )
                        else -> json(unauthorized, HttpStatusCode.Unauthorized)
                    }
                }
            // Subscribe before the call: the refresh runs on the engine's thread and may finish before a lazily
            // dispatched `async` starts collecting (the expiry signal has no replay).
            val expired = async(start = CoroutineStart.UNDISPATCHED) { api.sessionExpired.first() }

            val e = assertFailsWith<ApiException> { api.get<Me>("me") }

            assertEquals(ApiError.UNAUTHORIZED, e.error.code)
            assertEquals(401, e.error.status)
            assertNull(store.read())
            expired.await()
        }

    @Test
    fun publicCallsNeverSendOrRefreshTokens() =
        runTest {
            val store = InMemoryTokenStore(stored)
            var auth: String? = "unset"
            val api =
                client(store) { req ->
                    auth = req.headers[HttpHeaders.Authorization]
                    json("""{"sent":true,"expiresInSec":300,"resendInSec":60,"devCode":"123456"}""")
                }

            val r =
                api.post<OtpRequestBody, OtpRequestResponse>(
                    "auth/otp/request",
                    OtpRequestBody("+8801712345678"),
                    authenticated = false
                )

            assertEquals(60, r.resendInSec)
            assertEquals("123456", r.devCode)
            assertNull(auth)
        }

    @Test
    fun mapsErrorEnvelope() =
        runTest {
            val api =
                client(InMemoryTokenStore()) {
                    json(
                        """{"error":{"code":"VALIDATION_FAILED","message":"সঠিক মোবাইল নম্বর দিন (01XXXXXXXXX)","details":{"phone":["invalid"]}}}""",
                        HttpStatusCode.UnprocessableEntity,
                    )
                }

            val e =
                assertFailsWith<ApiException> {
                    api.post<OtpRequestBody, OtpRequestResponse>(
                        "auth/otp/request",
                        OtpRequestBody("123"),
                        false
                    )
                }

            assertEquals(ApiError.VALIDATION_FAILED, e.error.code)
            assertEquals("সঠিক মোবাইল নম্বর দিন (01XXXXXXXXX)", e.error.message)
            assertEquals(422, e.error.status)
            assertTrue(e.error.details != null)
        }

    @Test
    fun nonEnvelopeErrorAndTransportFailure() =
        runTest {
            val html = client(InMemoryTokenStore()) { respond("<html>Bad gateway</html>", HttpStatusCode.BadGateway) }
            assertEquals(ApiError.UNEXPECTED_RESPONSE, assertFailsWith<ApiException> { html.get<Me>("me") }.error.code)

            val offline = client(InMemoryTokenStore()) { throw IOException("Unable to resolve host") }
            val e = assertFailsWith<ApiException> { offline.get<Me>("me") }
            assertTrue(e.error.isNetwork)
        }
}
