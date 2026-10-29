package com.messages.som

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.messages.som.data.AppMessagingApiRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PageBg=Color(0xFF080A0E)
private val CardBg=Color(0xFF11151C)
private val Muted=Color(0xFF8993A3)
private val Accent=Color(0xFF9FC5FF)
private val Error=Color(0xFFFFA8A0)

class AppMessagesActivity : ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.POST_NOTIFICATIONS),7002)
        }
        if(checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS)!=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.READ_PHONE_NUMBERS),7001)
        }
        setContent{MessagesSomTheme{MessagesSomScreen(this)}}
    }
}

@Composable
private fun MessagesSomScreen(activity:AppMessagesActivity){
    val repo=remember{AppMessagingApiRepository(activity)}
    var profile by remember{mutableStateOf<AppMessagingApiRepository.AppProfile?>(null)}
    var selected by remember{mutableStateOf<AppMessagingApiRepository.AppProfile?>(null)}
    var loading by remember{mutableStateOf(true)}
    var error by remember{mutableStateOf("")}
    var search by remember{mutableStateOf("")}
    var results by remember{mutableStateOf<List<AppMessagingApiRepository.AppProfile>>(emptyList())}

    fun register(){
        loading=true;error=""
        repo.ensureRegistered{r->r.onSuccess{profile=it;loading=false}.onFailure{error=it.message?:"Unable to connect to Messages SOM";loading=false}}
    }
    LaunchedEffect(Unit){register()}
    if(selected!=null){AppChatScreen(repo,selected!!){selected=null};return}

    Scaffold(containerColor=PageBg,topBar={
        TopAppBar(
            title={Column{Text("Messages SOM",color=Color.White,fontWeight=FontWeight.SemiBold,fontSize=20.sp);Text("Private messaging network",color=Muted,fontSize=11.sp)}},
            navigationIcon={IconButton(onClick={activity.finish()}){Icon(Icons.Default.ArrowBack,"Back",tint=Color.White)}},
            actions={IconButton(onClick={::register}){Icon(Icons.Default.Refresh,"Refresh",tint=Color.White)}}
        )
    }){pad->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal=16.dp)){
            if(loading){
                Box(Modifier.fillMaxWidth().padding(top=70.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(color=Accent)}
            }else{
                profile?.let{me->
                    Surface(color=CardBg,shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth().padding(top=10.dp)){
                        Column(Modifier.padding(18.dp)){
                            Row(verticalAlignment=Alignment.CenterVertically){
                                Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF1B2738)),contentAlignment=Alignment.Center){Text("M",color=Accent,fontWeight=FontWeight.Bold,fontSize=20.sp)}
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)){Text("Your Messages SOM ID",color=Muted,fontSize=11.sp);Text(me.messagesId,color=Color.White,fontWeight=FontWeight.Bold,fontSize=21.sp)}
                                Icon(Icons.Default.CheckCircle,"Connected",tint=Color(0xFF7ED7A5),modifier=Modifier.size(21.dp))
                            }
                            if(me.phoneNumber.isNotBlank()){Spacer(Modifier.height(12.dp));Text("Linked number",color=Muted,fontSize=11.sp);Text(me.phoneNumber,color=Color.White,fontSize=15.sp)}
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text("Start a conversation",color=Color.White,fontWeight=FontWeight.Bold,fontSize=19.sp)
                Text("Find another Messages SOM user by phone number or MSOM ID.",color=Muted,fontSize=13.sp,modifier=Modifier.padding(top=4.dp,bottom=10.dp))
                OutlinedTextField(
                    value=search,
                    onValueChange={search=it;results=emptyList();if(error.startsWith("No Messages"))error=""},
                    modifier=Modifier.fillMaxWidth(),singleLine=true,
                    leadingIcon={Icon(Icons.Default.Search,null,tint=Muted)},
                    placeholder={Text("Phone number or MSOM ID")},
                    trailingIcon={IconButton(enabled=search.trim().length>=5,onClick={repo.search(search){r->r.onSuccess{results=it;if(it.isEmpty())error="No Messages SOM user found"}.onFailure{error=it.message?:"Search failed"}}}){Icon(Icons.Default.PersonAdd,"Find user")}},
                    keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(imeAction=ImeAction.Search),
                    keyboardActions=androidx.compose.foundation.text.KeyboardActions(onSearch={if(search.trim().length>=5)repo.search(search){r->r.onSuccess{results=it;if(it.isEmpty())error="No Messages SOM user found"}.onFailure{error=it.message?:"Search failed"}}}),
                    shape=RoundedCornerShape(18.dp),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Accent,unfocusedBorderColor=Color(0xFF454B55),cursorColor=Accent)
                )
                if(results.isNotEmpty()){
                    Spacer(Modifier.height(8.dp));results.forEach{u->UserRow(u){selected=u}}
                }
                if(error.isNotBlank()){
                    Spacer(Modifier.height(10.dp));Surface(color=Color(0xFF251619),shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()){
                        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text(error,color=Error,fontSize=12.sp,modifier=Modifier.weight(1f));IconButton(onClick={register()}){Icon(Icons.Default.Refresh,"Retry",tint=Color.White)}}
                    }
                }
                Spacer(Modifier.height(26.dp))
                Surface(color=CardBg,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()){
                    Column(Modifier.padding(16.dp)){Text("Messages SOM network",color=Color.White,fontWeight=FontWeight.SemiBold);Text("Messages between connected apps and users stay inside the Messages SOM network. They do not use carrier SMS.",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=6.dp))}
                }
            }
        }
    }
}

@Composable private fun UserRow(user:AppMessagingApiRepository.AppProfile,onClick:()->Unit){
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF1C2430)),contentAlignment=Alignment.Center){Text(user.displayName.take(1).uppercase().ifBlank{"M"},color=Accent,fontWeight=FontWeight.Bold,fontSize=18.sp)}
        Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text(user.displayName.ifBlank{"Messages SOM user"},color=Color.White,fontWeight=FontWeight.Medium);Text(user.phoneNumber.ifBlank{user.messagesId},color=Muted,fontSize=12.sp)}
    }
}

@Composable private fun AppChatScreen(repo:AppMessagingApiRepository,user:AppMessagingApiRepository.AppProfile,onBack:()->Unit){
    var messages by remember{mutableStateOf<List<AppMessagingApiRepository.AppMessage>>(emptyList())}
    var text by remember{mutableStateOf("")};var sending by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};val listState=rememberLazyListState()
    fun reload(){repo.conversation(user.uid){r->r.onSuccess{messages=it;error=""}.onFailure{error=it.message?:"Unable to load conversation"}}}
    LaunchedEffect(user.uid){reload();repo.markRead(user.uid)}
    LaunchedEffect(messages.size){if(messages.isNotEmpty())listState.animateScrollToItem(messages.lastIndex)}
    Scaffold(containerColor=PageBg,topBar={TopAppBar(title={Column{Text(user.displayName.ifBlank{"Messages SOM user"},color=Color.White,fontSize=17.sp);Text(user.phoneNumber.ifBlank{user.messagesId},color=Muted,fontSize=11.sp)}},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back",tint=Color.White)}},actions={IconButton(onClick=::reload){Icon(Icons.Default.Refresh,"Refresh",tint=Color.White)}})},bottomBar={
        if(user.allowReplies)Row(Modifier.fillMaxWidth().background(Color(0xFF0E1116)).padding(9.dp),verticalAlignment=Alignment.Bottom){
            OutlinedTextField(value=text,onValueChange={text=it},modifier=Modifier.weight(1f),placeholder={Text("Write a message")},maxLines=5,shape=RoundedCornerShape(24.dp),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Accent,unfocusedBorderColor=Color(0xFF454B55),cursorColor=Accent))
            Spacer(Modifier.width(7.dp));FilledIconButton(onClick={val value=text.trim();if(value.isBlank()||sending)return@FilledIconButton;sending=true;error="";repo.send(user,value){r->r.onSuccess{text="";reload()}.onFailure{error=it.message?:"Message failed"};sending=false}},enabled=text.isNotBlank()&&!sending,modifier=Modifier.size(52.dp)){Icon(Icons.Default.Send,"Send")}
        } else Surface(color=Color(0xFF11151C)){Text("Replies are disabled for this Sender ID",color=Muted,fontSize=12.sp,modifier=Modifier.fillMaxWidth().padding(16.dp))}
    }){pad->Column(Modifier.fillMaxSize().padding(pad)){
        if(error.isNotBlank())Text(error,color=Error,fontSize=12.sp,modifier=Modifier.padding(horizontal=12.dp,vertical=7.dp))
        LazyColumn(state=listState,modifier=Modifier.fillMaxSize().padding(horizontal=12.dp),contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
            items(messages,key={it.id}){m->Row(Modifier.fillMaxWidth(),horizontalArrangement=if(m.mine)Arrangement.End else Arrangement.Start){Surface(color=if(m.mine)Color(0xFF19304A) else Color(0xFF1B2028),shape=RoundedCornerShape(20.dp),modifier=Modifier.widthIn(max=330.dp)){Column(Modifier.padding(horizontal=14.dp,vertical=9.dp)){
                if(!m.mine&&m.senderId.isNotBlank())Text(m.senderId,color=Accent,fontSize=10.sp,fontWeight=FontWeight.Bold)
                Text(m.text,color=Color.White,fontSize=16.sp);Text(SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(m.createdAt)),color=Color(0xFF9AA4B2),fontSize=10.sp,modifier=Modifier.padding(top=5.dp))
            }}}}
        }
    }}
}
