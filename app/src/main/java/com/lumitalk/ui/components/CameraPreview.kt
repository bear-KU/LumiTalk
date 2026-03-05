package com.lumitalk.ui.components

import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.media.ImageReader
import com.lumitalk.util.codec.EncoderState
import com.lumitalk.util.codec.DecoderState
import com.lumitalk.util.codec.MuxerState
import com.lumitalk.util.camera.CameraMode
import com.lumitalk.util.camera.CameraState

@Composable
fun CameraPreview(
    cameraState: CameraState,
    onFrameAvailable: (ByteArray, Int, Int) -> Unit,
    onFpsUpdated: (Float) -> Unit,
    isRecording: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config = cameraState.currentConfig

    key(config) {
        val yBuffer = remember { ByteArray(config.width * config.height) }

        val muxerState = remember { MuxerState() }

        val decoderState = remember {
            DecoderState(
                width = config.width,
                height = config.height,
                yBuffer = yBuffer,
                onFrameAvailable = onFrameAvailable
            )
        }

        val encoderState = remember {
            EncoderState(
                config = config,
                muxerState = muxerState,
                decoderState = decoderState,
                onFpsUpdated = onFpsUpdated
            )
        }

        LaunchedEffect(isRecording) {
            if (isRecording) {
                muxerState.start(context)
            } else if (muxerState.muxer != null) {
                muxerState.stop(context)
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                muxerState.release()
                decoderState.release()
                encoderState.release()
            }
        }

        if (config.mode == CameraMode.HIGH_SPEED) {
            AndroidView(
                modifier = modifier,
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                surface: SurfaceTexture,
                                width: Int,
                                height: Int
                            ) {
                                surface.setDefaultBufferSize(config.width, config.height)
                                encoderState.start()
                                cameraState.openCamera(
                                    context,
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
                        val image =
                            reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                        val yPlane = image.planes[0]
                        val yPlaneBuffer = yPlane.buffer
                        val rowStride = yPlane.rowStride
                        if (rowStride == config.width) {
                            yPlaneBuffer.get(yBuffer, 0, config.width * config.height)
                        } else {
                            for (row in 0 until config.height) {
                                yPlaneBuffer.position(row * rowStride)
                                yPlaneBuffer.get(yBuffer, row * config.width, config.width)
                            }
                        }
                        image.close()
                        onFrameAvailable(yBuffer, config.width, config.height)
                    }, null)
                }
            }

            AndroidView(
                modifier = modifier,
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                surface: SurfaceTexture,
                                width: Int,
                                height: Int
                            ) {
                                surface.setDefaultBufferSize(config.width, config.height)
                                encoderState.start()
                                cameraState.openCamera(
                                    context,
                                    Surface(surface),
                                    listOf(imageReader.surface, encoderState.inputSurface),
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
