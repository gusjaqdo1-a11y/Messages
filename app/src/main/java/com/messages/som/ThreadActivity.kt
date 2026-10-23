package com.messages.som

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ThreadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val address = intent.getStringExtra("address") ?: ""
        markThreadRead(address)
        setContent { MessagesSomTheme { ThreadScreen(this, address) } }
    }

    private fun markThreadRead(address: String) {
        if (address.isBlank() || ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return
        runCatching {
            contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                ContentValues().apply { put(Telephony.Sms.READ, 1) },
                "address=? AND read=0",
                arrayOf(address)
            )
        }
    }
}

data class SmsItem(val body: String, val incoming: Boolean, val date: Long)

private fun loadThread(context: android.content.Context, address: String): List<SmsItem> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return emptyList()
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
    var archived by remember { mutableStateOf(isArchived(activity, address)) }
    val canReply = isWritableDestination(address)
    val clipboard = LocalClipboardManager.current
    Scaffold(
        containerColor = Color(0xFF090B0F),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(address, color = Color.White, fontSize = 17.sp)
                        Text(if (canReply) "SMS conversation" else "Sender ID • no replies", color = Color(0xFF8B95A4), fontSize = 11.sp)
                    }
                },
                navigationIcon = { IconButton({ activity.finish() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) } },
                actions = {
                    IconButton(onClick = {
                        archived = !archived
                        setArchived(activity, address, archived)
                    }) {
                        Icon(if (archived) Icons.Default.Unarchive else Icons.Default.Archive, if (archived) "Unarchive" else "Archive", tint = Color.White)
                    }
                }
            )
        },
        bottomBar = {
            if (canReply) Row(Modifier.fillMaxWidth().background(Color(0xFF0F1217)).padding(10.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("Type a message…") }, maxLines = 5, shape = RoundedCornerShape(24.dp))
                Spacer(Modifier.width(6.dp))
                FilledIconButton(onClick = {
                    if (text.isNotBlank() && ContextCompat.checkSelfPermission(activity, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                        SmsManager.getDefault().sendTextMessage(address, null, text.trim(), null, null)
                        text = ""
                        messages = loadThread(activity, address)
                    }
                }, enabled = text.isNotBlank()) { Icon(Icons.Default.Send, "Send") }
            } else {
                Surface(color = Color(0xFF171B22)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Link, null, tint = Color(0xFFFFC978)); Spacer(Modifier.width(10.dp)); Text("This Sender ID does not accept replies.", color = Color(0xFFD0BFA7)) } }
            }
        }
    ) { p ->
        LazyColumn(Modifier.fillMaxSize().background(Color(0xFF090B0F)).padding(p).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(messages) { message ->
                MessageBubble(message, onCopy = { clipboard.setText(AnnotatedString(message.body)) }, onLink = { url -> activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))) })
            }
        }
    }
}

@Composable
private fun MessageBubble(message: SmsItem, onCopy: () -> Unit, onLink: (String) -> Unit) {
    val hasLink = containsLink(message.body)
    val url = Regex("https?://\\S+|www\\.\\S+", RegexOption.IGNORE_CASE).find(message.body)?.value
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.incoming) Arrangement.Start else Arrangement.End) {
        Surface(color = if (message.incoming) Color(0xFF1B2028) else Color(0xFF274A70), shape = RoundedCornerShape(22.dp, 22.dp, if (message.incoming) 22.dp else 6.dp, if (message.incoming) 6.dp else 22.dp)) {
            Column(Modifier.widthIn(max = 310.dp).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(message.body, color = Color.White, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    if (hasLink) {
                        AssistChip(onClick = { url?.let { onLink(if (it.startsWith("http")) it else "https://$it") } }, label = { Text("Open link", fontSize = 10.sp) }, leadingIcon = { Icon(Icons.Default.Link, null, Modifier.size(14.dp)) })
                        Spacer(Modifier.width(5.dp))
                    }
                    IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ContentCopy, "Copy", tint = Color(0xFFB9C5D6), modifier = Modifier.size(15.dp)) }
                    Spacer(Modifier.width(4.dp))
                    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.date)), color = Color(0xFF9AA4B2), fontSize = 10.sp)
                }
            }
        }
    }
}
