package com.lumitalk.ui.screens

import android.widget.NumberPicker
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lumitalk.NativeBridge
import com.lumitalk.util.rememberFlashlightState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SendScreen(modifier: Modifier = Modifier) {
    var selectedT by remember { mutableStateOf(10) }
    var showDialog by remember { mutableStateOf(false) }
    var inputData by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    val flashState = rememberFlashlightState()
    val coroutineScope = rememberCoroutineScope()
    val nativeBridge = remember { NativeBridge() }

    // 権限がない場合は起動時にリクエスト
    LaunchedEffect(Unit) {
        if (!flashState.hasPermission) {
            flashState.requestPermission()
        }
    }

    if (showDialog) {
        TPickerDialog(
            currentValue = selectedT,
            onConfirm = { selectedT = it; showDialog = false },
            onDismiss = { showDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("T (ms)", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { showDialog = true },
            modifier = Modifier.width(160.dp),
            enabled = !isSending
        ) {
            Text("${selectedT}ms")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("送信データ", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = inputData,
            onValueChange = { inputData = it },
            label = { Text("入力してください") },
            singleLine = true,
            enabled = !isSending,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (!flashState.hasPermission) {
            Text(
                text = "カメラ権限がありません",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                isSending = true
                coroutineScope.launch(Dispatchers.Default) {
                    val sequence = nativeBridge.generateSignalSequence(inputData, selectedT)
                    var i = 0
                    while (i < sequence.size) {
                        val state = sequence[i]
                        val duration = sequence[i + 1]
                        flashState.setFlash(state == 1)
                        if (duration > 0) delay(duration.toLong())
                        i += 2
                    }
                    flashState.setFlash(false)
                    isSending = false
                }
            },
            enabled = inputData.isNotEmpty() && !isSending && flashState.isAvailable && flashState.hasPermission
        ) {
            Text(if (isSending) "送信中..." else "送信")
        }
    }
}

@Composable
fun TPickerDialog(
    currentValue: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var tempValue by remember { mutableStateOf(currentValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("T を選択") },
        text = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                AndroidView(
                    factory = { context ->
                        NumberPicker(context).apply {
                            minValue = 1
                            maxValue = 50
                            value = tempValue
                            wrapSelectorWheel = false
                            setOnValueChangedListener { _, _, newVal ->
                                tempValue = newVal
                            }
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(tempValue) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
