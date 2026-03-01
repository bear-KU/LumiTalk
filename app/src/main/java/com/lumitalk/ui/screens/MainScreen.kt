package com.lumitalk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumitalk.util.rememberFlashlightState

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CounterControl()
        Spacer(modifier = Modifier.height(32.dp))
        FlashlightControl()
    }
}

@Composable
fun CounterControl() {
    var count by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Count: $count",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = { count++ }) {
            Text("Click Me")
        }
    }
}

@Composable
fun FlashlightControl() {
    val flashState = rememberFlashlightState()

    if (!flashState.isAvailable) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Flash not available on this device",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Flash: ${if (flashState.isFlashOn) "ON" else "OFF"}",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = { flashState.requestPermissionAndToggle() }) {
            Text("Toggle Flash")
        }
    }
}
