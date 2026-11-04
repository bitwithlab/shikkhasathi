package com.shikkhasathi.features.home.data

import com.shikkhasathi.core.model.Dashboard
import com.shikkhasathi.core.network.ApiClient
import com.shikkhasathi.features.auth.data.AuthRepository
import com.shikkhasathi.features.auth.data.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Home dashboard aggregate (`GET /dashboard`) with an offline copy. */
interface DashboardRepository {
    /** Last stored answer, or null on a fresh install / after sign-out. */
    suspend fun cached(): CachedDashboard?

    /**
     * Network load; stores the answer in the cache on success. [fresh] bypasses the server's 60 s
     * aggregate cache (used right after a manual sync so new work shows immediately).
     */
    suspend fun fetch(fresh: Boolean = false): Dashboard

    suspend fun clearCache()
}

class ApiDashboardRepository(
    private val api: ApiClient,
    private val cache: DashboardCache,
    private val clock: Clock = Clock.System,
) : DashboardRepository {
    override suspend fun cached(): CachedDashboard? = cache.read()

    override suspend fun fetch(fresh: Boolean): Dashboard {
        val dashboard = api.get<Dashboard>(if (fresh) "dashboard?fresh=1" else "dashboard")
        // A failed write only costs the next cold start its instant paint.
        runCatching { cache.write(CachedDashboard(dashboard, clock.now())) }
        return dashboard
    }

    override suspend fun clearCache() = cache.clear()
}

/** Wipes the dashboard copy when the user signs out, so the next account never sees someone else's classes. */
class DashboardCacheJanitor(
    auth: AuthRepository,
    cache: DashboardCache,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            // Starts as Restoring; a restore that finds no session also lands on SignedOut, which is correct to clear.
            auth.session.filterIsInstance<SessionState.SignedOut>().collect { cache.clear() }
        }
    }
}
