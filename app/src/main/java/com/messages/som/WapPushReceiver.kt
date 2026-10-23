package com.messages.som

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WapPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // MMS/WAP-PUSH delivery is claimed by the default SMS app.
        // The message provider can be refreshed when the main UI resumes.
    }
}
