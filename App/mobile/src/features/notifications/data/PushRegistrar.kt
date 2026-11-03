package com.shikkhasathi.features.notifications.data

import com.shikkhasathi.core.datastore.KeyValueStore
import com.shikkhasathi.core.model.ClientPlatform
import com.shikkhasathi.core.network.ApiException
import com.shikkhasathi.features.auth.data.AuthRepository
import com.shikkhasathi.features.auth.data.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps this device's push token registered for the signed-in account (`POST /me/push-tokens`).
 *
 * Tokens arrive from the platform host:
 * - Android: `SsMessagingService.onNewToken` and `FirebaseMessaging.getInstance().token` at launch (`:android`);
 * - iOS: `application(_:didRegisterForRemoteNotificationsWithDeviceToken:)` in the Swift host (hex string).
 *
 * A token is registered once per (account, token) — the pair is remembered in [KeyValueStore], so app restarts do
 * not re-post it; a new token or another account signs it up again. Failures retry with backoff while the app runs.
 * [unregister] runs before sign-out (`SignOut`) so the phone stops receiving the old account's notifications.
 */
class PushRegistrar(
    private val repository: NotificationsRepository,
    auth: AuthRepository,
    private val prefs: KeyValueStore,
    private val platform: ClientPlatform,
    scope: CoroutineScope,
    private val retryDelaysMs: List<Long> = RETRY_DELAYS_MS,
) {
    private val token = MutableStateFlow(prefs.getString(TOKEN_KEY))

    init {
        scope.launch {
            combine(auth.session, token) { session, t -> (session as? SessionState.SignedIn)?.user?.id to t }
                .collectLatest { (userId, t) -> if (userId != null && t != null) ensureRegistered(userId, t) }
        }
    }

    /** New or refreshed platform token. Ignores tokens the contract would reject (< 10 chars). */
    fun onNewToken(value: String) {
        val t = value.trim()
        if (t.length < MIN_TOKEN || t.length > MAX_TOKEN) return
        prefs.putString(TOKEN_KEY, t)
        token.value = t
    }

    suspend fun unregister() {
        val t = token.value ?: return
        if (prefs.getString(REGISTERED_KEY) == null) return
        try {
            repository.unregisterPush(t)
        } catch (_: ApiException) {
            // Best effort: the server also drops tokens FCM/APNs report as invalid.
        }
        prefs.remove(REGISTERED_KEY)
    }

    private suspend fun ensureRegistered(
        userId: String,
        t: String,
    ) {
        val marker = "$userId|$t"
        if (prefs.getString(REGISTERED_KEY) == marker) return
        for (attempt in 0..retryDelaysMs.size) {
            try {
                repository.registerPush(platform, t)
                prefs.putString(REGISTERED_KEY, marker)
                return
            } catch (_: ApiException) {
                val wait = retryDelaysMs.getOrNull(attempt) ?: return
                delay(wait)
            }
        }
    }

    companion object {
        const val TOKEN_KEY = "push_token"
        const val REGISTERED_KEY = "push_registered"
        private const val MIN_TOKEN = 10
        private const val MAX_TOKEN = 4096
        val RETRY_DELAYS_MS = listOf(5_000L, 30_000L, 120_000L)
    }
}
