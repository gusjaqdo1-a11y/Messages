package com.messages.som.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

object PhoneNumberAutoLinker {
    fun sync(context: Context, repository: AppMessagingRepository) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED) return
        val number = if (Build.VERSION.SDK_INT >= 33) runCatching {
            val manager = context.getSystemService(SubscriptionManager::class.java)
            manager.activeSubscriptionInfoList.orEmpty().asSequence()
                .mapNotNull { manager.getPhoneNumber(it.subscriptionId) }
                .firstOrNull { it.isNotBlank() }
        }.getOrNull() else runCatching {
            context.getSystemService(TelephonyManager::class.java).line1Number
        }.getOrNull()
        val normalized = number?.trim()?.replace(Regex("[^+0-9]"), "") ?: return
        if (Regex("^\\+?[1-9]\\d{6,14}$").matches(normalized)) repository.updatePhoneNumber(normalized) { }
    }
}
