package com.lumitalk.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class FlashlightState(
    val cameraManager: CameraManager,
    val cameraId: String?,
    val isFlashOn: Boolean,
    val isAvailable: Boolean,
    val hasPermission: Boolean,
    val setFlash: (Boolean) -> Unit,
    val toggle: () -> Unit,
    val requestPermissionAndToggle: () -> Unit,
    val requestPermission: () -> Unit
)

@Composable
fun rememberFlashlightState(): FlashlightState {
    val context = LocalContext.current
    var isFlashOn by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

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

    val setFlash: (Boolean) -> Unit = { state: Boolean ->
        cameraId?.let {
            try {
                cameraManager.setTorchMode(it, state)
                isFlashOn = state
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        Unit
    }

    val toggle: () -> Unit = {
        setFlash(!isFlashOn)
        Unit
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) toggle()
    }

    val permissionLauncherOnly = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    val requestPermissionAndToggle: () -> Unit = {
        if (hasPermission) {
            toggle()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        Unit
    }

    val requestPermission: () -> Unit = {
        if (!hasPermission) {
            permissionLauncherOnly.launch(Manifest.permission.CAMERA)
        }
        Unit
    }

    DisposableEffect(Unit) {
        onDispose {
            setFlash(false)
        }
    }

    return FlashlightState(
        cameraManager = cameraManager,
        cameraId = cameraId,
        isFlashOn = isFlashOn,
        isAvailable = cameraId != null,
        hasPermission = hasPermission,
        setFlash = setFlash,
        toggle = toggle,
        requestPermissionAndToggle = requestPermissionAndToggle,
        requestPermission = requestPermission
    )
}
