package com.messages.som.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

object DevicePhoneNumber {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    fun read(context: Context): String? {
        if (!hasPermission(context)) return null
        val candidates = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                val sm = context.getSystemService(SubscriptionManager::class.java)
                sm.activeSubscriptionInfoList.orEmpty().forEach { info ->
                    sm.getPhoneNumber(info.subscriptionId)?.takeIf { it.isNotBlank() }?.let(candidates::add)
                }
            }
        }
        runCatching {
            val tm = context.getSystemService(TelephonyManager::class.java)
            tm.line1Number?.takeIf { it.isNotBlank() }?.let(candidates::add)
        }
        return candidates.asSequence()
            .map { it.trim().replace(Regex("[^+0-9]"), "") }
            .firstOrNull { Regex("^\\+?[1-9]\\d{6,14}$").matches(it) }
    }
}
