package com.messages.som

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MessagesSomTheme { MessagesHome(this) } }
    }
}

data class SmsThread(val address: String, val name: String, val preview: String, val date: Long, val count: Int, val unreadCount: Int, val hasLink: Boolean)
data class SmsPermissionState(val granted: Boolean, val missing: List<String>)

private val URL_PATTERN = Pattern.compile("https?://\\S+|www\\.\\S+", Pattern.CASE_INSENSITIVE)
fun containsLink(text: String): Boolean = URL_PATTERN.matcher(text).find()

fun isWritableDestination(address: String): Boolean {
    val value = address.trim()
    if (value.isBlank()) return false
    if (value.any { it.isLetter() } && value.none { it.isDigit() }) return false
    return value.count { it.isDigit() } >= 3 && value.none { it.isWhitespace() }
}

fun displayContactName(context: Context, address: String): String {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return address
    return runCatching {
        context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), "${ContactsContract.CommonDataKinds.Phone.NUMBER}=?", arrayOf(address), null)?.use { if (it.moveToFirst()) it.getString(0).orEmpty() else "" }.orEmpty().ifBlank { address }
    }.getOrDefault(address)
}

private fun loadThreads(context: Context): List<SmsThread> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val out = linkedMapOf<String, SmsThread>()
    context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.READ), null, null, "${Telephony.Sms.DATE} DESC")?.use { c ->
        val ai = c.getColumnIndex(Telephony.Sms.ADDRESS); val bi = c.getColumnIndex(Telephony.Sms.BODY); val di = c.getColumnIndex(Telephony.Sms.DATE); val ri = c.getColumnIndex(Telephony.Sms.READ)
        while (c.moveToNext() && out.size < 500) {
            val address = c.getString(ai) ?: "Unknown"; val body = c.getString(bi) ?: ""; val current = out[address]
            if (current == null) out[address] = SmsThread(address, displayContactName(context, address), body, c.getLong(di), 1, if (ri >= 0 && c.getInt(ri) == 0) 1 else 0, containsLink(body))
            else out[address] = current.copy(count = current.count + 1, unreadCount = current.unreadCount + if (ri >= 0 && c.getInt(ri) == 0) 1 else 0)
        }
    }
    return out.values.toList()
}

private fun permissionState(context: Context): SmsPermissionState {
    val wanted = buildList {
        add(Manifest.permission.READ_SMS); add(Manifest.permission.SEND_SMS); add(Manifest.permission.RECEIVE_SMS); add(Manifest.permission.RECEIVE_MMS); add(Manifest.permission.RECEIVE_WAP_PUSH); add(Manifest.permission.READ_CONTACTS); add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val missing = wanted.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
    return SmsPermissionState(missing.isEmpty(), missing)
}

private fun isDefaultSmsApp(context: Context): Boolean = try {
    if (Build.VERSION.SDK_INT >= 29) context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_SMS) == true else Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
} catch (_: Exception) { Telephony.Sms.getDefaultSmsPackage(context) == context.packageName }

private fun requestDefaultSmsRole(activity: MainActivity) {
    try {
        if (Build.VERSION.SDK_INT >= 29) {
            val roleManager = activity.getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true && !roleManager.isRoleHeld(RoleManager.ROLE_SMS)) { activity.startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS), 7001); return }
        }
        if (Build.VERSION.SDK_INT >= 19) {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply { putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, activity.packageName) }
            if (intent.resolveActivity(activity.packageManager) != null) { activity.startActivity(intent); return }
        }
    } catch (_: Exception) { }
    val settingsIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    if (settingsIntent.resolveActivity(activity.packageManager) != null) activity.startActivity(settingsIntent)
}

@Composable
private fun MessagesHome(activity: MainActivity) {
    var threads by remember { mutableStateOf(loadThreads(activity)) }
    var query by rememberSaveable { mutableStateOf("") }
    var roleHeld by remember { mutableStateOf(isDefaultSmsApp(activity)) }
    var permissionsState by remember { mutableStateOf(permissionState(activity)) }
    var rolePromptShown by rememberSaveable { mutableStateOf(false) }
    var permissionPromptShown by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionsState = permissionState(activity); roleHeld = isDefaultSmsApp(activity); threads = loadThreads(activity) }
    fun refreshSetup() { roleHeld = isDefaultSmsApp(activity); permissionsState = permissionState(activity); threads = loadThreads(activity) }
    fun requestPermissions() { permissionsState = permissionState(activity); if (permissionsState.missing.isNotEmpty()) { permissionPromptShown = true; permissionsLauncher.launch(permissionsState.missing.toTypedArray()) } }
    fun requestSetup() { refreshSetup(); if (!roleHeld) requestDefaultSmsRole(activity) else if (!permissionsState.granted) requestPermissions() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refreshSetup() }
        lifecycleOwner.lifecycle.addObserver(observer); onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(450); refreshSetup()
        if (!isDefaultSmsApp(activity) && !rolePromptShown) { rolePromptShown = true; requestDefaultSmsRole(activity) }
        else if (isDefaultSmsApp(activity) && permissionState(activity).missing.isNotEmpty() && !permissionPromptShown) requestPermissions()
    }

    val filtered = threads.filter { !isArchived(activity, it.address) && (it.address.contains(query, true) || it.name.contains(query, true) || it.preview.contains(query, true)) }
    Scaffold(containerColor = Color(0xFF080A0E), floatingActionButton = {
        FloatingActionButton(onClick = { activity.startActivity(Intent(activity, ComposeActivity::class.java)) }, containerColor = Color(0xFF9BB7FF), contentColor = Color(0xFF080A0E), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Default.Edit, "New message") }
    }) { pad ->
        Column(Modifier.fillMaxSize().background(Color(0xFF080A0E)).padding(pad)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Messages SOM", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White); Text(if (roleHeld) "Your messages, all in one place" else "Complete setup to manage SMS", color = Color(0xFF98A2B3), fontSize = 13.sp) }
                IconButton(onClick = { activity.startActivity(Intent(activity, ArchiveActivity::class.java)) }) { Icon(Icons.Default.Archive, "Archived", tint = Color.White) }
                IconButton(onClick = { requestSetup() }) { Icon(Icons.Default.Settings, "Messaging settings", tint = Color.White) }
            }
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), singleLine = true, shape = RoundedCornerShape(26.dp), leadingIcon = { Icon(Icons.Default.Search, "Search") }, placeholder = { Text("Search messages and contacts") }, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color(0xFF9BB7FF), unfocusedContainerColor = Color(0xFF171B22), focusedContainerColor = Color(0xFF171B22), unfocusedTextColor = Color.White, focusedTextColor = Color.White, cursorColor = Color(0xFF9BB7FF)))
            if (!roleHeld || !permissionsState.granted) SetupCard(roleHeld, permissionsState.granted, permissionsState.missing.size, { requestSetup() }, { requestPermissions() })
            HorizontalDivider(color = Color(0xFF242A33))
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.MarkUnreadChatAlt, null, tint = Color(0xFF9BB7FF), modifier = Modifier.size(62.dp)); Spacer(Modifier.height(14.dp)); Text(if (query.isBlank()) "No conversations yet" else "No results", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Text(if (roleHeld) "Your SMS conversations will appear here" else "Set Messages SOM as your default SMS app first", color = Color(0xFF9AA4B2)) } }
            } else LazyColumn(contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { items(filtered, key = { it.address }) { thread -> ConversationRow(activity, thread, onOpen = { activity.startActivity(Intent(activity, ThreadActivity::class.java).putExtra("address", thread.address)) }, onRefresh = { threads = loadThreads(activity) }) } }
        }
    }
}

@Composable
private fun SetupCard(roleHeld: Boolean, permissionsGranted: Boolean, missingCount: Int, onSetup: () -> Unit, onPermissions: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF171B22)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Surface(modifier = Modifier.size(46.dp), shape = CircleShape, color = Color(0xFF222A3A)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Security, null, tint = Color(0xFF9BB7FF)) } }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Finish Messages SOM setup", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold); Text(if (!roleHeld) "Make Messages SOM your default SMS app to manage your messages." else "Allow the permissions Messages SOM needs for SMS, contacts and notifications.", color = Color(0xFF9AA4B2), fontSize = 12.sp) } }
            Spacer(Modifier.height(14.dp)); SetupRow("Default SMS app", roleHeld); SetupRow("SMS & MMS access", permissionsGranted); SetupRow("Contacts access", permissionsGranted); SetupRow("Notifications", permissionsGranted); Spacer(Modifier.height(14.dp))
            Button(onClick = onSetup, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) { Icon(if (roleHeld) Icons.Default.Security else Icons.Default.Message, null); Spacer(Modifier.width(8.dp)); Text(if (roleHeld) "Allow required permissions" else "Set as default SMS app", fontWeight = FontWeight.SemiBold) }
            if (roleHeld && !permissionsGranted) TextButton(onClick = onPermissions, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Request remaining permissions${if (missingCount > 0) " ($missingCount)" else ""}") }
        }
    }
}

@Composable private fun SetupRow(title: String, complete: Boolean) { Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (complete) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (complete) Color(0xFF75D99A) else Color(0xFF7F8998), modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text(title, color = if (complete) Color.White else Color(0xFFB1B8C4), fontSize = 13.sp) } }

@Composable
private fun ConversationRow(activity: MainActivity, t: SmsThread, onOpen: () -> Unit, onRefresh: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onOpen).padding(horizontal = 10.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF202630)), contentAlignment = Alignment.Center) { Text((t.name.firstOrNull() ?: t.address.firstOrNull() ?: '?').uppercase(), color = Color(0xFF9BB7FF), fontWeight = FontWeight.Bold, fontSize = 20.sp) }
        Spacer(Modifier.width(13.dp)); Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(t.name, color = Color.White, fontSize = 17.sp, fontWeight = if (t.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); if (t.name != t.address) Text(t.address, color = Color(0xFF727D8D), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (t.unreadCount > 0) { Surface(color = Color(0xFF9BB7FF), shape = CircleShape) { Text(if (t.unreadCount > 99) "99+" else t.unreadCount.toString(), color = Color(0xFF0B0E13), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)) }; Spacer(Modifier.width(6.dp)) }
                Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t.date)), color = Color(0xFF7F8998), fontSize = 11.sp)
                Box { IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, "Conversation actions", tint = Color(0xFF9AA4B2), modifier = Modifier.size(18.dp)) }; DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (t.unreadCount > 0) "Mark as read" else "Mark as unread") }, onClick = { if (t.unreadCount > 0) markThreadRead(activity, t.address) else markThreadUnread(activity, t.address); menu = false; onRefresh() })
                    DropdownMenuItem(text = { Text("Archive") }, onClick = { setArchived(activity, t.address, true); menu = false; onRefresh() })
                    DropdownMenuItem(text = { Text("Delete conversation") }, onClick = { deleteThread(activity, t.address); menu = false; onRefresh() })
                } }
            }
            Spacer(Modifier.height(3.dp)); LinkHighlightedText(t.preview.replace('\n', ' '), t.hasLink, if (t.unreadCount > 0) Color.White else Color(0xFF9AA4B2))
        }
    }
}

fun markThreadRead(context: Context, address: String) { if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return; runCatching { context.contentResolver.update(Telephony.Sms.CONTENT_URI, android.content.ContentValues().apply { put(Telephony.Sms.READ, 1) }, "address=?", arrayOf(address)) } }
fun markThreadUnread(context: Context, address: String) { if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return; runCatching { context.contentResolver.update(Telephony.Sms.CONTENT_URI, android.content.ContentValues().apply { put(Telephony.Sms.READ, 0) }, "address=?", arrayOf(address)) } }
fun deleteThread(context: Context, address: String) { if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return; runCatching { context.contentResolver.delete(Telephony.Sms.CONTENT_URI, "address=?", arrayOf(address)) } }

@Composable
private fun LinkHighlightedText(text: String, hasLink: Boolean, baseColor: Color) {
    val annotated = remember(text, baseColor) { buildLinkAnnotatedString(text, baseColor) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (hasLink) { Surface(color = Color(0xFF202C48), shape = RoundedCornerShape(7.dp)) { Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Link, null, tint = Color(0xFF9BB7FF), modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)); Text("LINK", color = Color(0xFFB8C9FF), fontSize = 9.sp, fontWeight = FontWeight.Bold) } }; Spacer(Modifier.width(7.dp)) }
        Text(annotated, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = baseColor, modifier = Modifier.weight(1f))
    }
}

private fun buildLinkAnnotatedString(text: String, baseColor: Color) = buildAnnotatedString {
    var last = 0; val m = URL_PATTERN.matcher(text)
    while (m.find()) { append(text.substring(last, m.start())); withStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFF9BB7FF), fontWeight = FontWeight.SemiBold, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) { append(m.group()) }; last = m.end() }
    append(text.substring(last))
}
