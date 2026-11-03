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
            config = AppConfig(apiBaseUrl = BuildConfig.API_BASE_URL, debug = BuildConfig.DEBUG),
            extra = listOf(module { single<Context> { this@ShikkhaSathiApplication } }),
        )
    }
}
