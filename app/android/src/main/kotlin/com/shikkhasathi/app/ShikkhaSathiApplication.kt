package com.shikkhasathi.app

import android.app.Application
import android.content.Context
import com.shikkhasathi.shared.di.AppConfig
import com.shikkhasathi.shared.di.initKoin
import org.koin.dsl.module

class ShikkhaSathiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(
            config =
                AppConfig(
                    apiBaseUrl = BuildConfig.API_BASE_URL,
                    debug = BuildConfig.DEBUG,
                    googleServerClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID.ifBlank { null },
                ),
            extra = listOf(module { single<Context> { this@ShikkhaSathiApplication } }),
        )
    }
}
