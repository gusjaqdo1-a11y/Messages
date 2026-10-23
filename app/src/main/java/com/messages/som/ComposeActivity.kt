package com.messages.som

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.SmsManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class ComposeActivity: ComponentActivity(){ override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MessagesSomTheme{ComposeScreen(this,intent.getStringExtra("address")?:(""))}}}}

@Composable private fun ComposeScreen(activity:Activity, initial:String){
 var address by remember{mutableStateOf(initial)}; var body by remember{mutableStateOf("")}; var info by remember{mutableStateOf("")}
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickContact()){uri-> if(uri!=null){activity.contentResolver.query(uri,arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),null,null,null)?.use{c->if(c.moveToFirst())address=c.getString(0)}}}
 Scaffold(containerColor=Color(0xFF0B0D10),topBar={TopAppBar(title={Text("New message",color=Color.White)},navigationIcon={IconButton({activity.finish()}){Icon(Icons.Default.ArrowBack,null,tint=Color.White)}},actions={IconButton({picker.launch(null)}){Icon(Icons.Default.Contacts,null,tint=Color.White)}})},bottomBar={Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.Bottom){OutlinedTextField(body,{body=it},modifier=Modifier.weight(1f),placeholder={Text("Text message")},maxLines=5);IconButton(onClick={if(address.isNotBlank()&&body.isNotBlank()){if(androidx.core.content.ContextCompat.checkSelfPermission(activity,Manifest.permission.SEND_SMS)==PackageManager.PERMISSION_GRANTED){SmsManager.getDefault().sendTextMessage(address,null,body,null,null);body="";info="Sent"}else info="SMS permission required"}}){Icon(Icons.Default.Send,null,tint=Color(0xFF8AB4F8))}}}){pad->Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)){OutlinedTextField(address,{address=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("To")},trailingIcon={IconButton({picker.launch(null)}){Icon(Icons.Default.PersonAdd,null)}});if(info.isNotBlank())Text(info,color=Color(0xFF8AB4F8),modifier=Modifier.padding(8.dp));Spacer(Modifier.height(16.dp));Text("Messages SOM",color=Color(0xFF9AA0A6))}}
}
