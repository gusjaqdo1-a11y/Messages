package com.messages.som

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    var sending by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) activity.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { c -> if (c.moveToFirst()) address = c.getString(0) }
    }
    val canReply = isWritableDestination(address)
    val chars = body.length
    val segments = if (chars == 0) 0 else ((chars - 1) / 160) + 1
    Scaffold(containerColor = Color(0xFF090B0F), topBar = {
        TopAppBar(
            title = { Column { Text(if (initial.isBlank()) "New message" else "Reply", color = Color.White, fontWeight = FontWeight.SemiBold); Text("SMS", color = Color(0xFF8B95A4), fontSize = 11.sp) } },
            navigationIcon = { IconButton({ activity.finish() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) } },
            actions = { IconButton({ picker.launch(null) }) { Icon(Icons.Default.Contacts, "Choose contact", tint = Color.White) } }
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().background(Color(0xFF090B0F)).padding(pad).padding(horizontal = 16.dp, vertical = 10.dp)) {
            Surface(color = Color(0xFF151A21), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Recipient", color = Color(0xFF8B95A4), fontSize = 11.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = address, onValueChange = { address = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Phone number or contact") }, shape = RoundedCornerShape(18.dp))
                        Spacer(Modifier.width(6.dp)); FilledTonalIconButton(onClick = { picker.launch(null) }) { Icon(Icons.Default.PersonAdd, "Choose contact") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (address.isNotBlank() && !canReply) {
                Surface(color = Color(0xFF241D16), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Block, null, tint = Color(0xFFFFC978)); Spacer(Modifier.width(10.dp)); Column {
                            Text("Sender ID / invalid recipient", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("This destination cannot receive a native SIM reply.", color = Color(0xFFD0BFA7), fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Surface(color = Color(0xFF11151B), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                Box(Modifier.fillMaxSize()) {
                    OutlinedTextField(value = body, onValueChange = { if (it.length <= 1000) body = it }, modifier = Modifier.fillMaxSize().padding(8.dp), enabled = canReply, placeholder = { Text(if (canReply) "Type a message…" else "Replies are unavailable for this destination") }, shape = RoundedCornerShape(20.dp), maxLines = 12)
                    Row(Modifier.align(Alignment.BottomEnd).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (chars > 0) Text(if (segments > 1) "$chars • $segments SMS" else "$chars/160", color = Color(0xFF7F8998), fontSize = 11.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(info, color = if (info.startsWith("Message sent")) Color(0xFF75D99A) else Color(0xFFFFB4AB), fontSize = 12.sp, modifier = Modifier.weight(1f))
                FilledIconButton(onClick = {
                    if (address.isBlank() || body.isBlank()) info = "Enter a recipient and message"
                    else if (!isDefaultSmsApp(activity)) info = "Set Messages SOM as the default SMS app first"
                    else {
                        sending = true
                        val result = SmsDispatch.send(activity, address, body)
                        sending = false
                        if (result.isSuccess) { body = ""; info = "Message sent — delivery status will follow" }
                        else info = result.exceptionOrNull()?.message ?: "Message failed to send"
                    }
                }, enabled = canReply && body.isNotBlank() && !sending, modifier = Modifier.size(54.dp)) {
                    if (sending) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Send, "Send message")
                }
            }
        }
    }
}
