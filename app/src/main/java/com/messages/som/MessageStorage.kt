package com.messages.som

import android.content.Context

private const val PREFS = "messages_som_state"
private const val ARCHIVED = "archived_addresses"

fun isArchived(context: Context, address: String): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getStringSet(ARCHIVED, emptySet())
        ?.contains(address) == true

fun setArchived(context: Context, address: String, archived: Boolean) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val current = prefs.getStringSet(ARCHIVED, emptySet())?.toMutableSet() ?: mutableSetOf()
    if (archived) current.add(address) else current.remove(address)
    prefs.edit().putStringSet(ARCHIVED, current).apply()
}

fun archivedAddresses(context: Context): Set<String> =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getStringSet(ARCHIVED, emptySet())?.toSet() ?: emptySet()
