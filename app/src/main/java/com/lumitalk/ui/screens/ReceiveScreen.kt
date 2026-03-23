package com.lumitalk.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lumitalk.NativeBridge
import com.lumitalk.ui.components.AeLockButton
import com.lumitalk.ui.components.CameraModeButton
import com.lumitalk.ui.components.CameraPreview
import com.lumitalk.ui.components.RecordButton
import com.lumitalk.util.camera.rememberCameraState

private data class DetectionBox(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int
)

@Composable
fun ReceiveScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cameraState = rememberCameraState()
    val nativeBridge = remember { NativeBridge() }
    var currentFps by remember { mutableStateOf(0f) }
    var isRecording by remember { mutableStateOf(false) }
    var decodedResults by remember { mutableStateOf<List<Triple<Int, Int, String>>>(emptyList()) }
    var detectionBoxes by remember { mutableStateOf<List<DetectionBox>>(emptyList()) }
    var frameSize by remember { mutableStateOf(Pair(640, 480)) }

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
                    frameSize = Pair(width, height)
                    val raw = nativeBridge.processFrame(bytes, width, height)
                    // バウンディングボックスを毎フレームパース
                    val boxRegex = Regex("""\{"x":(-?\d+),"y":(-?\d+),"w":(\d+),"h":(\d+)\}""")
                    detectionBoxes = boxRegex.findAll(raw).map { m ->
                        DetectionBox(
                            m.groupValues[1].toInt(),
                            m.groupValues[2].toInt(),
                            m.groupValues[3].toInt(),
                            m.groupValues[4].toInt()
                        )
                    }.toList()
                    // デコード結果をパース
                    val decodedRegex = Regex("""\{"x":(\d+),"y":(\d+),"ascii":"([^"]*)"\}""")
                    val results = decodedRegex.findAll(raw)
                        .map { it.groupValues }
                        .filter { it[3].isNotEmpty() }
                        .map { Triple(it[1].toInt(), it[2].toInt(), it[3]) }
                        .toList()
                    if (results.isNotEmpty()) {
                        decodedResults = decodedResults + results
                    }
                },
                onFpsUpdated = { fps ->
                    currentFps = fps
                },
                isRecording = isRecording,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 検出ボックスの緑色オーバーレイ
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frameW = frameSize.first.toFloat()
            val frameH = frameSize.second.toFloat()
            // フレーム座標(横長)を画面座標(縦長)に90度右回転で合わせる
            val scaleX = size.width / frameH
            val scaleY = size.height / frameW
            val strokePx = 3.dp.toPx()
            detectionBoxes.forEach { box ->
                val rotatedX = frameH - (box.y + box.h).toFloat()
                val rotatedY = box.x.toFloat()
                val rotatedW = box.h.toFloat()
                val rotatedH = box.w.toFloat()
                drawRect(
                    color = Color.Green,
                    topLeft = Offset(rotatedX * scaleX, rotatedY * scaleY),
                    size = Size(rotatedW * scaleX, rotatedH * scaleY),
                    style = Stroke(width = strokePx)
                )
            }
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
