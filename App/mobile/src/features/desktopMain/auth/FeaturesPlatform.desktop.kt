package com.shikkhasathi.features.auth.data

import com.shikkhasathi.features.classroom.data.DesktopGoogleAuthorization
import com.shikkhasathi.features.classroom.data.GoogleAuthorization
import com.shikkhasathi.features.home.data.DashboardCache
import com.shikkhasathi.features.home.data.InMemoryDashboardCache
import org.koin.dsl.module

// Replace GoogleAuth with the native implementation when Google sign-in ships (see GoogleAuth).
actual fun featuresPlatformModule() =
    module {
        single<GoogleAuth> { UnavailableGoogleAuth }
        single<GoogleAuthorization> { DesktopGoogleAuthorization() }
        single<DashboardCache> { InMemoryDashboardCache() }
    }
