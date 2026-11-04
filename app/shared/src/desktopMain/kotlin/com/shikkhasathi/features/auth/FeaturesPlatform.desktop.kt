package com.shikkhasathi.features.auth.data

import com.shikkhasathi.features.classroom.data.DesktopGoogleAuthorization
import com.shikkhasathi.features.classroom.data.GoogleAuthorization
import com.shikkhasathi.features.downloads.data.AeadCipher
import com.shikkhasathi.features.downloads.data.DownloadRegistry
import com.shikkhasathi.features.downloads.data.InMemoryDownloadRegistry
import com.shikkhasathi.features.downloads.data.InMemoryOfflineFileStore
import com.shikkhasathi.features.downloads.data.JceAeadCipher
import com.shikkhasathi.features.downloads.data.KeyWrapper
import com.shikkhasathi.features.downloads.data.OfflineFileStore
import com.shikkhasathi.features.downloads.data.PlainKeyWrapper
import com.shikkhasathi.features.downloads.data.UnsupportedVideoDownloader
import com.shikkhasathi.features.downloads.data.VideoDownloader
import com.shikkhasathi.features.home.data.DashboardCache
import com.shikkhasathi.features.home.data.InMemoryDashboardCache
import org.koin.dsl.module

// Replace GoogleAuth with the native implementation when Google sign-in ships (see GoogleAuth).
actual fun featuresPlatformModule() =
    module {
        single<GoogleAuth> { UnavailableGoogleAuth }
        single<GoogleAuthorization> { DesktopGoogleAuthorization() }
        single<DashboardCache> { InMemoryDashboardCache() }
        single<AeadCipher> { JceAeadCipher() }
        single<KeyWrapper> { PlainKeyWrapper }
        single<OfflineFileStore> { InMemoryOfflineFileStore() }
        single<DownloadRegistry> { InMemoryDownloadRegistry() }
        single<VideoDownloader> { UnsupportedVideoDownloader }
    }
