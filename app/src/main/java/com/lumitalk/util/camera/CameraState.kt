package com.lumitalk.util.camera

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
    val isAeLocked: Boolean,
    val requestPermission: () -> Unit,
    val openCamera: (Context, Surface, List<Surface>, CameraConfig) -> Unit,
    val closeCamera: () -> Unit,
    val switchMode: () -> Unit,
    val lockAeAf: () -> Unit,
    val unlockAeAf: () -> Unit
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
            android.util.Log.d("CameraState", "Camera opened")
            onDeviceOpened(camera)
            try {
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
            } catch (e: Exception) {
                android.util.Log.e("CameraState", "Failed to configure camera session", e)
                try {
                    camera.close()
                } catch (_: Exception) {}
                onDeviceClosed()
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
    var isAeLocked by remember { mutableStateOf(false) }
    var lastSurfaces by remember { mutableStateOf<List<Surface>>(emptyList()) }

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
        android.util.Log.d("CameraState", "Closing camera")
        if (captureSession != null || cameraDevice != null) {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
        }
        isAeLocked = false
        Unit
    }

    val openCamera: (Context, Surface, List<Surface>, CameraConfig) -> Unit =
        { ctx, previewSurface, analysisSurfaces, config ->
            lastSurfaces = listOf(previewSurface) + analysisSurfaces
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
                    isAeLocked = false
                }
            )
            Unit
        }

    val lockAeAf: () -> Unit = {
        val session = captureSession
        val device = cameraDevice
        val config = currentConfig
        if (session != null && device != null) {
            try {
                val requestBuilder = device.createCaptureRequest(
                    if (config.mode == CameraMode.HIGH_SPEED) CameraDevice.TEMPLATE_RECORD
                    else CameraDevice.TEMPLATE_PREVIEW
                ).apply {
                    lastSurfaces.forEach { addTarget(it) }
                    set(CaptureRequest.CONTROL_AE_LOCK, true)
                    set(CaptureRequest.CONTROL_AWB_LOCK, true)
                    set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        android.util.Range(config.fps, config.fps))
                }

                if (config.mode == CameraMode.HIGH_SPEED) {
                    val highSpeedSession = session as CameraConstrainedHighSpeedCaptureSession
                    val requests = highSpeedSession.createHighSpeedRequestList(
                        requestBuilder.build()
                    )
                    session.setRepeatingBurst(requests, null, null)
                } else {
                    session.setRepeatingRequest(requestBuilder.build(), null, null)
                }
                isAeLocked = true
                android.util.Log.d("CameraState", "AE/AF locked")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        Unit
    }

    val unlockAeAf: () -> Unit = {
        val session = captureSession
        val device = cameraDevice
        val config = currentConfig
        if (session != null && device != null) {
            try {
                val requestBuilder = device.createCaptureRequest(
                    if (config.mode == CameraMode.HIGH_SPEED) CameraDevice.TEMPLATE_RECORD
                    else CameraDevice.TEMPLATE_PREVIEW
                ).apply {
                    lastSurfaces.forEach { addTarget(it) }
                    set(CaptureRequest.CONTROL_AE_LOCK, false)
                    set(CaptureRequest.CONTROL_AWB_LOCK, false)
                    set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        android.util.Range(config.fps, config.fps))
                }

                if (config.mode == CameraMode.HIGH_SPEED) {
                    val highSpeedSession = session as CameraConstrainedHighSpeedCaptureSession
                    val requests = highSpeedSession.createHighSpeedRequestList(
                        requestBuilder.build()
                    )
                    session.setRepeatingBurst(requests, null, null)
                } else {
                    session.setRepeatingRequest(requestBuilder.build(), null, null)
                }
                isAeLocked = false
                android.util.Log.d("CameraState", "AE/AF unlocked")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    android.util.Log.d("CameraState", "ON_PAUSE: closing camera")
                    closeCamera()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            android.util.Log.d("CameraState", "onDispose: closing camera")
            lifecycleOwner.lifecycle.removeObserver(observer)
            closeCamera()
        }
    }

    return CameraState(
        hasPermission = hasPermission,
        cameraDevice = cameraDevice,
        captureSession = captureSession,
        currentConfig = currentConfig,
        isAeLocked = isAeLocked,
        requestPermission = requestPermission,
        openCamera = openCamera,
        closeCamera = closeCamera,
        switchMode = switchMode,
        lockAeAf = lockAeAf,
        unlockAeAf = unlockAeAf
    )
}
