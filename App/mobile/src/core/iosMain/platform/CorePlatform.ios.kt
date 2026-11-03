package com.shikkhasathi.core.common

import com.shikkhasathi.core.datastore.KeychainTokenStore
import com.shikkhasathi.core.datastore.TokenStore
import com.shikkhasathi.core.model.ClientPlatform
import com.shikkhasathi.core.model.DeviceInfo
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.dsl.module
import platform.UIKit.UIDevice

actual fun corePlatformModule() =
    module {
        single<HttpClientEngine> { Darwin.create() }
        single<TokenStore> { KeychainTokenStore() }
        single { DeviceInfo(platform = ClientPlatform.Ios, deviceName = UIDevice.currentDevice.model.take(DeviceInfo.MAX_NAME_LENGTH)) }
    }
