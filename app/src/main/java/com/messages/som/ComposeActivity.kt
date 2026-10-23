package com.messages.som

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.SmsManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class ComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MessagesSomTheme { ComposeScreen(this, intent.getStringExtra("address") ?: "") } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeScreen(activity: Activity, initial: String) {
    var address by remember { mutableStateOf(initial) }
    var body by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) activity.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { c -> if (c.moveToFirst()) address = c.getString(0) }
    }
    val canReply = isWritableDestination(address)
    Scaffold(
        containerColor = Color(0xFF090B0F),
        topBar = {
            TopAppBar(
                title = { Text(if (initial.isBlank()) "New message" else "Reply", color = Color.White) },
                navigationIcon = { IconButton({ activity.finish() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) } },
                actions = { IconButton({ picker.launch(null) }) { Icon(Icons.Default.Contacts, "Contacts", tint = Color.White) } }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().background(Color(0xFF090B0F)).padding(pad).padding(horizontal = 18.dp, vertical = 10.dp)) {
            if (address.isNotBlank() && !canReply) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF241D16)), shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = Color(0xFFFFC978))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Sender ID", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text("This sender cannot receive replies. The message keyboard is disabled.", color = Color(0xFFD0BFA7), fontSize = MaterialTheme.typography.bodySmall.fontSize)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("To") },
                placeholder = { Text("Phone number or recipient") },
                trailingIcon = { IconButton({ picker.launch(null) }) { Icon(Icons.Default.PersonAdd, "Choose contact") } }
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
                enabled = canReply,
                placeholder = { Text(if (canReply) "Type a message…" else "Replies are unavailable for this Sender ID") },
                shape = RoundedCornerShape(22.dp),
                maxLines = 8,
                minLines = 5
            )
            if (info.isNotBlank()) Text(info, color = Color(0xFF9BB7FF), modifier = Modifier.padding(8.dp))
            if (canReply) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("SMS", color = Color(0xFF7F8998), modifier = Modifier.weight(1f))
                    FilledIconButton(
                        onClick = {
                            if (address.isNotBlank() && body.isNotBlank()) {
                                if (androidx.core.content.ContextCompat.checkSelfPermission(activity, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                                    SmsManager.getDefault().sendTextMessage(address.trim(), null, body, null, null)
                                    body = ""
                                    info = "Message sent"
                                } else info = "SMS permission required"
                            }
                        },
                        enabled = address.isNotBlank() && body.isNotBlank()
                    ) { Icon(Icons.Default.Send, "Send") }
                }
            }
        }
    }
}
