package com.messages.som

import android.content.Context

object ContextCompat {
    fun checkSelfPermission(context: Context, permission: String): Int =
        androidx.core.content.ContextCompat.checkSelfPermission(context, permission)
}
