package com.rowix.gifsnap.consumer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rowix.gifsnap.client.*
import com.rowix.gifsnap.compose.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val client = remember { GifSnapClient() }
            var selected by remember { mutableStateOf<GifItem?>(null) }
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.systemBarsPadding().padding(8.dp)) {
                        Text(selected?.let { "Selected: ${it.title}" } ?: "GifSnap Android sample")
                        GifSnapPicker(client, onSelect = { selected = it }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
