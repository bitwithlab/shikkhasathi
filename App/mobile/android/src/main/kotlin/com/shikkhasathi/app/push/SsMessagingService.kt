package com.shikkhasathi.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.shikkhasathi.app.MainActivity
import com.shikkhasathi.app.R

/**
 * FCM entry point (stub for Phase 1). Shows data/notification messages on the "classroom" channel.
 * Registering the device token with the API arrives with the notifications feature (Phase 4).
 */
class SsMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Log.i(TAG, "FCM token refreshed (${token.take(6)}…); registered with the API once push ships")
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
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
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
        NotificationManagerCompat.from(this).notify(message.messageId?.hashCode() ?: title.hashCode(), notification)
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

    private companion object {
        const val TAG = "SsMessaging"
    }
}
