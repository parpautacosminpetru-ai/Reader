package com.reader.workspace.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import kotlin.math.roundToInt

data class PdfPixelSize(
    val width: Int,
    val height: Int,
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

class PdfRendererSession(file: File) : Closeable {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val lock = Any()

    val pageCount: Int
        get() = renderer.pageCount

    fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int,
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
            bitmap
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
                .filter { it.isNotBlank() }
                .joinToString(separator = "\n")
                .ifBlank { "" }
        } finally {
            page.close()
        }
    }

    override fun close() = synchronized(lock) {
        renderer.close()
        descriptor.close()
    }
}
