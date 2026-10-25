package com.messages.som.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.messages.som.AppMessagesActivity
import com.messages.som.R

class MessagesFirebaseService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        getSharedPreferences("messages_som", MODE_PRIVATE).edit().putString("fcm_token", token).apply()
        AppMessagingRepository().ensureSignedIn { result -> result.onSuccess { AppMessagingRepository().updateFcmToken(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "Messages SOM"
        val body = message.notification?.body ?: message.data["body"] ?: "New message"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel("chat", "Messages", NotificationManager.IMPORTANCE_HIGH))
        }
        val intent = Intent(this, AppMessagesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = android.app.PendingIntent.getActivity(
            this, 1001, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, "chat")
            .setSmallIcon(R.drawable.ic_messages_som)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify((System.currentTimeMillis() and 0x7fffffff).toInt(), notification) }
    }
}
