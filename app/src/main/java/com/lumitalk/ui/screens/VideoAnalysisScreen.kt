package com.lumitalk.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.media.Image
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumitalk.NativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class VideoDetectionBox(val x: Int, val y: Int, val w: Int, val h: Int)
private data class VideoDecodedResult(val x: Int, val y: Int, val ascii: String)
private enum class AnalysisPreviewMode {
    WITH_DRAWING,
    WITHOUT_DRAWING
}

@Composable
fun VideoAnalysisScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val nativeBridge = remember { NativeBridge() }
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("動画を選択してください") }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var decodedResults by remember { mutableStateOf<List<VideoDecodedResult>>(emptyList()) }

    fun startAnalysis(mode: AnalysisPreviewMode) {
        val uri = selectedUri ?: return
        if (isAnalyzing) return
        isAnalyzing = true
        previewBitmap = null
        decodedResults = emptyList()
        statusText = if (mode == AnalysisPreviewMode.WITH_DRAWING) {
            "解析中...（描画あり）"
        } else {
            "解析中...（描画なし）"
        }
        scope.launch {
            try {
                analyzeVideo(
                    context = context,
                    uri = uri,
                    nativeBridge = nativeBridge,
                    previewMode = mode,
                    onFrame = { bitmap, status, decoded ->
                        if (bitmap != null) previewBitmap = bitmap
                        statusText = status
                        if (decoded.isNotEmpty()) {
                            decodedResults = decodedResults + decoded
                        }
                    }
                )
                statusText = "解析完了（${decodedResults.size}件デコード）"
            } catch (e: Exception) {
                statusText = "解析失敗: ${e.message ?: "unknown"}"
            } finally {
                isAnalyzing = false
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            statusText = "選択済み: ${uri.lastPathSegment ?: uri.toString()}"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("戻る")
        }

        Button(onClick = { picker.launch("video/*") }, modifier = Modifier.fillMaxWidth()) {
            Text("動画ファイルを選択")
        }

        Button(
            onClick = { startAnalysis(AnalysisPreviewMode.WITH_DRAWING) },
            enabled = selectedUri != null && !isAnalyzing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isAnalyzing) "解析中..." else "解析開始（描画あり）")
        }

        Button(
            onClick = { startAnalysis(AnalysisPreviewMode.WITHOUT_DRAWING) },
            enabled = selectedUri != null && !isAnalyzing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isAnalyzing) "解析中..." else "解析開始（描画なし）")
        }

        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        decodedResults.forEach { result ->
            Text(
                text = "[x=${result.x}, y=${result.y}] ${result.ascii}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap!!.asImageBitmap(),
                contentDescription = "analysis preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

private suspend fun analyzeVideo(
    context: Context,
    uri: Uri,
    nativeBridge: NativeBridge,
    previewMode: AnalysisPreviewMode,
    onFrame: (Bitmap?, String, List<VideoDecodedResult>) -> Unit
) {
    withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        val trackIndex = findVideoTrack(extractor)
            ?: throw IllegalArgumentException("動画トラックが見つかりません")
        extractor.selectTrack(trackIndex)

        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)
            ?: throw IllegalArgumentException("動画MIMEを取得できません")

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var frameIndex = 0

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                val ptsUs = extractor.sampleTime
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, ptsUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    outputIndex >= 0 -> {
                        if (info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            val image = codec.getOutputImage(outputIndex)
                            if (image != null) {
                                val yBytes = extractYPlane(image)
                                val width = image.width
                                val height = image.height
                                val raw = nativeBridge.processFrame(yBytes, width, height)
                                val decoded = parseDecodedResults(raw)
                                val preview = if (previewMode == AnalysisPreviewMode.WITH_DRAWING) {
                                    yPlaneToBitmap(yBytes, width, height).also {
                                        val boxes = parseBoxes(raw)
                                        drawBoxes(it, boxes)
                                    }
                                } else {
                                    null
                                }

                                frameIndex++
                                withContext(Dispatchers.Main) {
                                    onFrame(preview, "解析中: ${frameIndex}フレーム", decoded)
                                }
                                image.close()
                            }
                        }

                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }
    }
}

private fun findVideoTrack(extractor: MediaExtractor): Int? {
    for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
        if (mime.startsWith("video/")) {
            return i
        }
    }
    return null
}

private fun extractYPlane(image: Image): ByteArray {
    val width = image.width
    val height = image.height
    val yPlane = image.planes[0]
    val buffer = yPlane.buffer
    val rowStride = yPlane.rowStride
    val pixelStride = yPlane.pixelStride

    val out = ByteArray(width * height)
    var outOffset = 0
    for (row in 0 until height) {
        val rowStart = row * rowStride
        for (col in 0 until width) {
            out[outOffset++] = buffer.get(rowStart + col * pixelStride)
        }
    }
    return out
}

private fun yPlaneToBitmap(yBytes: ByteArray, width: Int, height: Int): Bitmap {
    val pixels = IntArray(width * height)
    for (i in yBytes.indices) {
        val y = yBytes[i].toInt() and 0xFF
        pixels[i] = (0xFF shl 24) or (y shl 16) or (y shl 8) or y
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}

private fun parseBoxes(raw: String): List<VideoDetectionBox> {
    val boxRegex = Regex("""\{"x":(-?\d+),"y":(-?\d+),"w":(\d+),"h":(\d+)\}""")
    return boxRegex.findAll(raw).map { m ->
        VideoDetectionBox(
            m.groupValues[1].toInt(),
            m.groupValues[2].toInt(),
            m.groupValues[3].toInt(),
            m.groupValues[4].toInt()
        )
    }.toList()
}

private fun parseDecodedResults(raw: String): List<VideoDecodedResult> {
    val decodedRegex = Regex("""\{"x":(\d+),"y":(\d+),"ascii":"([^"]*)"\}""")
    return decodedRegex.findAll(raw)
        .map { match ->
            VideoDecodedResult(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3]
            )
        }
        .filter { it.ascii.isNotEmpty() }
        .toList()
}

private fun drawBoxes(bitmap: Bitmap, boxes: List<VideoDetectionBox>) {
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        color = android.graphics.Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    boxes.forEach { box ->
        canvas.drawRect(
            box.x.toFloat(),
            box.y.toFloat(),
            (box.x + box.w).toFloat(),
            (box.y + box.h).toFloat(),
            paint
        )
    }
}
