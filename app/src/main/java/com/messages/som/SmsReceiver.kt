package com.messages.som

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class SmsReceiver: BroadcastReceiver(){ override fun onReceive(context:Context,intent:Intent){ if(intent.action!=Telephony.Sms.Intents.SMS_DELIVER_ACTION)return; val msgs=Telephony.Sms.Intents.getMessagesFromIntent(intent); val from=msgs.firstOrNull()?.displayOriginatingAddress?:"New message"; val text=msgs.joinToString(""){it.messageBody?:""}; val nm=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager; if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel("sms","Messages",NotificationManager.IMPORTANCE_HIGH)); val n=NotificationCompat.Builder(context,"sms").setSmallIcon(com.messages.som.R.drawable.ic_messages_som).setContentTitle(from).setContentText(text).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build(); try{NotificationManagerCompat.from(context).notify((System.currentTimeMillis()%100000).toInt(),n)}catch(_:SecurityException){ } } }
