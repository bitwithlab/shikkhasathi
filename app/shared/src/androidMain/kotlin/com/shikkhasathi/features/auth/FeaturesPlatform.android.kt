package com.shikkhasathi.features.auth.data

import android.content.Context
import com.shikkhasathi.features.classroom.data.AndroidGoogleAuthorization
import com.shikkhasathi.features.classroom.data.GoogleAuthorization
import com.shikkhasathi.features.classroom.data.GoogleAuthorizationBridge
import com.shikkhasathi.features.downloads.data.AeadCipher
import com.shikkhasathi.features.downloads.data.DownloadRegistry
import com.shikkhasathi.features.downloads.data.FileDownloadRegistry
import com.shikkhasathi.features.downloads.data.JceAeadCipher
import com.shikkhasathi.features.downloads.data.JvmOfflineFileStore
import com.shikkhasathi.features.downloads.data.KeyWrapper
import com.shikkhasathi.features.downloads.data.KeystoreKeyWrapper
import com.shikkhasathi.features.downloads.data.OfflineFileStore
import com.shikkhasathi.features.downloads.data.UnsupportedVideoDownloader
import com.shikkhasathi.features.downloads.data.VideoDownloader
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
        // Offline downloads: noBackupFilesDir is never backed up or transferred (files are useless elsewhere anyway).
        single<AeadCipher> { JceAeadCipher() }
        single<KeyWrapper> { KeystoreKeyWrapper() }
        single<OfflineFileStore> { JvmOfflineFileStore(get<Context>().noBackupFilesDir.resolve("offline")) }
        single<DownloadRegistry> { FileDownloadRegistry(Path(get<Context>().noBackupFilesDir.absolutePath, "offline", "registry.json")) }
        // Media3 HLS downloader (DownloadService + encrypted SimpleCache) — see docs/MOBILE_NOTES.md, needs an SDK machine.
        single<VideoDownloader> { UnsupportedVideoDownloader }
    }
