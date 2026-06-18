package com.lumitalk.ui.components

import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import com.lumitalk.NativeBridge
import com.lumitalk.util.camera.CameraMode
import com.lumitalk.util.camera.CameraState
import com.lumitalk.util.camera.GlesCropper

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

        fun processRoiFromFullBuffer(yBuffer: ByteBuffer, width: Int, height: Int, rowStride: Int) {
            val startRow = height / 2 - roiRadius
            val startCol = width  / 2 - roiRadius
            val roiBytes = ByteArray(roiWidth * roiHeight)
            for (row in 0 until roiHeight) {
                val srcOffset = (startRow + row) * rowStride + startCol
                yBuffer.position(srcOffset)
                yBuffer.get(roiBytes, row * roiWidth, roiWidth)
            }
            nativeBridge.pushFrameCenter(roiBytes, roiWidth, roiHeight)
            onStateUpdated(nativeBridge.getStateCenter())
            val result = nativeBridge.getResultCenter()
            if (result.isNotEmpty()) onResultAvailable(result)
        }

        var frameCountForUi = 0

        fun processAlreadyCroppedBuffer(yBuffer: ByteArray, width: Int, height: Int) {
            nativeBridge.pushFrameCenter(yBuffer, width, height)
            
            frameCountForUi++
            if (frameCountForUi % 1 == 0) { // 描画が重い場合はフレームスキップを検討
                onStateUpdated(nativeBridge.getStateCenter())
            }
            
            val result = nativeBridge.getResultCenter()
            if (result.isNotEmpty()) onResultAvailable(result)
        }

        if (config.mode == CameraMode.HIGH_SPEED) {            
            val readerThread = remember { HandlerThread("RoiReaderThread").apply { start() } }
            val readerHandler = remember { Handler(readerThread.looper) }

            val roiReader = remember {
                ImageReader.newInstance(roiWidth, roiHeight, PixelFormat.RGBA_8888, 2).apply {
                    setOnImageAvailableListener({ reader ->
                        val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                        val plane = image.planes[0]
                        val buffer = plane.buffer
                        val pixelStride = plane.pixelStride
                        val rowStride = plane.rowStride

                        val roiBytes = ByteArray(roiWidth * roiHeight)
                        var yIdx = 0
                        
                        for (row in 0 until roiHeight) {
                            var colOffset = row * rowStride
                            for (col in 0 until roiWidth) {
                                // roiBytes[yIdx++] = buffer.get(colOffset + 1) // G Channel をY成分として使用
                                val r = buffer.get(colOffset + 0).toInt() and 0xFF
                                val g = buffer.get(colOffset + 1).toInt() and 0xFF
                                val b = buffer.get(colOffset + 2).toInt() and 0xFF
                                roiBytes[yIdx++] = (0.299f * r + 0.587f * g + 0.114f * b).toInt().toByte()
                                colOffset += pixelStride
                            }
                        }
                        image.close()

                        processAlreadyCroppedBuffer(roiBytes, roiWidth, roiHeight)
                    }, readerHandler)
                }
            }

            val cropper = remember {
                GlesCropper(
                    srcWidth       = config.width,
                    srcHeight      = config.height,
                    roiWidth       = roiWidth,
                    roiHeight      = roiHeight,
                    roiSurface     = roiReader.surface
                )
            }

            DisposableEffect(Unit) {
                onDispose {
                    nativeBridge.stopReceiveCenter()
                    cropper.release()
                    roiReader.close()
                    readerThread.quitSafely()
                }
            }

            AndroidView(
                modifier = modifier,
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                st: SurfaceTexture, width: Int, height: Int
                            ) {
                                st.setDefaultBufferSize(config.width, config.height)
                                
                                cropper.init {
                                    cropper.setPreviewSurface(Surface(st))
                                    
                                    cropper.cameraSurface?.let { inputSurface ->
                                        cameraState.openCamera(
                                            ctx,
                                            inputSurface,
                                            emptyList(),
                                            config
                                        )
                                    }
                                }
                            }
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                cameraState.closeCamera()
                                return true
                            }
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
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
                        processRoiFromFullBuffer(yBuffer, config.width, config.height, rowStride)
                        image.close()
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
