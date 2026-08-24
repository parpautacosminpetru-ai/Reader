package com.reader.workspace.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfDocumentResearchScannerTest {
    @Test
    fun `page range clamps and accepts reversed input`() {
        assertEquals(0..9, PdfResearchPageRange.resolve(10, 1, 10))
        assertEquals(2..7, PdfResearchPageRange.resolve(10, 8, 3))
        assertEquals(0..9, PdfResearchPageRange.resolve(10, -40, 99))
        assertTrue(PdfResearchPageRange.resolve(0, 1, 1).isEmpty())
    }

    @Test
    fun `scan summary exposes page density without divide by zero`() {
        val low = PdfResearchPageScan(
            pageIndex = 0,
            textState = PdfResearchTextState.AVAILABLE,
            characterCount = 100,
            hitCount = 2,
            intersectionCount = 0,
            axisHitCounts = mapOf("a" to 2),
        )
        val high = low.copy(pageIndex = 1, hitCount = 8, intersectionCount = 3)
        val empty = low.copy(
            pageIndex = 2,
            textState = PdfResearchTextState.EMPTY,
            characterCount = 0,
            hitCount = 0,
            axisHitCounts = emptyMap(),
        )
        val result = PdfResearchScanResult(
            startPageIndex = 0,
            endPageIndex = 2,
            pages = listOf(low, high, empty),
        )

        assertEquals(10, result.totalHits)
        assertEquals(3, result.totalIntersections)
        assertEquals(2, result.pagesWithHits)
        assertEquals(1, result.emptyTextPages)
        assertEquals(8, result.maxPageHits)
        assertEquals(0.25f, result.densityFraction(low), 0.0001f)
        assertEquals(1f, result.densityFraction(high), 0.0001f)
    }
}
