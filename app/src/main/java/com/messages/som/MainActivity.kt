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
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.messages.som.data.AppMessagingApiRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { requestPermissionsIfNeeded() }
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { requestPermissionsIfNeeded() }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MessagesSomTheme { MainScreen(this, ::requestDefaultSmsRole, ::requestPermissionsIfNeeded) } }; if (Build.VERSION.SDK_INT >= 29 && !isDefaultSmsApp(this)) requestDefaultSmsRole() else requestPermissionsIfNeeded() }
    override fun onResume() { super.onResume(); if (isDefaultSmsApp(this)) { requestPermissionsIfNeeded(); syncMessagesSomProfile() } }
    private fun syncMessagesSomProfile() { if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED) return; AppMessagingApiRepository(this).ensureRegistered { } }
    private fun requestDefaultSmsRole() { if (Build.VERSION.SDK_INT >= 29) { val rm = getSystemService(RoleManager::class.java); if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_SMS)) { if (rm.isRoleHeld(RoleManager.ROLE_SMS)) { requestPermissionsIfNeeded(); return }; runCatching { roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS)) }.onFailure { openDefaultAppsSettings() }; return } }; runCatching { startActivity(Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)) }.onFailure { openDefaultAppsSettings() } }
    private fun openDefaultAppsSettings() { runCatching { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) } }
    private fun requestPermissionsIfNeeded() { val list = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_CONTACTS, Manifest.permission.RECEIVE_MMS, Manifest.permission.RECEIVE_WAP_PUSH, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS); if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS; val missing = list.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }; if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray()) else syncMessagesSomProfile() }
}

data class Conversation(val address: String, val body: String, val date: Long, val unread: Int)
fun isDefaultSmsApp(context: Context): Boolean { val legacy = runCatching { Telephony.Sms.getDefaultSmsPackage(context) == context.packageName }.getOrDefault(false); val role = if (Build.VERSION.SDK_INT >= 29) runCatching { context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_SMS) == true }.getOrDefault(false) else false; return legacy || role }
fun isWritableDestination(address: String): Boolean = address.trim().matches(Regex("\\+?[0-9][0-9 .()-]{5,}"))
fun containsLink(text: String): Boolean = Regex("https?://\\S+|www\\.\\S+", RegexOption.IGNORE_CASE).containsMatchIn(text)
fun displayContactName(context: Context, address: String): String { if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return address; return runCatching { context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), "${ContactsContract.CommonDataKinds.Phone.NUMBER}=?", arrayOf(address), null)?.use { if (it.moveToFirst()) it.getString(0) else address } ?: address }.getOrDefault(address) }
fun markThreadRead(context: Context, address: String) { if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return; runCatching { context.contentResolver.update(Telephony.Sms.CONTENT_URI, android.content.ContentValues().apply { put(Telephony.Sms.READ, 1) }, "address=? AND read=0", arrayOf(address)) } }
private fun loadConversations(context: Context): List<Conversation> { if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return emptyList(); val map = linkedMapOf<String, Conversation>(); runCatching { context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE), null, null, "${Telephony.Sms.DATE} DESC")?.use { c -> while (c.moveToNext()) { val address = c.getString(0) ?: continue; if (isArchived(context, address) || map.containsKey(address)) continue; map[address] = Conversation(address, c.getString(1) ?: "", c.getLong(2), 0) } }; map.keys.toList().forEach { address -> val unread = context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID), "address=? AND read=0", arrayOf(address), null)?.use { it.count } ?: 0; map[address] = map[address]!!.copy(unread = unread) } }; return map.values.sortedByDescending { it.date } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MainScreen(activity: MainActivity, requestRole: () -> Unit, requestPermissions: () -> Unit) { var conversations by remember { mutableStateOf(loadConversations(activity)) }; var query by remember { mutableStateOf("") }; var showMenu by remember { mutableStateOf(false) }; var defaultSms by remember { mutableStateOf(isDefaultSmsApp(activity)) }; fun refresh() { defaultSms = isDefaultSmsApp(activity); conversations = loadConversations(activity) }; val filtered = conversations.filter { query.isBlank() || displayContactName(activity, it.address).contains(query, true) || it.address.contains(query, true) || it.body.contains(query, true) }; LaunchedEffect(Unit) { while (true) { refresh(); kotlinx.coroutines.delay(1500) } }; Scaffold(containerColor = Color(0xFF090B0F), topBar = { TopAppBar(title = { Text("Messages SOM", color = Color.White, fontWeight = FontWeight.SemiBold) }, actions = { IconButton(onClick = { activity.startActivity(Intent(activity, AppMessagesActivity::class.java)) }) { Icon(Icons.Default.ChatBubbleOutline, "Messages SOM chat", tint = Color.White) }; IconButton(onClick = { activity.startActivity(Intent(activity, ArchiveActivity::class.java)) }) { Icon(Icons.Default.Archive, "Archived", tint = Color.White) }; Box { IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "More", tint = Color.White) }; DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) { DropdownMenuItem(text = { Text("Refresh") }, onClick = { refresh(); showMenu = false }); DropdownMenuItem(text = { Text("SMS settings") }, onClick = { runCatching { activity.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }; showMenu = false }) } } }) }, floatingActionButton = { FloatingActionButton(onClick = { activity.startActivity(Intent(activity, ComposeActivity::class.java)) }, containerColor = Color(0xFFA8C7FA), contentColor = Color(0xFF0A305F)) { Icon(Icons.Default.Edit, "New message") } }) { pad -> Column(Modifier.fillMaxSize().padding(pad)) { OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Search conversations") }, shape = RoundedCornerShape(28.dp)); SetupBanner(defaultSms, requestRole); if (filtered.isEmpty()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.MarkUnreadChatAlt, null, tint = Color(0xFF718096), modifier = Modifier.size(52.dp)); Spacer(Modifier.height(10.dp)); Text(if (query.isBlank()) "No conversations yet" else "No results", color = Color.White, fontSize = 18.sp); Text(if (query.isBlank()) "Use the chat icon for Messages SOM, or compose an SMS." else "Try another search.", color = Color(0xFF8993A3)) } } } else { LazyColumn(contentPadding = PaddingValues(bottom = 92.dp), modifier = Modifier.fillMaxSize()) { items(filtered, key = { it.address }) { c -> ConversationRow(activity, c) { refresh() } } } } } } }

@Composable private fun SetupBanner(defaultSms: Boolean, requestRole: () -> Unit) { if (defaultSms) Spacer(Modifier.height(0.dp)) else Surface(color = Color(0xFF171D26), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Sms, null, tint = Color(0xFFA8C7FA)); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("Make Messages SOM your SMS app", color = Color.White, fontWeight = FontWeight.SemiBold); Text("Required for full SMS control and sending.", color = Color(0xFF9EA8B8), fontSize = 12.sp) }; TextButton(onClick = requestRole) { Text("Set up") } } } }
@Composable private fun ConversationRow(activity: MainActivity, c: Conversation, refresh: () -> Unit) { val name = displayContactName(activity, c.address); val nameWeight = if (c.unread > 0) FontWeight.Bold else FontWeight.Medium; val bodyColor = if (c.unread > 0) Color(0xFFE2E6ED) else Color(0xFF929BAA); Row(Modifier.fillMaxWidth().clickable { activity.startActivity(Intent(activity, ThreadActivity::class.java).putExtra("address", c.address)) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF252B35)), contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), color = Color(0xFFA8C7FA), fontSize = 20.sp, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(13.dp)); Column(Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(name, color = Color.White, fontSize = 16.sp, fontWeight = nameWeight, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)); Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(c.date)), color = Color(0xFF8F98A8), fontSize = 11.sp) }; Row(verticalAlignment = Alignment.CenterVertically) { Text(c.body, color = bodyColor, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)); UnreadBadge(c.unread) }; LinkBadge(c.body) } } }
@Composable private fun UnreadBadge(unread: Int) { if (unread > 0) { Spacer(Modifier.width(8.dp)); Surface(color = Color(0xFFA8C7FA), shape = CircleShape) { Text(if (unread > 99) "99+" else unread.toString(), color = Color(0xFF0A305F), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)) } } }
@Composable private fun LinkBadge(body: String) { if (containsLink(body)) Text("LINK", color = Color(0xFFA8C7FA), fontSize = 9.sp, fontWeight = FontWeight.Bold) else Spacer(Modifier.height(0.dp)) }
