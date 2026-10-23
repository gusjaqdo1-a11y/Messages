package com.messages.som.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.messages.som.R

class MessagesFirebaseService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        getSharedPreferences("messages_som", MODE_PRIVATE).edit().putString("fcm_token", token).apply()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "Messages SOM"
        val body = message.notification?.body ?: message.data["body"] ?: "New message"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel("chat", "Messages", NotificationManager.IMPORTANCE_HIGH))
        }
        val notification = NotificationCompat.Builder(this, "chat")
            .setSmallIcon(R.drawable.ic_messages_som)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify((System.currentTimeMillis() and 0x7fffffff).toInt(), notification) }
    }
}
