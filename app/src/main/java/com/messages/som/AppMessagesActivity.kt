package com.messages.som

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.messages.som.data.AppMessagingApiRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppMessagesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); if(androidx.core.content.ContextCompat.checkSelfPermission(this,Manifest.permission.READ_PHONE_NUMBERS)!=PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.READ_PHONE_NUMBERS),7001); setContent{MessagesSomTheme{AppMessagesScreen(this)}} }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AppMessagesScreen(activity:AppMessagesActivity){
    val repo=remember{AppMessagingApiRepository(activity)}; var profile by remember{mutableStateOf<AppMessagingApiRepository.AppProfile?>(null)}; var selected by remember{mutableStateOf<AppMessagingApiRepository.AppProfile?>(null)}; var loading by remember{mutableStateOf(true)}; var error by remember{mutableStateOf("")}; var search by remember{mutableStateOf("")}; var results by remember{mutableStateOf<List<AppMessagingApiRepository.AppProfile>>(emptyList())}
    LaunchedEffect(Unit){repo.ensureRegistered{r->r.onSuccess{profile=it;loading=false}.onFailure{error=it.message?:"Unable to connect to Messages SOM";loading=false}}}
    if(selected!=null){AppChatScreen(repo,selected!!,{selected=null});return}
    Scaffold(containerColor=Color(0xFF090B0F),topBar={TopAppBar(title={Column{Text("Messages SOM",color=Color.White,fontWeight=FontWeight.SemiBold);Text("Messaging network",color=Color(0xFF8E98A8),fontSize=11.sp)}},navigationIcon={IconButton({activity.finish()}){Icon(Icons.Default.ArrowBack,"Back",tint=Color.White)}})}){pad->Column(Modifier.fillMaxSize().padding(pad).padding(horizontal=14.dp)){
        if(loading)Box(Modifier.fillMaxWidth().padding(top=50.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()} else {profile?.let{me->Surface(color=Color(0xFF11161D),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Column(Modifier.padding(16.dp)){Text("Your Messages SOM identity",color=Color(0xFF8E98A8),fontSize=12.sp);Text(me.messagesId,color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold);Text(me.displayName,color=Color(0xFF9EA8B8),fontSize=13.sp);if(me.phoneNumber.isNotBlank()){Spacer(Modifier.height(12.dp));Text("Linked phone number",color=Color(0xFF8E98A8),fontSize=11.sp);Text(me.phoneNumber,color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Medium)}}}}
        Spacer(Modifier.height(14.dp));Text("Message a Messages SOM user",color=Color.White,fontWeight=FontWeight.SemiBold);Spacer(Modifier.height(7.dp));OutlinedTextField(value=search,onValueChange={search=it;results=emptyList();error=""},modifier=Modifier.fillMaxWidth(),singleLine=true,leadingIcon={Icon(Icons.Default.Search,null)},placeholder={Text("Phone number or MSOM ID")},trailingIcon={IconButton(enabled=search.trim().length>=5,onClick={repo.search(search){r->r.onSuccess{results=it;if(it.isEmpty())error="No Messages SOM user found"}.onFailure{error=it.message?:"Search failed"}}}){Icon(Icons.Default.PersonAdd,"Find user")}},shape=RoundedCornerShape(18.dp));if(results.isNotEmpty()){Spacer(Modifier.height(8.dp));results.forEach{u->UserRow(u){selected=u}}};if(error.isNotBlank()){Spacer(Modifier.height(8.dp));Text(error,color=Color(0xFFFFA8A0),fontSize=12.sp)};Spacer(Modifier.height(18.dp));Text("App-to-app messaging",color=Color.White,fontWeight=FontWeight.SemiBold);Text("Connected apps can send through Messages SOM Sender IDs. Messages stay inside the Messages SOM network and do not use carrier SMS.",color=Color(0xFF8993A3),fontSize=13.sp,modifier=Modifier.padding(top=6.dp))}
    }}
}
@Composable private fun UserRow(user:AppMessagingApiRepository.AppProfile,onClick:()->Unit){Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF252B35)),contentAlignment=Alignment.Center){Text(user.displayName.take(1).uppercase(),color=Color(0xFFA8C7FA),fontWeight=FontWeight.Bold,fontSize=18.sp)};Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text(user.displayName,color=Color.White,fontWeight=FontWeight.Medium);Text(user.phoneNumber.ifBlank{user.messagesId},color=Color(0xFF8F99A9),fontSize=12.sp)}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AppChatScreen(repo:AppMessagingApiRepository,user:AppMessagingApiRepository.AppProfile,onBack:()->Unit){var messages by remember{mutableStateOf<List<AppMessagingApiRepository.AppMessage>>(emptyList())};var text by remember{mutableStateOf("")};var sending by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};val listState=rememberLazyListState();LaunchedEffect(user.uid){repo.conversation(user.uid){r->r.onSuccess{messages=it}.onFailure{error=it.message?:"Unable to load conversation"}};repo.markRead(user.uid)};LaunchedEffect(messages.size){if(messages.isNotEmpty())listState.animateScrollToItem(messages.lastIndex)};Scaffold(containerColor=Color(0xFF090B0F),topBar={TopAppBar(title={Column{Text(user.displayName,color=Color.White,fontSize=17.sp);Text(user.phoneNumber.ifBlank{user.messagesId},color=Color(0xFF8E98A8),fontSize=11.sp)}},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back",tint=Color.White)}})},bottomBar={if(user.allowReplies)Row(Modifier.fillMaxWidth().background(Color(0xFF0F1217)).padding(9.dp),verticalAlignment=Alignment.Bottom){OutlinedTextField(value=text,onValueChange={text=it},modifier=Modifier.weight(1f),placeholder={Text("Message")},maxLines=5,shape=RoundedCornerShape(24.dp));Spacer(Modifier.width(7.dp));FilledIconButton(onClick={val v=text.trim();if(v.isBlank()||sending)return@FilledIconButton;sending=true;repo.send(user,v){r->r.onSuccess{text="";repo.conversation(user.uid){it.onSuccess{messages=it}}}.onFailure{error=it.message?:"Message failed"};sending=false}},enabled=text.isNotBlank()&&!sending,modifier=Modifier.size(52.dp)){Icon(Icons.Default.Send,"Send")}}}){pad->Column(Modifier.fillMaxSize().padding(pad)){LazyColumn(state=listState,modifier=Modifier.fillMaxSize().padding(horizontal=12.dp),contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){items(messages,key{it.id}){m->val mine=m.senderUid.contains("app:")&&m.senderId=="";Row(Modifier.fillMaxWidth(),horizontalArrangement=if(mine)Arrangement.End else Arrangement.Start){Surface(color=if(mine)Color(0xFF274A70)else Color(0xFF1B2028),shape=RoundedCornerShape(20.dp),modifier=Modifier.widthIn(max=330.dp)){Column(Modifier.padding(horizontal=14.dp,vertical=9.dp)){if(m.senderId.isNotBlank())Text(m.senderId,color=Color(0xFF9FC5FF),fontSize=10.sp,fontWeight=FontWeight.Bold);Text(m.text,color=Color.White,fontSize=16.sp);Text(SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(m.createdAt)),color=Color(0xFF9AA4B2),fontSize=10.sp,modifier=Modifier.padding(top=5.dp))}}}}};if(error.isNotBlank())Text(error,color=Color(0xFFFFA8A0),fontSize=12.sp,modifier=Modifier.padding(10.dp))}}}
