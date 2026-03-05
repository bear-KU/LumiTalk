package com.lumitalk.util.codec

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import java.nio.ByteBuffer

class DecoderState(
    private val width: Int,
    private val height: Int,
    private val yBuffer: ByteArray,
    private val onFrameAvailable: (ByteArray, Int, Int) -> Unit,
) {
    var codec: MediaCodec? = null
    var isStarted: Boolean = false
    private val inputBufferQueue = java.util.concurrent.LinkedBlockingQueue<Int>()

    fun initialize(format: MediaFormat) {
        if (isStarted) return

        val handlerThread = HandlerThread("MediaDecoderCallback").also { it.start() }
        val handler = Handler(handlerThread.looper)

        codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            setCallback(object : MediaCodec.Callback() {

                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                    inputBufferQueue.offer(index)
                }

                override fun onOutputBufferAvailable(
                    codec: MediaCodec,
                    index: Int,
                    info: MediaCodec.BufferInfo
                ) {
                    if (info.size <= 0) {
                        codec.releaseOutputBuffer(index, false)
                        return
                    }

                    val image = codec.getOutputImage(index)
                    if (image != null) {
                        val yPlane = image.planes[0]
                        val yPlaneBuffer = yPlane.buffer
                        val rowStride = yPlane.rowStride
                        if (rowStride == width) {
                            yPlaneBuffer.get(yBuffer, 0, width * height)
                        } else {
                            for (row in 0 until height) {
                                yPlaneBuffer.position(row * rowStride)
                                yPlaneBuffer.get(yBuffer, row * width, width)
                            }
                        }
                        image.close()
                        onFrameAvailable(yBuffer, width, height)
                    }

                    codec.releaseOutputBuffer(index, false)
                }

                override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {}

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {}
            }, handler)

            configure(format, null, null, 0)
        }

        codec!!.start()
        isStarted = true
    }

    fun feedBuffer(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        val decoder = codec ?: return
        val inputIdx = inputBufferQueue.poll() ?: return
        val inputBuf = decoder.getInputBuffer(inputIdx) ?: return
        inputBuf.clear()
        inputBuf.put(buffer)
        decoder.queueInputBuffer(
            inputIdx, 0, info.size,
            info.presentationTimeUs, info.flags
        )
    }

    fun release() {
        if (isStarted) {
            codec?.stop()
            codec?.release()
            codec = null
            isStarted = false
        }
    }
}
