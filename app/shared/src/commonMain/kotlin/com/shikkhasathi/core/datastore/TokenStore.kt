package com.shikkhasathi.core.datastore

import com.shikkhasathi.core.model.AuthTokens
import com.shikkhasathi.core.model.Me
import kotlinx.serialization.Serializable

/** What the app keeps between launches. Persisted only by a platform-secure [TokenStore]. */
@Serializable
data class StoredSession(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String?,
    /** Last known profile, so the home greeting renders offline before `/me` answers. */
    val user: Me? = null,
)

fun AuthTokens.toStoredSession(previousRefreshToken: String? = null): StoredSession =
    StoredSession(
        accessToken = accessToken,
        accessTokenExpiresAt = accessTokenExpiresAt,
        refreshToken = refreshToken ?: previousRefreshToken,
        user = user,
    )

/**
 * Secure session storage.
 * - Android: AES-GCM with a non-exportable key in the Android Keystore (`KeystoreTokenStore`).
 * - iOS: Keychain generic password, this-device-only (`KeychainTokenStore`).
 * - Desktop (dev/test harness only): [InMemoryTokenStore].
 */
interface TokenStore {
    suspend fun read(): StoredSession?

    suspend fun write(session: StoredSession)

    suspend fun clear()
}

class InMemoryTokenStore(initial: StoredSession? = null) : TokenStore {
    private var session: StoredSession? = initial

    override suspend fun read(): StoredSession? = session

    override suspend fun write(session: StoredSession) {
        this.session = session
    }

    override suspend fun clear() {
        session = null
    }
}
