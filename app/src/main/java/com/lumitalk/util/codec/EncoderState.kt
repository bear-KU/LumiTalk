package com.lumitalk.util.codec

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.nio.ByteBuffer
import com.lumitalk.util.camera.CameraConfig
import com.lumitalk.util.camera.CameraMode

class EncoderState(
    private val config: CameraConfig,
    private val muxerState: MuxerState,
    private val decoderState: DecoderState,
    private val onFpsUpdated: (Float) -> Unit
) {
    val codec: MediaCodec
    val inputSurface: Surface

    private var frameCount = 0
    private var lastTime = System.currentTimeMillis()
    private val feedHandlerThread = HandlerThread("DecoderFeeder").also { it.start() }
    private val feedHandler = Handler(feedHandlerThread.looper)
    private val feedBufferInfos = Array(2) { MediaCodec.BufferInfo() }
    private var feedBufferIndex = 0

    init {
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

        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
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

                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        codec.releaseOutputBuffer(index, false)
                        return
                    }

                    outputBuffer.position(info.offset)
                    outputBuffer.limit(info.offset + info.size)

                    muxerState.writeSampleData(outputBuffer, info)
                    outputBuffer.position(info.offset)

                    if (config.mode == CameraMode.HIGH_SPEED) {
                        val infoCopy = feedBufferInfos[feedBufferIndex]
                        feedBufferIndex = (feedBufferIndex + 1) % 2
                        infoCopy.set(info.offset, info.size, info.presentationTimeUs, info.flags)

                        feedHandler.post {
                            decoderState.feedBuffer(outputBuffer, infoCopy)
                            codec.releaseOutputBuffer(index, false)
                        }
                        // codec.releaseOutputBuffer(index, false)

                        frameCount++
                        val now = System.currentTimeMillis()
                        if (now - lastTime >= 1000) {
                            val fps = frameCount * 1000f / (now - lastTime)
                            onFpsUpdated(fps)
                            frameCount = 0
                            lastTime = now
                        }
                        return
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

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                    muxerState.onOutputFormatChanged(format)
                    if (config.mode == CameraMode.HIGH_SPEED) {
                        decoderState.initialize(format)
                    }
                }
            }, handler)
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }

        inputSurface = codec.createInputSurface()
    }

    fun start() {
        codec.start()
    }

    fun release() {
        feedHandlerThread.quitSafely()
        codec.stop()
        codec.release()
    }
}
