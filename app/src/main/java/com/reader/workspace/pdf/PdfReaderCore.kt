package com.reader.workspace.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.models.selection.SelectionBoundary
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import kotlin.math.roundToInt

data class PdfPixelSize(
    val width: Int,
    val height: Int,
)

data class PdfPointRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class PdfTextHighlightRequest(
    val axisId: String,
    val startOffset: Int,
    val endOffsetExclusive: Int,
    val colorArgb: Long,
)

data class PdfResolvedHighlight(
    val axisId: String,
    val colorArgb: Long,
    val rects: List<PdfPointRect>,
)

data class PdfAnalysisRender(
    val bitmap: Bitmap,
    val pageWidthPoints: Int,
    val pageHeightPoints: Int,
)

object PdfPageSizing {
    fun fitWidth(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
    ): PdfPixelSize {
        require(sourceWidth > 0) { "sourceWidth must be positive" }
        require(sourceHeight > 0) { "sourceHeight must be positive" }
        require(targetWidth > 0) { "targetWidth must be positive" }

        val scale = targetWidth.toFloat() / sourceWidth
        return PdfPixelSize(
            width = targetWidth,
            height = (sourceHeight * scale).roundToInt().coerceAtLeast(1),
        )
    }
}

object PdfTextSupport {
    const val MIN_NATIVE_TEXT_API: Int = 35

    fun isNativeTextExtractionAvailable(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        sdkInt >= MIN_NATIVE_TEXT_API
}

object PdfResearchOverlay {
    const val MAX_HIGHLIGHTS_PER_PAGE: Int = 240
    const val FILL_ALPHA: Int = 78
    const val UNDERLINE_ALPHA: Int = 210

    fun isAvailable(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        sdkInt >= PdfTextSupport.MIN_NATIVE_TEXT_API

    fun scaleRect(rect: PdfPointRect, scale: Float): PdfPointRect {
        require(scale > 0f) { "scale must be positive" }
        return PdfPointRect(
            left = rect.left * scale,
            top = rect.top * scale,
            right = rect.right * scale,
            bottom = rect.bottom * scale,
        )
    }

    fun withAlpha(colorArgb: Long, alpha: Int): Int {
        val source = colorArgb.toInt()
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(source),
            Color.green(source),
            Color.blue(source),
        )
    }
}

class PdfRendererSession(file: File) : Closeable {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val lock = Any()

    val pageCount: Int
        get() = renderer.pageCount

    fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int,
        highlights: List<PdfResolvedHighlight> = emptyList(),
    ): Bitmap = synchronized(lock) {
        require(pageIndex in 0 until renderer.pageCount) { "Page index out of range: $pageIndex" }
        require(targetWidthPx > 0) { "targetWidthPx must be positive" }

        val page = renderer.openPage(pageIndex)
        try {
            val target = PdfPageSizing.fitWidth(
                sourceWidth = page.width,
                sourceHeight = page.height,
                targetWidth = targetWidthPx,
            )
            val bitmap = Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)

            val scale = target.width.toFloat() / page.width
            val matrix = Matrix().apply { postScale(scale, scale) }
            page.render(
                bitmap,
                null,
                matrix,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
            )
            drawResearchHighlights(bitmap, highlights, scale)
            bitmap
        } finally {
            page.close()
        }
    }

    fun renderPageForAnalysis(
        pageIndex: Int,
        targetWidthPx: Int,
    ): PdfAnalysisRender = synchronized(lock) {
        require(pageIndex in 0 until renderer.pageCount) { "Page index out of range: $pageIndex" }
        require(targetWidthPx > 0) { "targetWidthPx must be positive" }

        val page = renderer.openPage(pageIndex)
        try {
            val target = PdfPageSizing.fitWidth(
                sourceWidth = page.width,
                sourceHeight = page.height,
                targetWidth = targetWidthPx,
            )
            val bitmap = Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            val scale = target.width.toFloat() / page.width
            val matrix = Matrix().apply { postScale(scale, scale) }
            page.render(
                bitmap,
                null,
                matrix,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
            )
            PdfAnalysisRender(
                bitmap = bitmap,
                pageWidthPoints = page.width,
                pageHeightPoints = page.height,
            )
        } finally {
            page.close()
        }
    }

    fun extractPageText(pageIndex: Int): String? = synchronized(lock) {
        require(pageIndex in 0 until renderer.pageCount) { "Page index out of range: $pageIndex" }
        if (!PdfTextSupport.isNativeTextExtractionAvailable()) return@synchronized null

        val page = renderer.openPage(pageIndex)
        try {
            page.textContents
                .asSequence()
                .map { it.text }
                .joinToString(separator = "")
        } finally {
            page.close()
        }
    }

    fun resolveTextHighlights(
        pageIndex: Int,
        requests: List<PdfTextHighlightRequest>,
    ): List<PdfResolvedHighlight> = synchronized(lock) {
        require(pageIndex in 0 until renderer.pageCount) { "Page index out of range: $pageIndex" }
        if (!PdfResearchOverlay.isAvailable() || requests.isEmpty()) return@synchronized emptyList()

        val page = renderer.openPage(pageIndex)
        try {
            requests
                .asSequence()
                .filter { it.startOffset >= 0 && it.endOffsetExclusive > it.startOffset }
                .take(PdfResearchOverlay.MAX_HIGHLIGHTS_PER_PAGE)
                .mapNotNull { request ->
                    val selection = runCatching {
                        page.selectContent(
                            SelectionBoundary(request.startOffset),
                            SelectionBoundary(request.endOffsetExclusive),
                        )
                    }.getOrNull() ?: return@mapNotNull null

                    val rects = selection.selectedTextContents
                        .asSequence()
                        .flatMap { content -> content.bounds.asSequence() }
                        .map(RectF::toPdfPointRect)
                        .filter { rect -> rect.right > rect.left && rect.bottom > rect.top }
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
        } finally {
            page.close()
        }
    }

    override fun close() = synchronized(lock) {
        renderer.close()
        descriptor.close()
    }
}

private fun RectF.toPdfPointRect(): PdfPointRect = PdfPointRect(
    left = left,
    top = top,
    right = right,
    bottom = bottom,
)

private fun drawResearchHighlights(
    bitmap: Bitmap,
    highlights: List<PdfResolvedHighlight>,
    scale: Float,
) {
    if (highlights.isEmpty()) return

    val canvas = Canvas(bitmap)
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val underlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    highlights.forEach { highlight ->
        fillPaint.color = PdfResearchOverlay.withAlpha(
            highlight.colorArgb,
            PdfResearchOverlay.FILL_ALPHA,
        )
        underlinePaint.color = PdfResearchOverlay.withAlpha(
            highlight.colorArgb,
            PdfResearchOverlay.UNDERLINE_ALPHA,
        )

        highlight.rects.forEach { sourceRect ->
            val rect = PdfResearchOverlay.scaleRect(sourceRect, scale)
            val androidRect = RectF(rect.left, rect.top, rect.right, rect.bottom)
            canvas.drawRoundRect(androidRect, 3f, 3f, fillPaint)

            val underlineHeight = (1.75f * scale).coerceIn(2f, 7f)
            canvas.drawRect(
                androidRect.left,
                (androidRect.bottom - underlineHeight).coerceAtLeast(androidRect.top),
                androidRect.right,
                androidRect.bottom,
                underlinePaint,
            )
        }
    }
}
