package com.lumitalk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lumitalk.NativeBridge
import com.lumitalk.ui.components.CameraModeButton
import com.lumitalk.ui.components.CameraPreview
import com.lumitalk.ui.components.RecordButton
import com.lumitalk.util.camera.rememberCameraState

@Composable
fun ReceiveScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cameraState = rememberCameraState()
    val nativeBridge = remember { NativeBridge() }
    var frameInfo by remember { mutableStateOf("待機中...") }
    var currentFps by remember { mutableStateOf(0f) }
    var isRecording by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!cameraState.hasPermission) {
            cameraState.requestPermission()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (cameraState.hasPermission) {
            CameraPreview(
                cameraState = cameraState,
                onFrameAvailable = { bytes, width, height ->
                    frameInfo = nativeBridge.processFrame(bytes, width, height)
                },
                onFpsUpdated = { fps ->
                    currentFps = fps
                },
                isRecording = isRecording,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Text(
                text = frameInfo,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            Text(
                text = "FPS: ${"%.1f".format(currentFps)}",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }

        CameraModeButton(
            currentConfig = cameraState.currentConfig,
            onClick = { cameraState.switchMode() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )

        RecordButton(
            isRecording = isRecording,
            onClick = { isRecording = !isRecording },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}
