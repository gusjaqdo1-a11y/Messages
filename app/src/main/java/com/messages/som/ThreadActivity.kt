package com.messages.som

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class ThreadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val address = intent.getStringExtra("address") ?: ""
        setContent { MessagesSomTheme { ThreadScreen(this, address) } }
    }
}

data class SmsItem(val body: String, val incoming: Boolean, val date: Long)

private fun loadThread(context: android.content.Context, address: String): List<SmsItem> {
    if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val out = mutableListOf<SmsItem>()
    context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.BODY, Telephony.Sms.TYPE, Telephony.Sms.DATE), "address=?", arrayOf(address), "${Telephony.Sms.DATE} ASC")?.use { c ->
        while (c.moveToNext()) out.add(SmsItem(c.getString(0) ?: "", c.getInt(1) == Telephony.Sms.MESSAGE_TYPE_INBOX, c.getLong(2)))
    }
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadScreen(activity: ThreadActivity, address: String) {
    var text by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(loadThread(activity, address)) }
    Scaffold(
        containerColor = Color(0xFF0B0D10),
        topBar = { TopAppBar(title = { Text(address, color = Color.White) }, navigationIcon = { IconButton({ activity.finish() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) } }) },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(10.dp)) {
                OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("Message") })
                IconButton({
                    if (text.isNotBlank() && androidx.core.content.ContextCompat.checkSelfPermission(activity, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                        SmsManager.getDefault().sendTextMessage(address, null, text, null, null); text = ""; messages = loadThread(activity, address)
                    }
                }) { Icon(Icons.Default.Send, "Send", tint = Color(0xFF8AB4F8)) }
            }
        }
    ) { p ->
        LazyColumn(Modifier.fillMaxSize().background(Color(0xFF0B0D10)).padding(p).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.incoming) Arrangement.Start else Arrangement.End) {
                    Surface(color = if (message.incoming) Color(0xFF20252B) else Color(0xFF284A6B), shape = MaterialTheme.shapes.large) {
                        Text(message.body, color = Color.White, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }
}
