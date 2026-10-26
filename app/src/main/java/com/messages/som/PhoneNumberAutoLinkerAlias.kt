package com.messages.som

import android.content.Context

object PhoneNumberAutoLinker {
    fun sync(context: Context, repository: AppMessagingRepository) =
        com.messages.som.data.PhoneNumberAutoLinker.sync(context, repository)
}
