package com.shikkhasathi.core.common

import org.koin.core.module.Module

/**
 * Platform bindings for `core`, provided by each target:
 * - `io.ktor.client.engine.HttpClientEngine` — OkHttp (Android, desktop) / Darwin (iOS)
 * - `com.shikkhasathi.core.datastore.TokenStore` — Keystore / Keychain / in-memory
 * - `com.shikkhasathi.core.model.DeviceInfo`
 *
 * Android additionally expects an `android.content.Context` in the graph (registered by the Application).
 */
expect fun corePlatformModule(): Module
