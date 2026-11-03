package com.shikkhasathi.features.home.data

import com.shikkhasathi.core.model.Dashboard
import com.shikkhasathi.core.network.SsJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/** The last `GET /dashboard` answer and when it was stored. */
@Serializable
data class CachedDashboard(
    val dashboard: Dashboard,
    val savedAt: Instant,
)

/**
 * Offline cache for the home dashboard: HomeScreen paints the last answer instantly, then refreshes.
 * One entry per device; it is cleared on sign-out and on Classroom disconnect ([DashboardCacheJanitor]).
 *
 * Implementations: [InMemoryDashboardCache] (desktop/tests), [FileDashboardCache] (Android + iOS for now).
 * The planned Android implementation is Room KMP (CLAUDE.md §6) — see docs/MOBILE_NOTES.md.
 */
interface DashboardCache {
    suspend fun read(): CachedDashboard?

    suspend fun write(entry: CachedDashboard)

    suspend fun clear()
}

class InMemoryDashboardCache(initial: CachedDashboard? = null) : DashboardCache {
    private var entry: CachedDashboard? = initial

    override suspend fun read(): CachedDashboard? = entry

    override suspend fun write(entry: CachedDashboard) {
        this.entry = entry
    }

    override suspend fun clear() {
        entry = null
    }
}

/**
 * Stores the entry as one JSON file (app-private storage: Android `filesDir`, iOS Application Support).
 * A corrupt or incompatible file reads as "no cache" and is overwritten by the next successful load.
 */
class FileDashboardCache(
    private val file: Path,
    private val json: Json = SsJson,
) : DashboardCache {
    private val lock = Mutex()

    override suspend fun read(): CachedDashboard? =
        io {
            if (!SystemFileSystem.exists(file)) return@io null
            runCatching {
                val text = SystemFileSystem.source(file).buffered().use { it.readString() }
                json.decodeFromString(CachedDashboard.serializer(), text)
            }.getOrNull()
        }

    override suspend fun write(entry: CachedDashboard) =
        io {
            file.parent?.let { SystemFileSystem.createDirectories(it) }
            val tmp = Path("$file.tmp")
            SystemFileSystem.sink(tmp).buffered().use { it.writeString(json.encodeToString(CachedDashboard.serializer(), entry)) }
            SystemFileSystem.atomicMove(tmp, file)
        }

    override suspend fun clear() =
        io {
            SystemFileSystem.delete(file, mustExist = false)
        }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.Default) { lock.withLock { block() } }
}
