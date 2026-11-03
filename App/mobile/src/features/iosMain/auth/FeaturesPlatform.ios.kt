package com.shikkhasathi.features.auth.data

import com.shikkhasathi.features.classroom.data.GoogleAuthorization
import com.shikkhasathi.features.classroom.data.IosGoogleAuthorization
import com.shikkhasathi.features.home.data.DashboardCache
import com.shikkhasathi.features.home.data.FileDashboardCache
import kotlinx.io.files.Path
import org.koin.dsl.module
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

// Replace GoogleAuth with the native implementation when Google sign-in ships (see GoogleAuth).
actual fun featuresPlatformModule() =
    module {
        single<GoogleAuth> { UnavailableGoogleAuth }
        single<GoogleAuthorization> { IosGoogleAuthorization() }
        single<DashboardCache> {
            val support = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
            FileDashboardCache(Path(support, "ShikkhaSathi", "dashboard.json"))
        }
    }
