package com.shikkhasathi.core.common

import com.shikkhasathi.core.datastore.InMemoryTokenStore
import com.shikkhasathi.core.datastore.TokenStore
import com.shikkhasathi.core.model.ClientPlatform
import com.shikkhasathi.core.model.DeviceInfo
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.dsl.module

/**
 * The desktop JVM target is a development/test harness only (ADR-006). It signs in with mobile semantics
 * (refresh token in the body), so it reports itself as Android to the API.
 */
actual fun corePlatformModule() =
    module {
        single<HttpClientEngine> { OkHttp.create() }
        single<TokenStore> { InMemoryTokenStore() }
        single { DeviceInfo(platform = ClientPlatform.Android, deviceName = "Desktop (${System.getProperty("os.name")})") }
    }
