package com.messages.som

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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

data class SmsThread(
    val address: String,
    val preview: String,
    val date: Long,
    val count: Int,
    val hasLink: Boolean,
    val unread: Boolean
)

data class SmsPermissionState(val granted: Boolean, val missing: List<String>)

private val URL_PATTERN = Pattern.compile("https?://\\S+|www\\.\\S+", Pattern.CASE_INSENSITIVE)

fun containsLink(text: String): Boolean = URL_PATTERN.matcher(text).find()

fun isWritableDestination(address: String): Boolean {
    val value = address.trim()
    if (value.isBlank()) return false
    if (value.any { it.isLetter() } && value.none { it.isDigit() }) return false
    val digits = value.count { it.isDigit() }
    return digits >= 3 && value.none { it.isWhitespace() }
}

private fun loadThreads(context: Context): List<SmsThread> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val out = linkedMapOf<String, SmsThread>()
    context.contentResolver.query(
        Telephony.Sms.CONTENT_URI,
        arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.READ),
        null, null, "${Telephony.Sms.DATE} DESC"
    )?.use { c ->
        val ai = c.getColumnIndex(Telephony.Sms.ADDRESS)
        val bi = c.getColumnIndex(Telephony.Sms.BODY)
        val di = c.getColumnIndex(Telephony.Sms.DATE)
        val ri = c.getColumnIndex(Telephony.Sms.READ)
        while (c.moveToNext() && out.size < 200) {
            val address = c.getString(ai) ?: "Unknown"
            if (!out.containsKey(address)) {
                val body = c.getString(bi) ?: ""
                out[address] = SmsThread(address, body, c.getLong(di), 1, containsLink(body), ri >= 0 && c.getInt(ri) == 0)
            }
        }
    }
    return out.values.toList()
}

private fun permissionState(context: Context): SmsPermissionState {
    val wanted = buildList {
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val missing = wanted.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
    return SmsPermissionState(missing.isEmpty(), missing)
}

@Composable
private fun MessagesHome(activity: MainActivity) {
    var threads by remember { mutableStateOf(loadThreads(activity)) }
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    var roleHeld by remember { mutableStateOf(isDefaultSmsApp(activity)) }
    var permissionState by remember { mutableStateOf(permissionState(activity)) }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionState = permissionState(activity)
        threads = loadThreads(activity)
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        roleHeld = isDefaultSmsApp(activity)
        permissionState = permissionState(activity)
        if (roleHeld && permissionState.missing.isNotEmpty()) permissions.launch(permissionState.missing.toTypedArray())
        threads = loadThreads(activity)
    }

    fun requestControl() {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = activity.getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_SMS) && !rm.isRoleHeld(RoleManager.ROLE_SMS)) {
                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
                return
            }
        }
        if (permissionState.missing.isNotEmpty()) permissions.launch(permissionState.missing.toTypedArray())
    }

    LaunchedEffect(Unit) {
        if (!roleHeld) requestControl()
        else if (permissionState.missing.isNotEmpty()) permissions.launch(permissionState.missing.toTypedArray())
    }

    val filtered = threads.filter {
        val matchesSearch = it.address.contains(query, true) || it.preview.contains(query, true)
        val matchesTab = when (tab) { 1 -> it.hasLink; 2 -> !it.hasLink; else -> true }
        matchesSearch && matchesTab
    }

    Scaffold(
        containerColor = Color(0xFF090B0F),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { activity.startActivity(Intent(activity, ComposeActivity::class.java)) },
                containerColor = Color(0xFF9BB7FF), contentColor = Color(0xFF0A0C10),
                shape = RoundedCornerShape(18.dp)
            ) { Icon(Icons.Default.Edit, "New message") }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().background(Color(0xFF090B0F)).padding(pad)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Messages SOM", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(if (roleHeld) "Your default messaging app" else "Finish setup to control SMS", color = Color(0xFF9AA4B2), fontSize = 13.sp)
                }
                IconButton(onClick = { requestControl() }) { Icon(Icons.Default.Settings, "Messaging settings", tint = Color.White) }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                singleLine = true,
                shape = RoundedCornerShape(26.dp),
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                placeholder = { Text("Search messages and contacts") },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color(0xFF9BB7FF),
                    unfocusedContainerColor = Color(0xFF171B22), focusedContainerColor = Color(0xFF171B22),
                    unfocusedTextColor = Color.White, focusedTextColor = Color.White
                )
            )
            if (!roleHeld || !permissionState.granted) {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp).clickable { requestControl() },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF171B22)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, null, tint = Color(0xFF9BB7FF), modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Make Messages SOM your default SMS app", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Allow it to read, receive, send and manage your SMS like a full messaging app.", color = Color(0xFF9AA4B2), fontSize = 12.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Color.White)
                    }
                }
            }
            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = Color(0xFF9BB7FF)) {
                Tab(tab == 0, { tab = 0 }, text = { Text("All") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Links") })
                Tab(tab == 2, { tab = 2 }, text = { Text("Messages") })
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MarkUnreadChatAlt, null, tint = Color(0xFF9BB7FF), modifier = Modifier.size(62.dp))
                        Spacer(Modifier.height(14.dp))
                        Text(if (query.isBlank()) "No conversations yet" else "No results", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text("SMS conversations will appear here", color = Color(0xFF9AA4B2))
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filtered, key = { it.address }) { t ->
                        ConversationRow(t) { activity.startActivity(Intent(activity, ThreadActivity::class.java).putExtra("address", t.address)) }
                    }
                }
            }
        }
    }
}

private fun isDefaultSmsApp(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= 29) {
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)
    } else {
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }
}

@Composable
private fun ConversationRow(t: SmsThread, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF202630)), contentAlignment = Alignment.Center) {
            Text(t.address.take(1).uppercase(), color = Color(0xFF9BB7FF), fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.address, color = Color.White, fontSize = 17.sp, fontWeight = if (t.unread) FontWeight.Bold else FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t.date)), color = Color(0xFF7F8998), fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (t.hasLink) {
                    AssistChip(onClick = onClick, label = { Text("LINK", fontSize = 9.sp) }, leadingIcon = { Icon(Icons.Default.Link, null, Modifier.size(14.dp)) })
                    Spacer(Modifier.width(6.dp))
                }
                Text(t.preview.replace("\n", " "), color = if (t.unread) Color.White else Color(0xFF9AA4B2), maxLines = 1, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun MessagesSomTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF9BB7FF), background = Color(0xFF090B0F), surface = Color(0xFF171B22)), content = content)
}
