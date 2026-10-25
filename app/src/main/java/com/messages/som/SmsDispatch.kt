package com.messages.som

import android.Manifest
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.provider.Telephony
import androidx.core.content.ContextCompat

/**
 * Native SIM/SMS transport.
 *
 * Important: this sends from the device SIM/subscription. It cannot spoof an
 * arbitrary Sender ID. Sender-ID traffic must go through the configured
 * server/provider transport documented in docs/SENDER_ID_API.md.
 */
object SmsDispatch {
    fun send(context: Context, address: String, body: String): Result<Unit> {
        val target = address.trim()
        val text = body.trim()
        if (!isWritableDestination(target)) return Result.failure(IllegalArgumentException("Invalid phone number"))
        if (text.isBlank()) return Result.failure(IllegalArgumentException("Message is empty"))
        if (!isDefaultSmsApp(context)) return Result.failure(SecurityException("Messages SOM must be the default SMS app"))
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return Result.failure(SecurityException("SEND_SMS permission is not granted"))
        }

        return runCatching {
            val manager = smsManager(context)
            val parts = manager.divideMessage(text)
            val sentIntent = PendingIntent.getBroadcast(
                context,
                (System.currentTimeMillis() and 0x7fffffff).toInt(),
                Intent(context, SmsStatusReceiver::class.java).setAction(SmsStatusReceiver.ACTION_SENT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val deliveryIntent = PendingIntent.getBroadcast(
                context,
                ((System.currentTimeMillis() + 1) and 0x7fffffff).toInt(),
                Intent(context, SmsStatusReceiver::class.java).setAction(SmsStatusReceiver.ACTION_DELIVERED),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (parts.size == 1) {
                manager.sendTextMessage(target, null, text, sentIntent, deliveryIntent)
            } else {
                val sentIntents = ArrayList<PendingIntent>(parts.size)
                val deliveryIntents = ArrayList<PendingIntent>(parts.size)
                repeat(parts.size) {
                    sentIntents.add(sentIntent)
                    deliveryIntents.add(deliveryIntent)
                }
                manager.sendMultipartTextMessage(target, null, parts, sentIntents, deliveryIntents)
            }

            context.contentResolver.insert(
                Telephony.Sms.Sent.CONTENT_URI,
                ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, target)
                    put(Telephony.Sms.BODY, text)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                    put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
                }
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun smsManager(context: Context): SmsManager {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val subscriptionId = runCatching {
                SubscriptionManager.getDefaultSmsSubscriptionId()
            }.getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                return SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }
        }
        return SmsManager.getDefault()
    }
}
