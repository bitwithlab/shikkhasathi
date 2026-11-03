package com.shikkhasathi.shared.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Links the app was asked to open from outside the UI: App Links / Universal Links (`https://shikkhasathi.com/...`),
 * taps on push notifications (`data.link`), and the payment gateway's return URL.
 *
 * The last link is replayed until the signed-in shell handles it ([consumed]), so a cold start from a notification
 * — or a link that arrives while the student is still signing in — still lands on the right screen.
 */
class AppLinkBus {
    private val links = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 8)
    val pending: SharedFlow<String> = links.asSharedFlow()

    fun dispatch(link: String) {
        if (link.isNotBlank()) links.tryEmit(link.trim())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun consumed() = links.resetReplayCache()
}
