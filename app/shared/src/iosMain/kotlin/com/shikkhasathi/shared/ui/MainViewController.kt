package com.shikkhasathi.shared.ui

import androidx.compose.ui.window.ComposeUIViewController
import com.shikkhasathi.shared.di.AppConfig
import com.shikkhasathi.shared.di.initKoin
import org.koin.mp.KoinPlatformTools
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController

/**
 * Entry point used by `ios/iosApp/ContentView.swift`: `MainViewControllerKt.MainViewController()`.
 * The API base URL comes from Info.plist key `SSApiBaseUrl` (set per build configuration in Xcode).
 */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        val baseUrl = NSBundle.mainBundle.objectForInfoDictionaryKey("SSApiBaseUrl") as? String ?: DEFAULT_API_BASE_URL
        initKoin(AppConfig(apiBaseUrl = baseUrl))
    }
    return ComposeUIViewController { App() }
}

private const val DEFAULT_API_BASE_URL = "https://shikkhasathi.com/api/v1"
