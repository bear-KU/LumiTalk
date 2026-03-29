package com.lumitalk.ui.components

import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.media.ImageReader
import com.lumitalk.NativeBridge
import com.lumitalk.util.camera.CameraMode
import com.lumitalk.util.camera.CameraState
import com.lumitalk.util.codec.DecoderState
import com.lumitalk.util.codec.EncoderState
import com.lumitalk.util.codec.MuxerState

@Composable
fun CameraPreviewCenter(
    cameraState: CameraState,
    nativeBridge: NativeBridge,
    roiRadius: Int,
    onResultAvailable: (String) -> Unit,
    onStateUpdated: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config  = cameraState.currentConfig

    key(config) {
        val roiWidth  = roiRadius * 2
        val roiHeight = roiRadius * 2

        fun processRoiFromBuffer(yBuffer: ByteArray, width: Int, height: Int, rowStride: Int) {
            android.util.Log.d("CameraPreviewCenter", "processRoiFromBuffer called") 
            val startRow = height / 2 - roiRadius
            val startCol = width  / 2 - roiRadius
            val roiBytes = ByteArray(roiWidth * roiHeight)
            for (row in 0 until roiHeight) {
                val srcOffset = (startRow + row) * rowStride + startCol
                yBuffer.copyInto(roiBytes, row * roiWidth, srcOffset, srcOffset + roiWidth)
            }
            nativeBridge.pushFrameCenter(roiBytes, roiWidth, roiHeight)
            onStateUpdated(nativeBridge.getStateCenter())
            val result = nativeBridge.getResultCenter()
            if (result.isNotEmpty()) onResultAvailable(result)
        }

        if (config.mode == CameraMode.HIGH_SPEED) {
            val yBuffer    = remember { ByteArray(config.width * config.height) }
            val muxerState = remember { MuxerState() }
            val decoderState = remember {
                DecoderState(
                    width  = config.width,
                    height = config.height,
                    yBuffer = yBuffer,
                    onFrameAvailable = { bytes, width, height ->
                        processRoiFromBuffer(bytes, width, height, width)
                    }
                )
            }
            val encoderState = remember {
                EncoderState(
                    config       = config,
                    muxerState   = muxerState,
                    decoderState = decoderState,
                    onFpsUpdated = {}
                )
            }

            DisposableEffect(Unit) {
                onDispose {
                    nativeBridge.stopReceiveCenter()
                    muxerState.release()
                    decoderState.release()
                    encoderState.release()
                }
            }

            AndroidView(
                modifier = modifier,
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                surface: SurfaceTexture, width: Int, height: Int
                            ) {
                                surface.setDefaultBufferSize(config.width, config.height)
                                encoderState.start()
                                cameraState.openCamera(
                                    ctx,
                                    Surface(surface),
                                    listOf(encoderState.inputSurface),
                                    config
                                )
                            }
                            override fun onSurfaceTextureSizeChanged(
                                surface: SurfaceTexture, width: Int, height: Int) {}
                            override fun onSurfaceTextureDestroyed(
                                surface: SurfaceTexture): Boolean {
                                cameraState.closeCamera()
                                return true
                            }
                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                        }
                    }
                }
            )
        } else {
            val imageReader = remember {
                ImageReader.newInstance(
                    config.width, config.height, ImageFormat.YUV_420_888, 2
                ).apply {
                    setOnImageAvailableListener({ reader ->
                        val image = reader.acquireLatestImage()
                            ?: return@setOnImageAvailableListener
                        val yPlane    = image.planes[0]
                        val yBuffer   = yPlane.buffer
                        val rowStride = yPlane.rowStride
                        val startRow  = config.height / 2 - roiRadius
                        val startCol  = config.width  / 2 - roiRadius
                        val roiBytes  = ByteArray(roiWidth * roiHeight)
                        for (row in 0 until roiHeight) {
                            yBuffer.position((startRow + row) * rowStride + startCol)
                            yBuffer.get(roiBytes, row * roiWidth, roiWidth)
                        }
                        image.close()
                        nativeBridge.pushFrameCenter(roiBytes, roiWidth, roiHeight)
                        onStateUpdated(nativeBridge.getStateCenter())
                        val result = nativeBridge.getResultCenter()
                        if (result.isNotEmpty()) onResultAvailable(result)
                    }, null)
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    nativeBridge.stopReceiveCenter()
                    imageReader.close()
                }
            }

            AndroidView(
                modifier = modifier,
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                surface: SurfaceTexture, width: Int, height: Int
                            ) {
                                surface.setDefaultBufferSize(config.width, config.height)
                                cameraState.openCamera(
                                    ctx,
                                    Surface(surface),
                                    listOf(imageReader.surface),
                                    config
                                )
                            }
                            override fun onSurfaceTextureSizeChanged(
                                surface: SurfaceTexture, width: Int, height: Int) {}
                            override fun onSurfaceTextureDestroyed(
                                surface: SurfaceTexture): Boolean {
                                cameraState.closeCamera()
                                return true
                            }
                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                        }
                    }
                }
            )
        }
    }
}
