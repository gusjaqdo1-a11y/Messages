package com.messages.som

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

class MainActivity : ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MessagesSomTheme { MessagesHome(this) } } } }
data class SmsThread(val address:String,val preview:String,val date:Long,val count:Int)
private fun loadThreads(context:Context):List<SmsThread>{if(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED)return emptyList();val out=linkedMapOf<String,SmsThread>();context.contentResolver.query(Telephony.Sms.CONTENT_URI,arrayOf(Telephony.Sms.ADDRESS,Telephony.Sms.BODY,Telephony.Sms.DATE),null,null,Telephony.Sms.DATE+" DESC")?.use{c->val ai=c.getColumnIndex(Telephony.Sms.ADDRESS);val bi=c.getColumnIndex(Telephony.Sms.BODY);val di=c.getColumnIndex(Telephony.Sms.DATE);while(c.moveToNext()&&out.size<100){val a=c.getString(ai)?:"Unknown";if(!out.containsKey(a))out[a]=SmsThread(a,c.getString(bi)?:"",c.getLong(di),1)}};return out.values.toList()}
@Composable private fun MessagesHome(activity:MainActivity){var threads by remember{mutableStateOf(loadThreads(activity))};var query by remember{mutableStateOf("")};val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){threads=loadThreads(activity)};LaunchedEffect(Unit){permissions.launch(arrayOf(Manifest.permission.READ_SMS,Manifest.permission.SEND_SMS,Manifest.permission.READ_CONTACTS,Manifest.permission.RECEIVE_SMS,Manifest.permission.POST_NOTIFICATIONS))};val roleLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){threads=loadThreads(activity)};val filtered=threads.filter{it.address.contains(query,true)||it.preview.contains(query,true)};Scaffold(containerColor=Color(0xFF0B0D10),floatingActionButton={FloatingActionButton(onClick={activity.startActivity(Intent(activity,ComposeActivity::class.java))},containerColor=Color(0xFF8AB4F8),contentColor=Color(0xFF0B0D10)){Icon(Icons.Default.Edit,null)}}){pad->Column(Modifier.fillMaxSize().background(Color(0xFF0B0D10)).padding(pad).padding(horizontal=18.dp)){Row(Modifier.fillMaxWidth().padding(top=18.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically){Text("Messages SOM",fontSize=28.sp,fontWeight=FontWeight.Bold,color=Color.White,modifier=Modifier.weight(1f));IconButton({}){Icon(Icons.Default.AccountCircle,"Profile",tint=Color.White)}};OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(28.dp),leadingIcon={Icon(Icons.Default.Search,null)},placeholder={Text("Search conversations")},colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=Color.Transparent,focusedBorderColor=Color(0xFF8AB4F8),unfocusedContainerColor=Color(0xFF171A1F),focusedContainerColor=Color(0xFF171A1F),unfocusedTextColor=Color.White,focusedTextColor=Color.White));Row(Modifier.padding(vertical=14.dp)){AssistChip(onClick={if(android.os.Build.VERSION.SDK_INT>=29){val rm=activity.getSystemService(RoleManager::class.java);if(rm.isRoleAvailable(RoleManager.ROLE_SMS)&&!rm.isRoleHeld(RoleManager.ROLE_SMS))roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))}},label={Text("Set as default SMS")})};if(filtered.isEmpty())Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.ChatBubbleOutline,null,tint=Color(0xFF8AB4F8),modifier=Modifier.size(64.dp));Spacer(Modifier.height(14.dp));Text("No messages yet",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.SemiBold);Text("Your SMS conversations will appear here",color=Color(0xFF9AA0A6))}}else LazyColumn(verticalArrangement=Arrangement.spacedBy(4.dp)){items(filtered){t->ConversationRow(t){activity.startActivity(Intent(activity,ThreadActivity::class.java).putExtra("address",t.address))}}}}}
@Composable private fun ConversationRow(t:SmsThread,onClick:()->Unit){Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick=onClick).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF252A31)),contentAlignment=Alignment.Center){Text(t.address.take(1).uppercase(),color=Color(0xFF8AB4F8),fontWeight=FontWeight.Bold,fontSize=20.sp)};Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(t.address,color=Color.White,fontSize=17.sp,fontWeight=FontWeight.SemiBold);Text(t.preview.replace("\n"," "),color=Color(0xFF9AA0A6),maxLines=1)}}}
@Composable private fun MessagesSomTheme(content:@Composable()->Unit){MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF8AB4F8),background=Color(0xFF0B0D10),surface=Color(0xFF171A1F)),content=content)}
