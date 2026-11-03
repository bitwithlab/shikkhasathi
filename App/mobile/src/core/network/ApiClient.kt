package com.shikkhasathi.core.network

import com.shikkhasathi.core.datastore.StoredSession
import com.shikkhasathi.core.datastore.TokenStore
import com.shikkhasathi.core.datastore.toStoredSession
import com.shikkhasathi.core.model.AuthTokens
import com.shikkhasathi.core.model.RefreshBody
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * The one HTTP client of the app (Ktor + kotlinx.serialization).
 *
 * - Every call goes to `ApiConfig.baseUrl` + a relative path (`auth/otp/request`).
 * - Authenticated calls carry `Authorization: Bearer <access>` from [TokenStore].
 * - On `401` the client refreshes once — `POST auth/refresh` with the refresh token **in the body**
 *   (mobile transport, ADR-010) — stores the rotated pair and replays the request. Concurrent 401s share
 *   a single refresh. If the refresh is rejected the session is cleared and [sessionExpired] fires.
 * - Non-2xx answers become [ApiException] carrying the server's `{ error: { code, message } }` envelope;
 *   transport failures become [ApiError.NETWORK].
 *
 * Refresh is implemented here instead of Ktor's `Auth` plugin so the behaviour is explicit and testable
 * (Ktor only refreshes after a `WWW-Authenticate` challenge and caches tokens across logout).
 */
@Suppress("TooManyFunctions") // one small inline helper per HTTP verb
class ApiClient(
    engine: HttpClientEngine,
    private val config: ApiConfig,
    private val tokenStore: TokenStore,
    @PublishedApi internal val json: Json = SsJson,
) {
    private val refreshMutex = Mutex()
    private val expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when the refresh token is no longer accepted; the app returns to the sign-in screen. */
    val sessionExpired: SharedFlow<Unit> = expired.asSharedFlow()

    @PublishedApi
    internal val http: HttpClient =
        HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
            }
            if (config.enableLogging) {
                install(Logging) {
                    logger = Logger.SIMPLE
                    level = LogLevel.INFO
                    sanitizeHeader { it == HttpHeaders.Authorization }
                }
            }
        }

    suspend inline fun <reified T> get(
        path: String,
        authenticated: Boolean = true,
    ): T = decode(send(HttpMethod.Get, path, authenticated) {})

    suspend inline fun <reified B : Any, reified T> post(
        path: String,
        body: B,
        authenticated: Boolean = true,
    ): T =
        decode(
            send(HttpMethod.Post, path, authenticated) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    suspend inline fun <reified B : Any, reified T> patch(
        path: String,
        body: B,
    ): T =
        decode(
            send(HttpMethod.Patch, path, authenticated = true) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    suspend inline fun <reified B : Any, reified T> put(
        path: String,
        body: B,
    ): T =
        decode(
            send(HttpMethod.Put, path, authenticated = true) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    /** Authenticated POST without a body (`POST /classroom/sync`). */
    suspend inline fun <reified T> postEmpty(path: String): T = decode(send(HttpMethod.Post, path, authenticated = true) {})

    suspend inline fun <reified T> delete(path: String): T = decode(send(HttpMethod.Delete, path, authenticated = true) {})

    /** Sends a request, transparently refreshing the access token once on `401`. */
    suspend fun send(
        method: HttpMethod,
        path: String,
        authenticated: Boolean,
        configure: HttpRequestBuilder.() -> Unit,
    ): HttpResponse {
        val session = if (authenticated) tokenStore.read() else null
        val first = execute(method, path, session?.accessToken, configure)
        if (first.status != HttpStatusCode.Unauthorized || session == null) return first

        val refreshed = refresh(stale = session) ?: return first
        return execute(method, path, refreshed.accessToken, configure)
    }

    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    @PublishedApi
    internal suspend inline fun <reified T> decode(response: HttpResponse): T {
        if (!response.status.isSuccess()) throw ApiException(errorOf(response))
        return try {
            response.body<T>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Any decoding failure (SerializationException, IllegalArgumentException…) is a contract mismatch.
            throw ApiException(ApiError(ApiError.UNEXPECTED_RESPONSE, e.message ?: "bad body", response.status.value), e)
        }
    }

    @PublishedApi
    internal suspend fun errorOf(response: HttpResponse): ApiError {
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        val envelope = runCatching { json.decodeFromString(ErrorEnvelope.serializer(), text) }.getOrNull()
        return if (envelope != null) {
            ApiError(envelope.error.code, envelope.error.message, response.status.value, envelope.error.details)
        } else {
            ApiError(ApiError.UNEXPECTED_RESPONSE, "HTTP ${response.status.value}", response.status.value)
        }
    }

    @Suppress("TooGenericExceptionCaught") // transport boundary: every engine failure becomes ApiError.NETWORK
    private suspend fun execute(
        method: HttpMethod,
        path: String,
        accessToken: String?,
        configure: HttpRequestBuilder.() -> Unit,
    ): HttpResponse =
        try {
            http.request {
                this.method = method
                url(config.url(path))
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                header(CLIENT_PLATFORM_HEADER, config.platform.name.lowercase())
                accessToken?.let { bearerAuth(it) }
                configure()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            // IOException on JVM/Android, DarwinHttpRequestException on iOS, timeouts everywhere.
            throw ApiException(ApiError.network(e), e)
        }

    /**
     * Single-flight refresh. Returns the new session, the session another caller already refreshed,
     * or `null` when the session cannot be refreshed (the original 401 is then surfaced).
     */
    private suspend fun refresh(stale: StoredSession): StoredSession? =
        refreshMutex.withLock {
            val current = tokenStore.read() ?: return@withLock null
            if (current.accessToken != stale.accessToken) return@withLock current
            val refreshToken = current.refreshToken ?: return@withLock expire()

            val response =
                execute(HttpMethod.Post, REFRESH_PATH, accessToken = null) {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshBody(refreshToken))
                }
            when {
                response.status.isSuccess() -> {
                    val tokens = decode<AuthTokens>(response)
                    tokens.toStoredSession(previousRefreshToken = refreshToken).also { tokenStore.write(it) }
                }
                response.status.value in REJECTED_REFRESH -> expire()
                else -> null // 5xx / 429: keep the session, let the caller show a retryable error.
            }
        }

    private suspend fun expire(): StoredSession? {
        tokenStore.clear()
        expired.tryEmit(Unit)
        return null
    }

    fun close() = http.close()

    companion object {
        const val REFRESH_PATH = "auth/refresh"
        const val CLIENT_PLATFORM_HEADER = "X-Client-Platform"
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val REQUEST_TIMEOUT_MS = 30_000L
        private val REJECTED_REFRESH = setOf(400, 401, 403)
    }
}
