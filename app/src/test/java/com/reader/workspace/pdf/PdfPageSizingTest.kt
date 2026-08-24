package com.reader.workspace.pdf

import org.junit.Assert.assertEquals
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
}
