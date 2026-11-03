package com.shikkhasathi.features.auth.data

import com.shikkhasathi.features.downloads.data.DownloadManager
import com.shikkhasathi.features.notifications.data.PushRegistrar

/**
 * "লগ আউট": stop push to this phone while the session can still authenticate, delete offline downloads and their
 * key (they belong to this account), then end the session. Every step before logout is best effort — signing out
 * must always work, offline included.
 */
class SignOut(
    private val auth: AuthRepository,
    private val push: PushRegistrar,
    private val downloads: DownloadManager,
) {
    @Suppress("TooGenericExceptionCaught") // nothing may block the sign-out itself
    suspend operator fun invoke() {
        try {
            push.unregister()
        } catch (_: Exception) {
        }
        try {
            downloads.clearAll()
        } catch (_: Exception) {
        }
        auth.logout()
    }
}
