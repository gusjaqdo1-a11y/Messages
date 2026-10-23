package com.messages.som

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ThreadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val address = intent.getStringExtra("address") ?: ""
        val initialUnread = unreadCount(this, address)
        setContent { MessagesSomTheme { ThreadScreen(this, address, initialUnread) } }
    }
}

data class SmsItem(val id: Long, val body: String, val incoming: Boolean, val date: Long)

private fun loadThread(context: Context, address: String): List<SmsItem> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val out = mutableListOf<SmsItem>()
    context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.TYPE, Telephony.Sms.DATE), "address=?", arrayOf(address), "${Telephony.Sms.DATE} ASC")?.use { c ->
        while (c.moveToNext()) out.add(SmsItem(c.getLong(0), c.getString(1) ?: "", c.getInt(2) == Telephony.Sms.MESSAGE_TYPE_INBOX, c.getLong(3)))
    }
    return out
}

private fun unreadCount(context: Context, address: String): Int {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return 0
    return runCatching { context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID), "address=? AND read=0", arrayOf(address), null)?.use { it.count } ?: 0 }.getOrDefault(0)
}

private fun deleteMessage(context: Context, id: Long) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return
    runCatching { context.contentResolver.delete(Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id.toString()), null, null) }
}

private fun deleteThreadConversation(context: Context, address: String) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return
    runCatching { context.contentResolver.delete(Telephony.Sms.CONTENT_URI, "address=?", arrayOf(address)) }
}

private fun sendSms(context: Context, address: String, body: String, onDone: (Boolean) -> Unit) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) { onDone(false); return }
    runCatching {
        val manager = SmsManager.getDefault()
        val parts = manager.divideMessage(body)
        if (parts.size == 1) manager.sendTextMessage(address, null, body, null, null) else manager.sendMultipartTextMessage(address, null, parts, null, null)
        context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, ContentValues().apply { put(Telephony.Sms.ADDRESS, address); put(Telephony.Sms.BODY, body); put(Telephony.Sms.DATE, System.currentTimeMillis()); put(Telephony.Sms.READ, 1); put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT) })
        onDone(true)
    }.onFailure { onDone(false) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadScreen(activity: ThreadActivity, address: String, initialUnread: Int) {
    var text by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(loadThread(activity, address)) }
    var pendingUnread by remember { mutableStateOf(initialUnread) }
    var archived by remember { mutableStateOf(isArchived(activity, address)) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val canReply = isWritableDestination(address)
    val title = displayContactName(activity, address)

    LaunchedEffect(Unit) { while (true) { messages = loadThread(activity, address); delay(1500) } }

    Scaffold(
        containerColor = Color(0xFF090B0F),
        topBar = {
            TopAppBar(
                title = { Column { Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold); Text(if (title != address) address else if (canReply) "SMS conversation" else "Sender ID • no replies", color = Color(0xFF8B95A4), fontSize = 11.sp) } },
                navigationIcon = { IconButton({ activity.finish() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) } },
                actions = {
                    IconButton(onClick = { archived = !archived; setArchived(activity, address, archived); if (archived) activity.finish() }) { Icon(if (archived) Icons.Default.Unarchive else Icons.Default.Archive, "Archive", tint = Color.White) }
                    Box { IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "More", tint = Color.White) }; DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Mark as unread") }, onClick = { markThreadUnread(activity, address); pendingUnread = maxOf(1, pendingUnread); showMenu = false })
                        DropdownMenuItem(text = { Text("Delete conversation") }, onClick = { showMenu = false; showDeleteDialog = true })
                    } }
                }
            )
        },
        bottomBar = {
            if (canReply) Row(Modifier.fillMaxWidth().background(Color(0xFF0F1217)).padding(10.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f), placeholder = { Text("Text message") }, maxLines = 5, shape = RoundedCornerShape(25.dp))
                Spacer(Modifier.width(7.dp)); FilledIconButton(onClick = { val outgoing = text.trim(); if (outgoing.isNotBlank()) sendSms(activity, address, outgoing) { ok -> if (ok) { text = ""; messages = loadThread(activity, address); error = "" } else error = "Message could not be sent" } }, enabled = text.isNotBlank(), modifier = Modifier.size(52.dp)) { Icon(Icons.Default.Send, "Send") }
            } else Surface(color = Color(0xFF171B22)) { Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Block, null, tint = Color(0xFFFFC978)); Spacer(Modifier.width(10.dp)); Text("This Sender ID does not accept replies.", color = Color(0xFFD0BFA7)) } }
        }
    ) { p ->
        Box(Modifier.fillMaxSize().padding(p)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().background(Color(0xFF090B0F)), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                itemsIndexed(messages, key = { _, m -> m.id }) { _, message -> MessageBubble(message, onCopy = { clipboard.setText(AnnotatedString(message.body)) }, onDelete = { deleteMessage(activity, message.id); messages = loadThread(activity, address) }, onLink = { url -> runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }) }
            }
            if (pendingUnread > 0) Surface(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), color = Color(0xFF222A3A), shadowElevation = 8.dp, shape = RoundedCornerShape(24.dp)) {
                Row(Modifier.padding(horizontal = 7.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (messages.isNotEmpty()) { val target = maxOf(0, messages.size - pendingUnread); activity.lifecycleScope.launch { listState.animateScrollToItem(target); markThreadRead(activity, address); pendingUnread = 0 } } }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Jump to unread", tint = Color.White) }
                    Text(if (pendingUnread > 99) "99+ new messages" else "$pendingUnread new messages", color = Color.White, fontSize = 13.sp)
                }
            }
            if (error.isNotBlank()) Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = 82.dp), color = Color(0xFF3A2020), shape = RoundedCornerShape(16.dp)) { Text(error, color = Color(0xFFFFB4AB), modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
        }
    }

    if (showDeleteDialog) AlertDialog(onDismissRequest = { showDeleteDialog = false }, title = { Text("Delete conversation?") }, text = { Text("All messages in this conversation will be removed from the device.") }, confirmButton = { TextButton(onClick = { deleteThreadConversation(activity, address); showDeleteDialog = false; activity.finish() }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } })
}

private fun markThreadUnread(context: Context, address: String) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return
    runCatching { context.contentResolver.update(Telephony.Sms.CONTENT_URI, ContentValues().apply { put(Telephony.Sms.READ, 0) }, "address=?", arrayOf(address)) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(message: SmsItem, onCopy: () -> Unit, onDelete: () -> Unit, onLink: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val match = Regex("https?://\\S+|www\\.\\S+", RegexOption.IGNORE_CASE).find(message.body)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.incoming) Arrangement.Start else Arrangement.End) {
        Box {
            Surface(color = if (message.incoming) Color(0xFF1B2028) else Color(0xFF274A70), shape = RoundedCornerShape(22.dp, 22.dp, if (message.incoming) 22.dp else 6.dp, if (message.incoming) 6.dp else 22.dp)) {
                Column(Modifier.widthIn(max = 330.dp).combinedClickable(onClick = {}, onLongClick = { menu = true }).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    val annotated = androidx.compose.ui.text.buildAnnotatedString {
                        var cursor = 0
                        Regex("https?://\\S+|www\\.\\S+", RegexOption.IGNORE_CASE).findAll(message.body).forEach { m ->
                            append(message.body.substring(cursor, m.range.first))
                            withStyle(SpanStyle(color = Color(0xFFA8C7FA), textDecoration = TextDecoration.Underline)) { append(m.value) }
                            cursor = m.range.last + 1
                        }
                        append(message.body.substring(cursor))
                    }
                    Text(annotated, color = Color.White, fontSize = 16.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        if (match != null) { AssistChip(onClick = { onLink(if (match.value.startsWith("http")) match.value else "https://${match.value}") }, label = { Text("Open link", fontSize = 10.sp) }, leadingIcon = { Icon(Icons.Default.Link, null, Modifier.size(14.dp)) }); Spacer(Modifier.width(5.dp)) }
                        IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ContentCopy, "Copy", tint = Color(0xFFB9C5D6), modifier = Modifier.size(15.dp)) }
                        Spacer(Modifier.width(4.dp)); Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.date)), color = Color(0xFF9AA4B2), fontSize = 10.sp)
                    }
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) { DropdownMenuItem(text = { Text("Copy") }, onClick = { onCopy(); menu = false }); DropdownMenuItem(text = { Text("Delete message") }, onClick = { onDelete(); menu = false }) }
        }
    }
}
