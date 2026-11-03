package com.shikkhasathi.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.shikkhasathi.app.MainActivity
import com.shikkhasathi.app.R
import com.shikkhasathi.features.notifications.data.PushRegistrar
import org.koin.core.context.GlobalContext

/**
 * FCM entry point.
 * - [onNewToken] hands the token to [PushRegistrar] (`POST /me/push-tokens`, once per account + token).
 * - Messages carry `data.link` (Web/server services/push): the notification opens it in the app via
 *   [MainActivity] → `AppLinkBus` → `routeForLink` (`/learn/…`, `/exam-mode/…`, `/classroom/assignments?id=…`).
 * Data-only messages are shown here; notification messages in the background are shown by FCM itself and
 * deliver `data` as intent extras, which MainActivity reads under the same [EXTRA_LINK] key.
 */
class SsMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        GlobalContext.getOrNull()?.get<PushRegistrar>()?.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel()
        val id = message.messageId?.hashCode() ?: title.hashCode()
        val open =
            PendingIntent.getActivity(
                this,
                id,
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .apply { message.data[EXTRA_LINK]?.let { putExtra(EXTRA_LINK, it) } },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat
                .Builder(this, getString(R.string.notification_channel_classroom))
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
        NotificationManagerCompat.from(this).notify(id, notification)
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val id = getString(R.string.notification_channel_classroom)
        if (manager.getNotificationChannel(id) != null) return
        manager.createNotificationChannel(
            NotificationChannel(id, getString(R.string.notification_channel_classroom_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = getString(R.string.notification_channel_classroom_description)
            },
        )
    }

    companion object {
        /** Same key as the server's FCM `data.link`. */
        const val EXTRA_LINK = "link"
    }
}
