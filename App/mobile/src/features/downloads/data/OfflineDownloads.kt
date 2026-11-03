package com.shikkhasathi.features.downloads.data

import com.shikkhasathi.core.network.SsJson
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

// ---------------------------------------------------------------- model

@Serializable
enum class OfflineKind { Document, Video }

@Serializable
enum class DownloadStatus { Queued, Downloading, Completed, Failed }

/** One downloaded lesson resource. Persisted in the registry (no secrets: the key lives in [ContentKeyStore]). */
@Serializable
data class OfflineDownload(
    val lessonId: String,
    val courseId: String,
    val title: String,
    val courseTitle: String,
    val kind: OfflineKind,
    /** Account and install the file was encrypted for. */
    val userId: String,
    val installId: String,
    /** Enrollment `accessUntil`; null = lifetime access. */
    val accessUntil: Instant? = null,
    val createdAt: Instant,
    /** Last time the server confirmed the enrollment (download or an online outline load). */
    val lastVerifiedAt: Instant,
    val status: DownloadStatus = DownloadStatus.Queued,
    val bytes: Long = 0,
    val totalBytes: Long? = null,
    val fileName: String,
    val error: String? = null,
)

/** Why a download cannot be opened (or was removed). */
enum class OfflineValidity {
    Valid,

    /** Not confirmed online for [DownloadPolicy.offlineGrace]: connect once to keep using it. */
    NeedsOnlineCheck,

    /** Enrollment access ended (`accessUntil` passed, refund or revoke). */
    AccessEnded,

    /** Encrypted for another account (switched users on a shared phone). */
    OtherAccount,

    /** Encrypted for another install (restored backup / copied files). */
    OtherDevice,
    ;

    /** Files in these states are deleted on the next purge. */
    val purge: Boolean get() = this == AccessEnded || this == OtherAccount || this == OtherDevice
}

/**
 * Offline rules (spec §8 "offline downloads encrypted and tied to account/device"):
 * - a download belongs to one account on one install; anything else is purged;
 * - it stops at the enrollment's `accessUntil`;
 * - it needs an online check every [offlineGrace] (catches refunds/revokes while the phone stayed offline).
 */
class DownloadPolicy(
    val offlineGrace: Duration = DEFAULT_GRACE,
) {
    fun validity(
        d: OfflineDownload,
        now: Instant,
        userId: String?,
        installId: String,
    ): OfflineValidity =
        when {
            userId == null || d.userId != userId -> OfflineValidity.OtherAccount
            d.installId != installId -> OfflineValidity.OtherDevice
            d.accessUntil != null && now >= d.accessUntil -> OfflineValidity.AccessEnded
            now - d.lastVerifiedAt > offlineGrace -> OfflineValidity.NeedsOnlineCheck
            else -> OfflineValidity.Valid
        }

    companion object {
        val DEFAULT_GRACE: Duration = 14.days
    }
}

// ---------------------------------------------------------------- storage

/** Where the encrypted containers live (app-private, not backed up). */
interface OfflineFileStore {
    fun sink(name: String): Sink

    fun open(name: String): RandomReadable?

    fun delete(name: String)

    fun exists(name: String): Boolean
}

/** Tests and the desktop harness. */
class InMemoryOfflineFileStore : OfflineFileStore {
    val files = mutableMapOf<String, ByteArray>()

    override fun sink(name: String): Sink {
        val buffer = Buffer()
        return object : kotlinx.io.RawSink {
            override fun write(
                source: Buffer,
                byteCount: Long,
            ) = buffer.write(source, byteCount)

            override fun flush() {
                files[name] = buffer.copy().readByteArray()
            }

            override fun close() = flush()
        }.buffered()
    }

    override fun open(name: String): RandomReadable? = files[name]?.let(::ByteArrayReadable)

    override fun delete(name: String) {
        files.remove(name)
    }

    override fun exists(name: String): Boolean = name in files
}

class ByteArrayReadable(private val bytes: ByteArray) : RandomReadable {
    override val size: Long get() = bytes.size.toLong()

    override fun read(
        position: Long,
        dest: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (position >= bytes.size) return -1
        val n = minOf(length.toLong(), bytes.size - position).toInt()
        bytes.copyInto(dest, offset, position.toInt(), position.toInt() + n)
        return n
    }

    override fun close() = Unit
}

/** The list of downloads (one small JSON file). */
interface DownloadRegistry {
    suspend fun load(): List<OfflineDownload>

    suspend fun save(items: List<OfflineDownload>)
}

class InMemoryDownloadRegistry(initial: List<OfflineDownload> = emptyList()) : DownloadRegistry {
    var items: List<OfflineDownload> = initial

    override suspend fun load(): List<OfflineDownload> = items

    override suspend fun save(items: List<OfflineDownload>) {
        this.items = items
    }
}

class FileDownloadRegistry(private val file: Path) : DownloadRegistry {
    private val serializer = ListSerializer(OfflineDownload.serializer())

    override suspend fun load(): List<OfflineDownload> =
        withContext(Dispatchers.Default) {
            if (!SystemFileSystem.exists(file)) return@withContext emptyList()
            runCatching { SsJson.decodeFromString(serializer, SystemFileSystem.source(file).buffered().use { it.readString() }) }
                .getOrDefault(emptyList())
        }

    override suspend fun save(items: List<OfflineDownload>) =
        withContext(Dispatchers.Default) {
            file.parent?.let { SystemFileSystem.createDirectories(it) }
            val tmp = Path("$file.tmp")
            SystemFileSystem.sink(tmp).buffered().use { it.writeString(SsJson.encodeToString(serializer, items)) }
            SystemFileSystem.atomicMove(tmp, file)
        }
}

// ---------------------------------------------------------------- transport

/** Streams a (signed, short-lived) URL. [onTotal] gets the Content-Length when the server sends one. */
fun interface DownloadTransport {
    suspend fun fetch(
        url: String,
        onTotal: (Long?) -> Unit,
        onBytes: (ByteArray, Int) -> Unit,
    )
}

/** Plain Ktor client (no API auth header: the storage URL is already signed). */
class KtorDownloadTransport(private val http: HttpClient) : DownloadTransport {
    override suspend fun fetch(
        url: String,
        onTotal: (Long?) -> Unit,
        onBytes: (ByteArray, Int) -> Unit,
    ) {
        http.prepareGet(url).execute { response ->
            if (!response.status.isSuccess()) throw DownloadException("HTTP ${response.status.value}")
            onTotal(response.contentLength())
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(BUFFER)
            while (true) {
                val n = channel.readAvailable(buffer, 0, buffer.size)
                if (n == -1) break
                if (n > 0) onBytes(buffer, n)
            }
        }
    }

    private companion object {
        const val BUFFER = 32 * 1024
    }
}

class DownloadException(message: String) : Exception(message)

/**
 * Video lessons are HLS: segments + playlists, not one file. On Android they are downloaded by Media3's
 * `DownloadManager` / `HlsDownloader` into a `SimpleCache` whose data sink is AES-encrypted with the account's
 * content key, run from a WorkManager-backed `DownloadService` (docs/MOBILE_NOTES.md — needs an SDK machine).
 * Until a platform binds a real one, video downloads are reported as unsupported.
 */
interface VideoDownloader {
    val supported: Boolean

    suspend fun download(
        lessonId: String,
        hlsUrl: String,
        key: ByteArray,
        onProgress: (Long, Long?) -> Unit,
    )

    fun delete(lessonId: String)
}

object UnsupportedVideoDownloader : VideoDownloader {
    override val supported: Boolean = false

    override suspend fun download(
        lessonId: String,
        hlsUrl: String,
        key: ByteArray,
        onProgress: (Long, Long?) -> Unit,
    ) = throw DownloadException("video downloads need the platform downloader")

    override fun delete(lessonId: String) = Unit
}

// ---------------------------------------------------------------- manager

/** What the app asks to download. [url] is a short-lived signed URL from the API (`GET /documents/:id/url`). */
data class DownloadRequest(
    val lessonId: String,
    val courseId: String,
    val title: String,
    val courseTitle: String,
    val kind: OfflineKind,
    val url: String,
    val accessUntil: Instant?,
)

interface DownloadManager {
    val downloads: StateFlow<List<OfflineDownload>>

    /** Loads the registry and purges what is no longer allowed. Call once after sign-in. */
    suspend fun start()

    fun enqueue(request: DownloadRequest): Boolean

    suspend fun remove(lessonId: String)

    fun validity(download: OfflineDownload): OfflineValidity

    /** An online outline load confirmed (or denied) the enrollment of [courseId]. */
    suspend fun verified(
        courseId: String,
        enrolled: Boolean,
        accessUntil: Instant?,
    )

    /** Decrypting reader of a completed, valid download, or null. */
    fun open(lessonId: String): EncryptedReader?

    /** Sign-out: delete every file and forget the account's key. */
    suspend fun clearAll()
}

/**
 * Downloads documents (PDF notes, previous questions) straight into encrypted containers, and videos through
 * the platform [VideoDownloader]. Plain bytes never touch the disk: the network stream is encrypted chunk by chunk.
 */
@Suppress("TooManyFunctions", "LongParameterList")
class OfflineDownloadManager(
    private val registry: DownloadRegistry,
    private val files: OfflineFileStore,
    private val transport: DownloadTransport,
    private val keys: ContentKeyStore,
    private val cipher: AeadCipher,
    private val currentUserId: () -> String?,
    private val installId: String,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val policy: DownloadPolicy = DownloadPolicy(),
    private val videos: VideoDownloader = UnsupportedVideoDownloader,
) : DownloadManager {
    private val items = MutableStateFlow<List<OfflineDownload>>(emptyList())
    override val downloads: StateFlow<List<OfflineDownload>> = items.asStateFlow()
    private val lock = Mutex()
    private val jobs = mutableMapOf<String, Job>()

    override suspend fun start() {
        val loaded = registry.load()
        // A download interrupted by process death restarts from scratch (signed URLs expire anyway): mark it failed.
        val normalized =
            loaded.map {
                if (it.status == DownloadStatus.Downloading || it.status == DownloadStatus.Queued) {
                    it.copy(status = DownloadStatus.Failed, error = "interrupted")
                } else {
                    it
                }
            }
        val (gone, keep) = normalized.partition { validity(it).purge }
        gone.forEach(::deleteFiles)
        mutate { keep }
    }

    override fun enqueue(request: DownloadRequest): Boolean {
        val userId = currentUserId()
        val now = clock.now()
        val allowed =
            userId != null &&
                (request.kind != OfflineKind.Video || videos.supported) &&
                (request.accessUntil == null || now < request.accessUntil)
        if (!allowed || userId == null) return false
        val entry =
            OfflineDownload(
                lessonId = request.lessonId,
                courseId = request.courseId,
                title = request.title,
                courseTitle = request.courseTitle,
                kind = request.kind,
                userId = userId,
                installId = installId,
                accessUntil = request.accessUntil,
                createdAt = now,
                lastVerifiedAt = now,
                fileName = "${request.lessonId}.sse",
            )
        jobs[request.lessonId]?.cancel()
        jobs[request.lessonId] =
            scope.launch {
                mutate { list -> list.filterNot { it.lessonId == request.lessonId } + entry }
                run(entry, request.url, userId)
            }
        return true
    }

    @Suppress("TooGenericExceptionCaught") // transport/IO/crypto boundary: every failure marks the row failed
    private suspend fun run(
        entry: OfflineDownload,
        url: String,
        userId: String,
    ) {
        update(entry.lessonId) { it.copy(status = DownloadStatus.Downloading, error = null) }
        try {
            val key = keys.keyFor(userId)
            when (entry.kind) {
                OfflineKind.Document -> downloadDocument(entry, url, key)
                OfflineKind.Video -> videos.download(entry.lessonId, url, key) { done, total -> progress(entry.lessonId, done, total) }
            }
            update(entry.lessonId) { it.copy(status = DownloadStatus.Completed, totalBytes = it.totalBytes ?: it.bytes) }
        } catch (e: CancellationException) {
            deleteFiles(entry)
            throw e
        } catch (e: Exception) {
            deleteFiles(entry)
            update(entry.lessonId) { it.copy(status = DownloadStatus.Failed, error = e.message ?: "failed", bytes = 0) }
        }
    }

    private suspend fun downloadDocument(
        entry: OfflineDownload,
        url: String,
        key: ByteArray,
    ) {
        // Network → in-memory buffer → encrypted container. Documents are small (PDF notes, question papers).
        val plain = Buffer()
        transport.fetch(url, onTotal = { total -> progress(entry.lessonId, 0, total) }) { bytes, n ->
            plain.write(bytes, 0, n)
            progress(entry.lessonId, plain.size, null)
        }
        files.sink(entry.fileName).use { sink -> ChunkedAead(cipher, key).encrypt(plain, sink) }
    }

    private fun progress(
        lessonId: String,
        done: Long,
        total: Long?,
    ) {
        items.value = items.value.map { if (it.lessonId == lessonId) it.copy(bytes = done, totalBytes = total ?: it.totalBytes) else it }
    }

    override suspend fun remove(lessonId: String) {
        jobs.remove(lessonId)?.cancel()
        val entry = items.value.firstOrNull { it.lessonId == lessonId } ?: return
        deleteFiles(entry)
        mutate { list -> list.filterNot { it.lessonId == lessonId } }
    }

    override fun validity(download: OfflineDownload): OfflineValidity = policy.validity(download, clock.now(), currentUserId(), installId)

    override suspend fun verified(
        courseId: String,
        enrolled: Boolean,
        accessUntil: Instant?,
    ) {
        val now = clock.now()
        if (!enrolled) {
            items.value.filter { it.courseId == courseId }.forEach { remove(it.lessonId) }
            return
        }
        mutate { list -> list.map { if (it.courseId == courseId) it.copy(lastVerifiedAt = now, accessUntil = accessUntil) else it } }
    }

    override fun open(lessonId: String): EncryptedReader? {
        val entry =
            items.value
                .firstOrNull { it.lessonId == lessonId && it.status == DownloadStatus.Completed }
                ?.takeIf { validity(it) == OfflineValidity.Valid }
        val file = entry?.let { files.open(it.fileName) } ?: return null
        return runCatching { ChunkedAead(cipher, keys.keyFor(entry.userId)).reader(file) }.getOrNull()
    }

    override suspend fun clearAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        val all = items.value
        all.forEach(::deleteFiles)
        all.map { it.userId }.distinct().forEach(keys::forget)
        currentUserId()?.let(keys::forget)
        mutate { emptyList() }
    }

    private fun deleteFiles(entry: OfflineDownload) {
        runCatching { files.delete(entry.fileName) }
        if (entry.kind == OfflineKind.Video) runCatching { videos.delete(entry.lessonId) }
    }

    private suspend fun update(
        lessonId: String,
        change: (OfflineDownload) -> OfflineDownload,
    ) = mutate { list -> list.map { if (it.lessonId == lessonId) change(it) else it } }

    private suspend fun mutate(change: (List<OfflineDownload>) -> List<OfflineDownload>) =
        lock.withLock {
            val next = change(items.value)
            items.value = next
            registry.save(next)
        }
}
