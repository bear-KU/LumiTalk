package com.lumitalk.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.lumitalk.NativeBridge
import com.lumitalk.ui.components.AeLockButton
import com.lumitalk.ui.components.CameraModeButton
import com.lumitalk.ui.components.CameraPreviewCenter
import com.lumitalk.util.camera.rememberCameraState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val ROI_FRACTION = 1f / 10f

@Composable
fun ReceiveCenterScreen(modifier: Modifier = Modifier) {
    val cameraState    = rememberCameraState()
    val nativeBridge   = remember { NativeBridge() }
    val coroutineScope = rememberCoroutineScope()
    var decodedResults by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLedOn        by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!cameraState.hasPermission) cameraState.requestPermission()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidth  = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()

        val frameWidth  = cameraState.currentConfig.width.toFloat()
        val frameHeight = cameraState.currentConfig.height.toFloat()

        val scaleX = screenWidth  / frameHeight
        val scaleY = screenHeight / frameWidth

        val screenRadius = minOf(screenWidth, screenHeight) * ROI_FRACTION
        val roiRadius    = minOf(screenRadius / scaleX, screenRadius / scaleY).toInt()

        if (cameraState.hasPermission) {
            CameraPreviewCenter(
                cameraState       = cameraState,
                nativeBridge      = nativeBridge,
                roiRadius         = roiRadius,
                onResultAvailable = { result ->
                    coroutineScope.launch(Dispatchers.Main) {
                        decodedResults = (decodedResults + result).takeLast(5)
                    }
                },
                onStateUpdated = { state ->
                    coroutineScope.launch(Dispatchers.Main) {
                        isLedOn = state
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center      = Offset(size.width / 2f, size.height / 2f)
            val circleColor = if (isLedOn) Color.Green.copy(alpha = 0.8f)
                              else Color.White.copy(alpha = 0.8f)

            drawCircle(
                color  = circleColor,
                radius = screenRadius,
                center = center,
                style  = Stroke(width = 3.dp.toPx())
            )
            val cross = 14.dp.toPx()
            listOf(
                Offset(-cross, 0f) to Offset(cross, 0f),
                Offset(0f, -cross) to Offset(0f, cross)
            ).forEach { (s, e) ->
                drawLine(
                    color       = circleColor,
                    start       = center + s,
                    end         = center + e,
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            decodedResults.forEach { ascii ->
                Text(
                    text  = ascii,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Yellow
                )
            }
        }

        CameraModeButton(
            currentConfig = cameraState.currentConfig,
            onClick       = { cameraState.switchMode() },
            modifier      = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )
        AeLockButton(
            isLocked = cameraState.isAeLocked,
            onLock   = { cameraState.lockAeAf() },
            onUnlock = { cameraState.unlockAeAf() },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )
    }
}
