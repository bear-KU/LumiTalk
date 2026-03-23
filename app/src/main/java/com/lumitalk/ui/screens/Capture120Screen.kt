package com.lumitalk.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lumitalk.ui.components.CameraPreview
import com.lumitalk.ui.components.RecordButton
import com.lumitalk.util.camera.CameraMode
import com.lumitalk.util.camera.rememberCameraState

@Composable
fun Capture120Screen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cameraState = rememberCameraState()
    var currentFps by remember { mutableStateOf(0f) }
    var isRecording by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!cameraState.hasPermission) {
            cameraState.requestPermission()
        }
    }

    LaunchedEffect(cameraState.currentConfig.mode) {
        if (cameraState.currentConfig.mode != CameraMode.HIGH_SPEED) {
            cameraState.switchMode()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val isHighSpeedReady = cameraState.currentConfig.mode == CameraMode.HIGH_SPEED
        if (cameraState.hasPermission && isHighSpeedReady) {
            CameraPreview(
                cameraState = cameraState,
                onFrameAvailable = { _, _, _ -> },
                onFpsUpdated = { fps -> currentFps = fps },
                isRecording = isRecording,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Button(onClick = onBack) {
                Text("戻る")
            }
            Text(
                text = "Capture: ${cameraState.currentConfig.label}",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            Text(
                text = "FPS: ${"%.1f".format(currentFps)}",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }

        RecordButton(
            isRecording = isRecording,
            onClick = {
                if (isHighSpeedReady) {
                    isRecording = !isRecording
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}
