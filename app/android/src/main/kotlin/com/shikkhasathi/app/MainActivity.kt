package com.shikkhasathi.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.google.firebase.messaging.FirebaseMessaging
import com.shikkhasathi.app.push.SsMessagingService
import com.shikkhasathi.features.classroom.data.GoogleAuthorizationBridge
import com.shikkhasathi.features.notifications.data.PushRegistrar
import com.shikkhasathi.shared.navigation.AppLinkBus
import com.shikkhasathi.shared.ui.App
import org.koin.core.context.GlobalContext

/**
 * Single activity hosting the shared Compose UI (`singleTask`).
 *
 * Links reach the shared [AppLinkBus], which the signed-in shell turns into a destination (`routeForLink`):
 * - App Links: `https://shikkhasathi.com/...` (VIEW intent data) — including the payment gateway's return URL;
 * - push taps: the notification's `link` extra set by [SsMessagingService].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val koin = GlobalContext.get()
        // Registers the consent-screen launcher for Classroom connect (must happen before STARTED).
        koin.get<GoogleAuthorizationBridge>().attach(this)
        if (savedInstanceState == null) dispatchLink(intent)
        // The current FCM token (onNewToken only fires when it changes); PushRegistrar dedupes per account.
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> koin.get<PushRegistrar>().onNewToken(token) }
        }
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchLink(intent)
    }

    private fun dispatchLink(intent: Intent?) {
        val link = intent?.getStringExtra(SsMessagingService.EXTRA_LINK) ?: intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString
        link?.let { GlobalContext.get().get<AppLinkBus>().dispatch(it) }
    }
}
