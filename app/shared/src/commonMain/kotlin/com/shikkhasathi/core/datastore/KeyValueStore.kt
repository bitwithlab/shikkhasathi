package com.shikkhasathi.core.datastore

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Small non-secret preferences (install id, last registered push token, player speed).
 * - Android: private SharedPreferences `ss_prefs` (excluded from backup, `data_extraction_rules.xml`).
 * - iOS: `NSUserDefaults`.
 * - Desktop / tests: [InMemoryKeyValueStore].
 * Secrets never go here — sessions use [TokenStore].
 */
interface KeyValueStore {
    fun getString(key: String): String?

    fun putString(
        key: String,
        value: String,
    )

    fun remove(key: String)
}

class InMemoryKeyValueStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {
    private val values = initial.toMutableMap()

    override fun getString(key: String): String? = values[key]

    override fun putString(
        key: String,
        value: String,
    ) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}

/**
 * Random id created on first launch and kept for the life of the install. It is the `deviceId` of
 * `POST /lessons/:id/playback` (3-device limit, ADR-022) and ties offline downloads to this install.
 * Reinstalling the app makes a new id (the old one ages out of the server's device list after 30 days).
 */
class InstallId(
    private val store: KeyValueStore,
    private val generate: () -> String = ::randomId,
) {
    val value: String by lazy {
        store.getString(KEY)?.takeIf { it.length >= MIN_LENGTH } ?: generate().also { store.putString(KEY, it) }
    }

    companion object {
        const val KEY = "install_id"

        /** `PlaybackBody.deviceId` is 8–80 characters. */
        const val MIN_LENGTH = 8
    }
}

/** UUID v4 string — also used for checkout idempotency keys. */
@OptIn(ExperimentalUuidApi::class)
fun randomId(): String = Uuid.random().toString()
