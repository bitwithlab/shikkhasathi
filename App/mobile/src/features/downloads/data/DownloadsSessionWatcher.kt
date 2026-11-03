package com.shikkhasathi.features.downloads.data

import com.shikkhasathi.features.auth.data.AuthRepository
import com.shikkhasathi.features.auth.data.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Loads the download registry for each signed-in account; files of any other account are purged then. */
class DownloadsSessionWatcher(
    auth: AuthRepository,
    manager: DownloadManager,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            auth.session
                .filterIsInstance<SessionState.SignedIn>()
                .map { it.user.id }
                .distinctUntilChanged()
                .collect { manager.start() }
        }
    }
}
