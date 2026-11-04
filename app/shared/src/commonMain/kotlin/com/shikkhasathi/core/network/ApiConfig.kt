package com.shikkhasathi.core.network

import com.shikkhasathi.core.model.ClientPlatform

/**
 * @param baseUrl versioned API root, e.g. `https://shikkhasathi.com/api/v1` (Android: BuildConfig per build type;
 *   iOS: Info.plist `SSApiBaseUrl`). Paths passed to [ApiClient] are relative to it (`auth/otp/request`).
 */
data class ApiConfig(
    val baseUrl: String,
    val platform: ClientPlatform,
    val enableLogging: Boolean = false,
) {
    fun url(path: String): String = baseUrl.trimEnd('/') + "/" + path.trimStart('/')
}
