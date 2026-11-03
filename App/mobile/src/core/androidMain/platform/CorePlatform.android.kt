package com.shikkhasathi.core.common

import android.os.Build
import com.shikkhasathi.core.datastore.InstallId
import com.shikkhasathi.core.datastore.KeyValueStore
import com.shikkhasathi.core.datastore.KeystoreTokenStore
import com.shikkhasathi.core.datastore.TokenStore
import com.shikkhasathi.core.model.ClientPlatform
import com.shikkhasathi.core.model.DeviceInfo
import com.shikkhasathi.core.player.ExoMediaPlayer
import com.shikkhasathi.core.player.MediaPlayerFactory
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.dsl.module

actual fun corePlatformModule() =
    module {
        single<HttpClientEngine> { OkHttp.create() }
        single<TokenStore> { KeystoreTokenStore(context = get()) }
        single<KeyValueStore> { PrefsKeyValueStore(context = get()) }
        single { InstallId(get()) }
        single {
            DeviceInfo(
                platform = ClientPlatform.Android,
                deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".take(DeviceInfo.MAX_NAME_LENGTH),
                deviceId = get<InstallId>().value,
            )
        }
        single<ExternalBrowser> { CustomTabsBrowser(context = get()) }
        single { MediaPlayerFactory { ExoMediaPlayer(context = get()) } }
    }
