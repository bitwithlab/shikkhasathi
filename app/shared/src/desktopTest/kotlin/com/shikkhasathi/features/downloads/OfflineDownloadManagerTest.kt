package com.shikkhasathi.features.downloads

import com.shikkhasathi.core.datastore.InMemoryKeyValueStore
import com.shikkhasathi.features.downloads.data.ContentKeyStore
import com.shikkhasathi.features.downloads.data.DownloadRequest
import com.shikkhasathi.features.downloads.data.DownloadStatus
import com.shikkhasathi.features.downloads.data.DownloadTransport
import com.shikkhasathi.features.downloads.data.InMemoryDownloadRegistry
import com.shikkhasathi.features.downloads.data.InMemoryOfflineFileStore
import com.shikkhasathi.features.downloads.data.JceAeadCipher
import com.shikkhasathi.features.downloads.data.OfflineDownloadManager
import com.shikkhasathi.features.downloads.data.OfflineKind
import com.shikkhasathi.features.downloads.data.OfflineValidity
import com.shikkhasathi.features.downloads.data.PlainKeyWrapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineDownloadManagerTest {
    private val pdf = ("%PDF-1.7 previous question CSE-2203 2025 " + "x".repeat(200_000)).encodeToByteArray()
    private var now = Instant.parse("2026-11-03T04:00:00Z")
    private val clock =
        object : Clock {
            override fun now(): Instant = now
        }
    private var user: String? = "user-a"
    private val files = InMemoryOfflineFileStore()
    private val registry = InMemoryDownloadRegistry()
    private val prefs = InMemoryKeyValueStore()
    private val cipher = JceAeadCipher()
    private val transport =
        DownloadTransport { _, onTotal, onBytes ->
            onTotal(pdf.size.toLong())
            pdf.toList().chunked(4096).forEach { part -> onBytes(part.toByteArray(), part.size) }
        }

    private fun TestScope.manager(install: String = "install-1") =
        OfflineDownloadManager(
            registry = registry,
            files = files,
            transport = transport,
            keys = ContentKeyStore(prefs, PlainKeyWrapper, cipher),
            cipher = cipher,
            currentUserId = { user },
            installId = install,
            scope = this,
            clock = clock,
        )

    private fun request(accessUntil: Instant? = Instant.parse("2027-01-01T00:00:00Z")) =
        DownloadRequest(
            "lesson-1",
            "course-1",
            "আগের বছরের প্রশ্ন",
            "DS Midterm",
            OfflineKind.Document,
            "https://files.test/q.pdf",
            accessUntil
        )

    @Test
    fun downloadsEncryptedAndOpensForTheSameAccountAndDevice() =
        runTest {
            val m = manager()
            m.start()
            assertTrue(m.enqueue(request()))
            runCurrent()
            val d = m.downloads.value.single()
            assertEquals(DownloadStatus.Completed, d.status)
            assertEquals(pdf.size.toLong(), d.totalBytes)
            val stored = files.files.getValue(d.fileName)
            assertFalse(String(stored, Charsets.ISO_8859_1).contains("%PDF"), "no plain bytes on disk")
            assertContentEquals(pdf, m.open("lesson-1")!!.readAll())
            assertEquals(1, registry.items.size, "persisted")
        }

    @Test
    fun anotherAccountOrInstallPurgesOnStart() =
        runTest {
            val m = manager()
            m.start()
            m.enqueue(request())
            runCurrent()

            user = "user-b"
            assertEquals(OfflineValidity.OtherAccount, m.validity(m.downloads.value.single()))
            assertNull(m.open("lesson-1"))
            val next = manager()
            next.start()
            assertTrue(next.downloads.value.isEmpty())
            assertTrue(files.files.isEmpty())

            user = "user-a"
            val m2 = manager()
            m2.start()
            m2.enqueue(request())
            runCurrent()
            val restored = manager(install = "install-2")
            restored.start()
            assertTrue(restored.downloads.value.isEmpty(), "restored backup on another install")
        }

    @Test
    fun expiryFollowsAccessUntilAndTheOfflineGrace() =
        runTest {
            val m = manager()
            m.start()
            assertFalse(m.enqueue(request(accessUntil = now - 1.days)), "already expired → refused")
            m.enqueue(request(accessUntil = now + 30.days))
            runCurrent()
            val d = m.downloads.value.single()

            now += 15.days
            assertEquals(OfflineValidity.NeedsOnlineCheck, m.validity(d))
            assertNull(m.open("lesson-1"))
            m.verified("course-1", enrolled = true, accessUntil = now + 10.days)
            assertEquals(OfflineValidity.Valid, m.validity(m.downloads.value.single()))

            now += 11.days
            assertEquals(OfflineValidity.AccessEnded, m.validity(m.downloads.value.single()))
            m.start()
            assertTrue(m.downloads.value.isEmpty(), "purged")
        }

    @Test
    fun refundOrRevokeRemovesAndSignOutShredsTheKey() =
        runTest {
            val m = manager()
            m.start()
            m.enqueue(request())
            runCurrent()
            m.verified("course-1", enrolled = false, accessUntil = null)
            assertTrue(m.downloads.value.isEmpty())
            assertTrue(files.files.isEmpty())

            m.enqueue(request())
            runCurrent()
            assertTrue(prefs.getString("dl_key_user-a") != null)
            m.clearAll()
            assertTrue(files.files.isEmpty())
            assertNull(prefs.getString("dl_key_user-a"), "key forgotten → crypto-shredded")
        }

    @Test
    fun failedTransferLeavesNoFileAndInterruptedOnesAreMarkedFailed() =
        runTest {
            val failing =
                OfflineDownloadManager(
                    registry = registry,
                    files = files,
                    transport = { _, _, _ ->
                        throw com.shikkhasathi.features.downloads.data
                            .DownloadException("HTTP 403")
                    },
                    keys = ContentKeyStore(prefs, PlainKeyWrapper, cipher),
                    cipher = cipher,
                    currentUserId = { user },
                    installId = "install-1",
                    scope = this,
                    clock = clock,
                )
            failing.start()
            failing.enqueue(request())
            runCurrent()
            assertEquals(
                DownloadStatus.Failed,
                failing.downloads.value
                    .single()
                    .status
            )
            assertTrue(files.files.isEmpty())

            registry.items = registry.items.map { it.copy(status = DownloadStatus.Downloading) }
            val restarted = manager()
            restarted.start()
            assertEquals(
                DownloadStatus.Failed,
                restarted.downloads.value
                    .single()
                    .status
            )
            assertFalse(restarted.enqueue(request().copy(kind = OfflineKind.Video)), "no platform video downloader on this target")
        }
}
