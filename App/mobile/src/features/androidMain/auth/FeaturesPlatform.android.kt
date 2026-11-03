package com.shikkhasathi.features.auth.data

import android.content.Context
import com.shikkhasathi.features.classroom.data.AndroidGoogleAuthorization
import com.shikkhasathi.features.classroom.data.GoogleAuthorization
import com.shikkhasathi.features.classroom.data.GoogleAuthorizationBridge
import com.shikkhasathi.features.home.data.DashboardCache
import com.shikkhasathi.features.home.data.FileDashboardCache
import kotlinx.io.files.Path
import org.koin.dsl.module

// Replace GoogleAuth with the native implementation when Google sign-in ships (see GoogleAuth).
// DashboardCache: JSON file in app-private storage until the Room KMP cache lands (docs/MOBILE_NOTES.md).
actual fun featuresPlatformModule() =
    module {
        single<GoogleAuth> { UnavailableGoogleAuth }
        single { GoogleAuthorizationBridge() }
        single<GoogleAuthorization> { AndroidGoogleAuthorization(bridge = get(), config = get()) }
        single<DashboardCache> { FileDashboardCache(Path(get<Context>().filesDir.absolutePath, "cache", "dashboard.json")) }
    }
