package com.reader.workspace.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageSizingTest {
    @Test
    fun fitWidthPreservesAspectRatio() {
        val size = PdfPageSizing.fitWidth(
            sourceWidth = 600,
            sourceHeight = 900,
            targetWidth = 1200,
        )

        assertEquals(1200, size.width)
        assertEquals(1800, size.height)
    }

    @Test(expected = IllegalArgumentException::class)
    fun fitWidthRejectsInvalidTargetWidth() {
        PdfPageSizing.fitWidth(600, 900, 0)
    }

    @Test
    fun nativeTextExtractionStartsAtApi35() {
        assertFalse(PdfTextSupport.isNativeTextExtractionAvailable(34))
        assertTrue(PdfTextSupport.isNativeTextExtractionAvailable(35))
        assertTrue(PdfTextSupport.isNativeTextExtractionAvailable(36))
    }

    @Test
    fun visualOverlayStartsAtApi35() {
        assertFalse(PdfResearchOverlay.isAvailable(34))
        assertTrue(PdfResearchOverlay.isAvailable(35))
        assertTrue(PdfResearchOverlay.isAvailable(36))
    }

    @Test
    fun overlayRectUsesSamePageScaleAsRenderer() {
        val scaled = PdfResearchOverlay.scaleRect(
            PdfPointRect(left = 10f, top = 20f, right = 110f, bottom = 50f),
            scale = 2f,
        )

        assertEquals(20f, scaled.left)
        assertEquals(40f, scaled.top)
        assertEquals(220f, scaled.right)
        assertEquals(100f, scaled.bottom)
    }
}
