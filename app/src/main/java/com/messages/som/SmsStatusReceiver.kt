package com.messages.som

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class SmsStatusReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SENT = "com.messages.som.SMS_SENT"
        const val ACTION_DELIVERED = "com.messages.som.SMS_DELIVERED"
        private const val CHANNEL = "sms_status"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val message = when (intent.action) {
            ACTION_SENT -> if (resultCode == android.app.Activity.RESULT_OK) "Message sent" else "Message failed to send (code $resultCode)"
            ACTION_DELIVERED -> "Message delivered"
            else -> return
        }
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "SMS status", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(com.messages.som.R.drawable.ic_messages_som)
            .setContentTitle("Messages SOM")
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        context.getSystemService(NotificationManager::class.java).notify((System.currentTimeMillis() and 0x7fffffff).toInt(), notification)
    }
}
