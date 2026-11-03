package com.shikkhasathi.features.auth.data

import com.shikkhasathi.features.classroom.data.GoogleAuthorization
import com.shikkhasathi.features.classroom.data.IosGoogleAuthorization
import com.shikkhasathi.features.downloads.data.AeadCipher
import com.shikkhasathi.features.downloads.data.DownloadRegistry
import com.shikkhasathi.features.downloads.data.FileDownloadRegistry
import com.shikkhasathi.features.downloads.data.IosAeadCipher
import com.shikkhasathi.features.downloads.data.IosKeyWrapper
import com.shikkhasathi.features.downloads.data.IosOfflineFileStore
import com.shikkhasathi.features.downloads.data.KeyWrapper
import com.shikkhasathi.features.downloads.data.OfflineFileStore
import com.shikkhasathi.features.downloads.data.UnsupportedVideoDownloader
import com.shikkhasathi.features.downloads.data.VideoDownloader
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
        single<AeadCipher> { IosAeadCipher() }
        single<KeyWrapper> { IosKeyWrapper() }
        single<OfflineFileStore> { IosOfflineFileStore(Path(applicationSupport(), "ShikkhaSathi", "offline")) }
        single<DownloadRegistry> { FileDownloadRegistry(Path(applicationSupport(), "ShikkhaSathi", "offline", "registry.json")) }
        // AVAssetDownloadURLSession for HLS — to implement in the Swift host (docs/MOBILE_NOTES.md).
        single<VideoDownloader> { UnsupportedVideoDownloader }
    }

private fun applicationSupport(): String =
    NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
