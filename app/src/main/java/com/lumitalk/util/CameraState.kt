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
    val currentConfig: CameraConfig,
    val requestPermission: () -> Unit,
    val openCamera: (Context, Surface, List<Surface>, CameraConfig) -> Unit,
    val closeCamera: () -> Unit,
    val switchMode: () -> Unit
)

private fun openCameraInternal(
    context: Context,
    previewSurface: Surface,
    analysisSurfaces: List<Surface>,
    config: CameraConfig,
    onDeviceOpened: (CameraDevice) -> Unit,
    onSessionCreated: (CameraCaptureSession) -> Unit,
    onDeviceClosed: () -> Unit
) {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
        cameraManager.getCameraCharacteristics(id)
            .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    } ?: return

    val allSurfaces = listOf(previewSurface) + analysisSurfaces

    val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            onDeviceOpened(camera)

            if (config.mode == CameraMode.HIGH_SPEED) {
                val captureRequest = camera.createCaptureRequest(
                    CameraDevice.TEMPLATE_RECORD
                ).apply {
                    allSurfaces.forEach { addTarget(it) }
                    set(
                        CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        android.util.Range(config.fps, config.fps)
                    )
                }

                @Suppress("DEPRECATION")
                camera.createConstrainedHighSpeedCaptureSession(
                    allSurfaces,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            onSessionCreated(session)
                            val highSpeedSession =
                                session as CameraConstrainedHighSpeedCaptureSession
                            val requests = highSpeedSession.createHighSpeedRequestList(
                                captureRequest.build()
                            )
                            session.setRepeatingBurst(requests, null, null)
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {}
                    },
                    null
                )
            } else {
                val captureRequest = camera.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW
                ).apply {
                    allSurfaces.forEach { addTarget(it) }
                    set(
                        CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        android.util.Range(config.fps, config.fps)
                    )
                }

                @Suppress("DEPRECATION")
                camera.createCaptureSession(
                    allSurfaces,
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
    var currentConfig by remember { mutableStateOf<CameraConfig>(STANDARD_CONFIG) }

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

    val openCamera: (Context, Surface, List<Surface>, CameraConfig) -> Unit =
        { ctx, previewSurface, analysisSurfaces, config ->
            openCameraInternal(
                context = ctx,
                previewSurface = previewSurface,
                analysisSurfaces = analysisSurfaces,
                config = config,
                onDeviceOpened = { device -> cameraDevice = device },
                onSessionCreated = { session -> captureSession = session },
                onDeviceClosed = {
                    cameraDevice = null
                    captureSession = null
                }
            )
            Unit
        }

    val switchMode: () -> Unit = {
        currentConfig = if (currentConfig.mode == CameraMode.STANDARD) {
            HIGH_SPEED_CONFIG
        } else {
            STANDARD_CONFIG
        }
        closeCamera()
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
        currentConfig = currentConfig,
        requestPermission = requestPermission,
        openCamera = openCamera,
        closeCamera = closeCamera,
        switchMode = switchMode
    )
}
