package com.lumitalk.util.codec

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.provider.MediaStore
import java.io.File
import java.nio.ByteBuffer

class MuxerState {
    var muxer: MediaMuxer? = null
    var trackIndex: Int = -1
    var isStarted: Boolean = false
    var outputFile: File? = null
    var outputFormat: MediaFormat? = null
    var firstPresentationTimeUs: Long = -1L

    fun start(context: Context) {
        val file = File(context.cacheDir, "lumitalk_${System.currentTimeMillis()}.mp4")
        outputFile = file
        muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        trackIndex = -1
        isStarted = false
        firstPresentationTimeUs = -1L

        outputFormat?.let { format ->
            trackIndex = muxer!!.addTrack(format)
            muxer!!.start()
            isStarted = true
        }
    }

    fun stop(context: Context) {
        val wasStarted = isStarted
        isStarted = false
        if (wasStarted) {
            try { muxer?.stop() } catch (e: Exception) { e.printStackTrace() }
        }
        muxer?.release()
        muxer = null
        outputFile?.let { file ->
            if (file.exists() && file.length() > 0) {
                saveVideoToGallery(context, file)
            }
        }
        outputFile = null
    }

    fun release() {
        if (isStarted) {
            muxer?.stop()
            muxer?.release()
        }
        muxer = null
    }

    fun onOutputFormatChanged(format: MediaFormat) {
        outputFormat = format
        if (muxer != null && !isStarted) {
            trackIndex = muxer!!.addTrack(format)
            muxer!!.start()
            isStarted = true
        }
    }

    fun writeSampleData(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (!isStarted || trackIndex < 0 || info.size <= 0) return
        if (firstPresentationTimeUs < 0) {
            firstPresentationTimeUs = info.presentationTimeUs
        }
        val adjustedInfo = MediaCodec.BufferInfo().apply {
            offset = info.offset
            size = info.size
            flags = info.flags
            presentationTimeUs = info.presentationTimeUs - firstPresentationTimeUs
        }
        muxer?.writeSampleData(trackIndex, buffer, adjustedInfo)
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
        file.inputStream().use { input -> input.copyTo(output) }
    }
    file.delete()
}
