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
import com.lumitalk.ui.components.AeLockButton
import com.lumitalk.ui.components.CameraModeButton
import com.lumitalk.ui.components.CameraPreview
import com.lumitalk.ui.components.RecordButton
import com.lumitalk.util.camera.rememberCameraState

@Composable
fun ReceiveScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cameraState = rememberCameraState()
    val nativeBridge = remember { NativeBridge() }
    var currentFps by remember { mutableStateOf(0f) }
    var isRecording by remember { mutableStateOf(false) }
    var decodedResults by remember { mutableStateOf<List<Triple<Int, Int, String>>>(emptyList()) }

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
                    val raw = nativeBridge.processFrame(bytes, width, height)
                    if (raw.isNotEmpty()) {
                        val results = mutableListOf<Triple<Int, Int, String>>()
                        val regex = Regex("""\{"x":(\d+),"y":(\d+),"ascii":"([^"]*)"\}""")
                        regex.findAll(raw).forEach { match ->
                            val x = match.groupValues[1].toInt()
                            val y = match.groupValues[2].toInt()
                            val ascii = match.groupValues[3]
                            if (ascii.isNotEmpty()) {
                                results.add(Triple(x, y, ascii))
                            }
                        }
                        if (results.isNotEmpty()) {
                            decodedResults = decodedResults + results
                        }
                    }
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
                text = "FPS: ${"%.1f".format(currentFps)}",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            decodedResults.forEach { (x, y, ascii) ->
                Text(
                    text = "[x=$x, y=$y] $ascii",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Yellow
                )
            }
        }

        CameraModeButton(
            currentConfig = cameraState.currentConfig,
            onClick = { cameraState.switchMode() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )

        AeLockButton(
            isLocked = cameraState.isAeLocked,
            onLock = { cameraState.lockAeAf() },
            onUnlock = { cameraState.unlockAeAf() },
            modifier = Modifier
                .align(Alignment.BottomStart)
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
