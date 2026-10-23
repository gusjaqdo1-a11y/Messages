package com.messages.som.data

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MessagesFirebaseService: FirebaseMessagingService(){ override fun onNewToken(token:String){ Log.d("MessagesSOM","FCM token refreshed") } override fun onMessageReceived(message:RemoteMessage){ Log.d("MessagesSOM","FCM message received: ${message.data.keys}") } }
