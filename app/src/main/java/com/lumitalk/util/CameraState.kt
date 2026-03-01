package com.lumitalk.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class CameraState(
    val hasPermission: Boolean,
    val cameraDevice: CameraDevice?,
    val captureSession: CameraCaptureSession?,
    val requestPermission: () -> Unit,
    val openCamera: (Context, Surface, Int, Int) -> Unit,
    val closeCamera: () -> Unit
)

private fun openCameraInternal(
    context: Context,
    surface: Surface,
    onDeviceOpened: (CameraDevice) -> Unit,
    onSessionCreated: (CameraCaptureSession) -> Unit,
    onDeviceClosed: () -> Unit
) {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
        cameraManager.getCameraCharacteristics(id)
            .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    } ?: return

    val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            onDeviceOpened(camera)
            val captureRequest = camera.createCaptureRequest(
                CameraDevice.TEMPLATE_PREVIEW
            ).apply {
                addTarget(surface)
            }

            @Suppress("DEPRECATION")
            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        onSessionCreated(session)
                        session.setRepeatingRequest(captureRequest.build(), null, null)
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                },
                null
            )
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            onDeviceClosed()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            onDeviceClosed()
        }
    }

    try {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            cameraManager.openCamera(cameraId, stateCallback, null)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun rememberCameraState(): CameraState {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraDevice by remember { mutableStateOf<CameraDevice?>(null) }
    var captureSession by remember { mutableStateOf<CameraCaptureSession?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    val requestPermission: () -> Unit = {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        Unit
    }

    val closeCamera: () -> Unit = {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        Unit
    }

    val openCamera: (Context, Surface, Int, Int) -> Unit = { ctx, surface, _, _ ->
        openCameraInternal(
            context = ctx,
            surface = surface,
            onDeviceOpened = { device -> cameraDevice = device },
            onSessionCreated = { session -> captureSession = session },
            onDeviceClosed = {
                cameraDevice = null
                captureSession = null
            }
        )
        Unit
    }

    DisposableEffect(Unit) {
        onDispose {
            closeCamera()
        }
    }

    return CameraState(
        hasPermission = hasPermission,
        cameraDevice = cameraDevice,
        captureSession = captureSession,
        requestPermission = requestPermission,
        openCamera = openCamera,
        closeCamera = closeCamera
    )
}
