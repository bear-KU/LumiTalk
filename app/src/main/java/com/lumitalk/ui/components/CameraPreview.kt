package com.lumitalk.ui.components

import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.LaunchedEffect
import android.provider.MediaStore
import com.lumitalk.util.CameraMode
import com.lumitalk.util.CameraState
import java.io.File

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
        val muxerState = remember {
            object {
                var muxer: MediaMuxer? = null
                var trackIndex: Int = -1
                var isStarted: Boolean = false
                var outputFile: File? = null
                var outputFormat: MediaFormat? = null
                var firstPresentationTimeUs: Long = -1L
            }
        }

        val mediaCodec = remember {
            var frameCount = 0
            var lastTime = System.currentTimeMillis()

            val format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                config.width,
                config.height
            ).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, 10_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val handlerThread = HandlerThread("MediaCodecCallback").also { it.start() }
            val handler = Handler(handlerThread.looper)

            MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                setCallback(object : MediaCodec.Callback() {
                    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {}

                        override fun onOutputBufferAvailable(
                            codec: MediaCodec,
                            index: Int,
                            info: MediaCodec.BufferInfo
                        ) {
                            val outputBuffer = codec.getOutputBuffer(index) ?: run {
                                codec.releaseOutputBuffer(index, false)
                                return
                            }

                            // コーデック設定データはスキップ
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                codec.releaseOutputBuffer(index, false)
                                return
                            }

                            // バッファの位置とサイズを正しく設定
                            outputBuffer.position(info.offset)
                            outputBuffer.limit(info.offset + info.size)

                            if (muxerState.isStarted && muxerState.trackIndex >= 0 && info.size > 0) {
                                if (muxerState.firstPresentationTimeUs < 0) {
                                    muxerState.firstPresentationTimeUs = info.presentationTimeUs
                                }

                                // タイムスタンプを最初のフレームからの相対時間に補正
                                val adjustedInfo = MediaCodec.BufferInfo().apply {
                                    offset = info.offset
                                    size = info.size
                                    flags = info.flags
                                    presentationTimeUs = info.presentationTimeUs - muxerState.firstPresentationTimeUs
                                }

                                muxerState.muxer?.writeSampleData(
                                    muxerState.trackIndex,
                                    outputBuffer,
                                    adjustedInfo
                                )
                            }

                            codec.releaseOutputBuffer(index, false)

                            frameCount++
                            val now = System.currentTimeMillis()
                            if (now - lastTime >= 1000) {
                                val fps = frameCount * 1000f / (now - lastTime)
                                onFpsUpdated(fps)
                                frameCount = 0
                                lastTime = now
                            }
                        }

                    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {}

                    override fun onOutputFormatChanged(
                        codec: MediaCodec,
                        format: MediaFormat
                    ) {
                        muxerState.outputFormat = format
                        if (muxerState.muxer != null && !muxerState.isStarted) {
                            muxerState.trackIndex = muxerState.muxer!!.addTrack(format)
                            muxerState.muxer!!.start()
                            muxerState.isStarted = true
                        }
                    }
                }, handler)
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
        }

        LaunchedEffect(isRecording) {
            if (isRecording) {
                val file = File(
                    context.cacheDir,
                    "lumitalk_${System.currentTimeMillis()}.mp4"
                )
                muxerState.outputFile = file
                muxerState.muxer = MediaMuxer(
                    file.absolutePath,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                )
                muxerState.trackIndex = -1
                muxerState.isStarted = false
                muxerState.firstPresentationTimeUs = -1L

                muxerState.outputFormat?.let { format ->
                    muxerState.trackIndex = muxerState.muxer!!.addTrack(format)
                    muxerState.muxer!!.start()
                    muxerState.isStarted = true
                }
            } else if (muxerState.muxer != null) {
                // MediaCodecのコールバックスレッドと競合しないよう
                // isStarted を先に false にしてから stop する
                val wasStarted = muxerState.isStarted
                muxerState.isStarted = false

                if (wasStarted) {
                    try {
                        muxerState.muxer?.stop()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                muxerState.muxer?.release()
                muxerState.muxer = null

                muxerState.outputFile?.let { file ->
                    if (file.exists() && file.length() > 0) {
                        saveVideoToGallery(context, file)
                    }
                }
                muxerState.outputFile = null
            }
        }

        val inputSurface = remember { mediaCodec.createInputSurface() }

        DisposableEffect(Unit) {
            onDispose {
                if (muxerState.isStarted) {
                    muxerState.muxer?.stop()
                    muxerState.muxer?.release()
                }
                mediaCodec.stop()
                mediaCodec.release()
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
                                mediaCodec.start()
                                cameraState.openCamera(
                                    context,
                                    Surface(surface),
                                    listOf(inputSurface),
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
                        val plane = image.planes[0]
                        val buffer = plane.buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        onFrameAvailable(bytes, image.width, image.height)
                        image.close()
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
                                mediaCodec.start()
                                cameraState.openCamera(
                                    context,
                                    Surface(surface),
                                    listOf(imageReader.surface, inputSurface),
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

private fun saveVideoToGallery(context: Context, file: File) {
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/LumiTalk")
    }

    val uri = context.contentResolver.insert(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        values
    ) ?: return

    context.contentResolver.openOutputStream(uri)?.use { output ->
        file.inputStream().use { input ->
            input.copyTo(output)
        }
    }

    file.delete()
}
