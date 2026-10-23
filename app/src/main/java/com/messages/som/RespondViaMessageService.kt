package com.messages.som

import android.app.Service
import android.content.Intent
import android.os.IBinder

class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Reserved for quick-reply/respond-via-message handling.
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
