package com.reader.workspace.pdf

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable
import java.util.LinkedHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class PdfPageTextSource {
    NATIVE,
    OCR,
    NONE,
}

data class PdfOcrTextSpan(
    val startOffset: Int,
    val endOffsetExclusive: Int,
    val rect: PdfPointRect,
)

data class PdfPageTextResult(
    val text: String,
    val source: PdfPageTextSource,
    val spans: List<PdfOcrTextSpan> = emptyList(),
) {
    fun resolveOcrHighlights(
        requests: List<PdfTextHighlightRequest>,
    ): List<PdfResolvedHighlight> {
        if (source != PdfPageTextSource.OCR || requests.isEmpty() || spans.isEmpty()) {
            return emptyList()
        }

        return requests
            .asSequence()
            .filter { it.startOffset >= 0 && it.endOffsetExclusive > it.startOffset }
            .take(PdfResearchOverlay.MAX_HIGHLIGHTS_PER_PAGE)
            .mapNotNull { request ->
                val rects = spans
                    .asSequence()
                    .filter { span ->
                        span.endOffsetExclusive > request.startOffset &&
                            span.startOffset < request.endOffsetExclusive
                    }
                    .map(PdfOcrTextSpan::rect)
                    .distinct()
                    .toList()

                if (rects.isEmpty()) {
                    null
                } else {
                    PdfResolvedHighlight(
                        axisId = request.axisId,
                        colorArgb = request.colorArgb,
                        rects = rects,
                    )
                }
            }
            .toList()
    }
}

object PdfOfflineOcrConfig {
    const val ANALYSIS_WIDTH_PX: Int = 1800
    const val CACHE_PAGE_COUNT: Int = 8
}

class OfflinePdfOcrEngine : Closeable {
    private val recognizer: TextRecognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS,
    )

    suspend fun recognize(
        bitmap: Bitmap,
        pageWidthPoints: Int,
    ): PdfPageTextResult {
        require(bitmap.width > 0) { "bitmap width must be positive" }
        require(pageWidthPoints > 0) { "pageWidthPoints must be positive" }

        val recognized = recognizer
            .process(InputImage.fromBitmap(bitmap, 0))
            .awaitResult()
        return recognized.toPageTextResult(
            bitmapWidthPx = bitmap.width,
            pageWidthPoints = pageWidthPoints,
        )
    }

    override fun close() {
        recognizer.close()
    }
}

class PdfPageTextResolver(
    private val session: PdfRendererSession,
    private val ocrEngine: OfflinePdfOcrEngine = OfflinePdfOcrEngine(),
) : Closeable {
    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<Int, PdfPageTextResult>(
        PdfOfflineOcrConfig.CACHE_PAGE_COUNT + 1,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Int, PdfPageTextResult>?,
        ): Boolean = size > PdfOfflineOcrConfig.CACHE_PAGE_COUNT
    }

    suspend fun resolve(pageIndex: Int): PdfPageTextResult = mutex.withLock {
        cache[pageIndex]?.let { return@withLock it }

        val resolved = resolveUncached(pageIndex)
        cache[pageIndex] = resolved
        resolved
    }

    suspend fun resolveHighlights(
        pageIndex: Int,
        pageText: PdfPageTextResult,
        requests: List<PdfTextHighlightRequest>,
    ): List<PdfResolvedHighlight> = when (pageText.source) {
        PdfPageTextSource.NATIVE -> withContext(Dispatchers.IO) {
            session.resolveTextHighlights(pageIndex, requests)
        }
        PdfPageTextSource.OCR -> pageText.resolveOcrHighlights(requests)
        PdfPageTextSource.NONE -> emptyList()
    }

    private suspend fun resolveUncached(pageIndex: Int): PdfPageTextResult {
        val nativeText = withContext(Dispatchers.IO) {
            session.extractPageText(pageIndex)
        }
        if (!nativeText.isNullOrBlank()) {
            return PdfPageTextResult(
                text = nativeText,
                source = PdfPageTextSource.NATIVE,
            )
        }

        val rendered = withContext(Dispatchers.IO) {
            session.renderPageForAnalysis(
                pageIndex = pageIndex,
                targetWidthPx = PdfOfflineOcrConfig.ANALYSIS_WIDTH_PX,
            )
        }
        return try {
            val ocr = ocrEngine.recognize(
                bitmap = rendered.bitmap,
                pageWidthPoints = rendered.pageWidthPoints,
            )
            if (ocr.text.isBlank()) {
                PdfPageTextResult(
                    text = "",
                    source = PdfPageTextSource.NONE,
                )
            } else {
                ocr
            }
        } catch (_: Throwable) {
            PdfPageTextResult(
                text = nativeText.orEmpty(),
                source = if (nativeText.isNullOrBlank()) {
                    PdfPageTextSource.NONE
                } else {
                    PdfPageTextSource.NATIVE
                },
            )
        } finally {
            rendered.bitmap.recycle()
        }
    }

    override fun close() {
        synchronized(cache) { cache.clear() }
        ocrEngine.close()
    }
}

private fun Text.toPageTextResult(
    bitmapWidthPx: Int,
    pageWidthPoints: Int,
): PdfPageTextResult {
    val pointScale = pageWidthPoints.toFloat() / bitmapWidthPx.toFloat()
    val builder = StringBuilder()
    val spans = mutableListOf<PdfOcrTextSpan>()

    textBlocks.forEachIndexed { blockIndex, block ->
        block.lines.forEachIndexed { lineIndex, line ->
            val elements = line.elements
            if (elements.isEmpty()) {
                appendOcrToken(
                    builder = builder,
                    spans = spans,
                    token = line.text,
                    boundingBox = line.boundingBox,
                    pointScale = pointScale,
                )
            } else {
                elements.forEachIndexed { elementIndex, element ->
                    if (elementIndex > 0) builder.append(' ')
                    appendOcrToken(
                        builder = builder,
                        spans = spans,
                        token = element.text,
                        boundingBox = element.boundingBox,
                        pointScale = pointScale,
                    )
                }
            }

            if (lineIndex + 1 < block.lines.size) builder.append('\n')
        }
        if (blockIndex + 1 < textBlocks.size) builder.append("\n\n")
    }

    return PdfPageTextResult(
        text = builder.toString(),
        source = if (builder.isEmpty()) PdfPageTextSource.NONE else PdfPageTextSource.OCR,
        spans = spans,
    )
}

private fun appendOcrToken(
    builder: StringBuilder,
    spans: MutableList<PdfOcrTextSpan>,
    token: String,
    boundingBox: Rect?,
    pointScale: Float,
) {
    if (token.isEmpty()) return
    val start = builder.length
    builder.append(token)
    val end = builder.length

    boundingBox?.let { box ->
        spans += PdfOcrTextSpan(
            startOffset = start,
            endOffsetExclusive = end,
            rect = PdfPointRect(
                left = box.left * pointScale,
                top = box.top * pointScale,
                right = box.right * pointScale,
                bottom = box.bottom * pointScale,
            ),
        )
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener {
        continuation.resumeWithException(IllegalStateException("ML Kit task was cancelled"))
    }
}
