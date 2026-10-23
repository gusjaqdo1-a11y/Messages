package com.messages.som

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class ArchiveActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MessagesSomTheme { ArchiveScreen(this) } }
    }
}

@Composable
private fun ArchiveScreen(activity: ArchiveActivity) {
    var addresses by remember { mutableStateOf(archivedAddresses(activity).toList().sorted()) }

    Scaffold(
        containerColor = Color(0xFF080A0E),
        topBar = {
            TopAppBar(
                title = { Text("Archived", color = Color.White) },
                navigationIcon = { IconButton(onClick = activity::finish) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) } }
            )
        }
    ) { padding ->
        if (addresses.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Archive, null, tint = Color(0xFF9BB7FF), modifier = Modifier.size(58.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Nothing archived", color = Color.White, fontSize = 19.sp)
                    Text("Archived conversations will appear here.", color = Color(0xFF98A2B3))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(addresses, key = { it }) { address ->
                    ListItem(
                        modifier = Modifier.clickable {
                            activity.startActivity(android.content.Intent(activity, ThreadActivity::class.java).putExtra("address", address))
                        },
                        headlineContent = { Text(address, color = Color.White) },
                        supportingContent = { Text("Archived conversation", color = Color(0xFF8E98A8)) },
                        leadingContent = { Icon(Icons.Default.Archive, null, tint = Color(0xFF9BB7FF)) },
                        trailingContent = {
                            IconButton(onClick = {
                                setArchived(activity, address, false)
                                addresses = archivedAddresses(activity).toList().sorted()
                            }) { Icon(Icons.Default.Unarchive, "Unarchive", tint = Color(0xFFB9C7DA)) }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color(0xFF151A21))
                    )
                }
            }
        }
    }
}
