package com.reader.workspace.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePdfOcrMappingTest {
    @Test
    fun `OCR highlight resolves every token span touched by lexical hit`() {
        val page = PdfPageTextResult(
            text = "cauze reformei",
            source = PdfPageTextSource.OCR,
            spans = listOf(
                PdfOcrTextSpan(0, 5, PdfPointRect(10f, 20f, 60f, 35f)),
                PdfOcrTextSpan(6, 14, PdfPointRect(65f, 20f, 130f, 35f)),
            ),
        )

        val resolved = page.resolveOcrHighlights(
            listOf(
                PdfTextHighlightRequest(
                    axisId = "causes",
                    startOffset = 0,
                    endOffsetExclusive = 5,
                    colorArgb = 0xFFE53935L,
                ),
            ),
        )

        assertEquals(1, resolved.size)
        assertEquals(1, resolved.single().rects.size)
        assertEquals(PdfPointRect(10f, 20f, 60f, 35f), resolved.single().rects.single())
    }

    @Test
    fun `OCR highlight can span multiple recognized elements`() {
        val page = PdfPageTextResult(
            text = "Martin Luther",
            source = PdfPageTextSource.OCR,
            spans = listOf(
                PdfOcrTextSpan(0, 6, PdfPointRect(5f, 5f, 45f, 20f)),
                PdfOcrTextSpan(7, 13, PdfPointRect(50f, 5f, 90f, 20f)),
            ),
        )

        val resolved = page.resolveOcrHighlights(
            listOf(
                PdfTextHighlightRequest(
                    axisId = "luther",
                    startOffset = 0,
                    endOffsetExclusive = 13,
                    colorArgb = 0xFF1E88E5L,
                ),
            ),
        )

        assertEquals(2, resolved.single().rects.size)
    }

    @Test
    fun `native pages do not use OCR rectangle mapping`() {
        val page = PdfPageTextResult(
            text = "cauze",
            source = PdfPageTextSource.NATIVE,
            spans = listOf(PdfOcrTextSpan(0, 5, PdfPointRect(0f, 0f, 10f, 10f))),
        )

        assertTrue(
            page.resolveOcrHighlights(
                listOf(PdfTextHighlightRequest("axis", 0, 5, 0xFF43A047L)),
            ).isEmpty(),
        )
    }
}
