package com.lumitalk.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.lumitalk.ui.components.CameraPreview
import com.lumitalk.util.rememberCameraState

@Composable
fun ReceiveScreen(modifier: Modifier = Modifier) {
    val cameraState = rememberCameraState()

    LaunchedEffect(Unit) {
        if (!cameraState.hasPermission) {
            cameraState.requestPermission()
        }
    }

    if (cameraState.hasPermission) {
        CameraPreview(
            cameraState = cameraState,
            modifier = modifier.fillMaxSize()
        )
    }
}
