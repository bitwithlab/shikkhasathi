package com.shikkhasathi.shared.di

import com.shikkhasathi.core.common.corePlatformModule
import com.shikkhasathi.core.model.DeviceInfo
import com.shikkhasathi.core.network.ApiClient
import com.shikkhasathi.core.network.ApiConfig
import com.shikkhasathi.features.auth.data.ApiAuthRepository
import com.shikkhasathi.features.auth.data.AuthRepository
import com.shikkhasathi.features.auth.data.featuresPlatformModule
import com.shikkhasathi.features.auth.presentation.AuthViewModel
import com.shikkhasathi.features.home.presentation.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

/** Build-time configuration handed in by the host app (Android BuildConfig / iOS Info.plist). */
data class AppConfig(
    val apiBaseUrl: String,
    val debug: Boolean = false,
)

fun appModule(config: AppConfig): Module =
    module {
        single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        single { ApiConfig(baseUrl = config.apiBaseUrl, platform = get<DeviceInfo>().platform, enableLogging = config.debug) }
        single { ApiClient(engine = get(), config = get(), tokenStore = get()) }
        single<AuthRepository> { ApiAuthRepository(api = get(), tokenStore = get(), device = get(), appScope = get()) }
        // ViewModels are factories; the Compose layer scopes each instance to a NavBackStackEntry / screen.
        factory { AuthViewModel(repository = get(), googleAuth = get()) }
        factory { HomeViewModel(repository = get()) }
    }

fun allModules(config: AppConfig): List<Module> = listOf(corePlatformModule(), featuresPlatformModule(), appModule(config))

/** Called once per process: `Application.onCreate` on Android, `MainViewController()` on iOS. */
fun initKoin(
    config: AppConfig,
    extra: List<Module> = emptyList(),
): KoinApplication = startKoin { modules(extra + allModules(config)) }
