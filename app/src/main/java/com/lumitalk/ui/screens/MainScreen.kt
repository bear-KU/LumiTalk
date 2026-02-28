package com.lumitalk.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme

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

        Button(
            onClick = {
                count++
            }
        ) {
            Text("Click Me")
        }
    }
}

@Composable
fun FlashlightControl() {
    val context = LocalContext.current
    var isFlashOn by remember { mutableStateOf(false) }

    val cameraManager = remember {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    val cameraId = remember {
        cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(
                CameraCharacteristics.FLASH_INFO_AVAILABLE
            ) == true
        }
    }

    if (cameraId == null) {
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

    DisposableEffect(Unit) {
        onDispose {
            toggleFlashlight(cameraManager, cameraId, false)
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                isFlashOn = !isFlashOn
                toggleFlashlight(cameraManager, cameraId, isFlashOn)
            }
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Flash: ${if (isFlashOn) "ON" else "OFF"}",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                val permissionCheck = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                )
                if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                    isFlashOn = !isFlashOn
                    toggleFlashlight(cameraManager, cameraId, isFlashOn)
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        ) {
            Text("Toggle Flash")
        }
    }
}

fun toggleFlashlight(cameraManager: CameraManager, cameraId: String, isFlashOn: Boolean) {
    try {
        cameraManager.setTorchMode(cameraId, isFlashOn)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

