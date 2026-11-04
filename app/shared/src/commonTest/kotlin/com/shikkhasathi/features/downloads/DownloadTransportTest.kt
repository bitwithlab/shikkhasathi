package com.shikkhasathi.features.downloads

import com.shikkhasathi.features.downloads.data.DownloadException
import com.shikkhasathi.features.downloads.data.KtorDownloadTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DownloadTransportTest {
    @Test
    fun streamsTheSignedUrlWithoutAuthHeaders() =
        runTest {
            val body = ByteArray(100_000) { (it % 7).toByte() }
            var auth: String? = "unset"
            val http =
                HttpClient(
                    MockEngine { req ->
                        auth = req.headers[HttpHeaders.Authorization]
                        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, body.size.toString()))
                    },
                )
            var total: Long? = null
            var received = 0
            KtorDownloadTransport(http).fetch("https://storage.test/doc.pdf?token=abc", onTotal = { total = it }) { _, n -> received += n }
            assertEquals(body.size, received)
            assertEquals(body.size.toLong(), total)
            assertNull(auth)
        }

    @Test
    fun expiredSignedUrlFails() =
        runTest {
            val http = HttpClient(MockEngine { respond("expired", HttpStatusCode.Forbidden) })
            assertFailsWith<DownloadException> { KtorDownloadTransport(http).fetch("https://storage.test/x", {}) { _, _ -> } }
        }
}
