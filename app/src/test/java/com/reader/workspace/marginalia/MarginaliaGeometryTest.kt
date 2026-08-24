package com.reader.workspace.marginalia

import org.junit.Assert.assertEquals
import org.junit.Test

class MarginaliaGeometryTest {
    @Test
    fun widthIsUserControlledWithinSafeBounds() {
        assertEquals(0.10f, MarginaliaGeometry.clampWidthFraction(0.02f))
        assertEquals(0.45f, MarginaliaGeometry.clampWidthFraction(0.45f))
        assertEquals(0.80f, MarginaliaGeometry.clampWidthFraction(0.95f))
        assertEquals(360, MarginaliaGeometry.widthPx(1200, 0.30f))
    }

    @Test
    fun textAnchorMapsToSameVerticalRegionOfMargin() {
        val anchor = DocumentAnchor(
            documentId = "doc-1",
            pageIndex = 4,
            startOffset = 250,
            endOffsetExclusive = 400,
        )

        val range = MarginaliaGeometry.anchorVerticalRange(anchor, pageCharacterCount = 1000)

        assertEquals(0.25f, range.topFraction)
        assertEquals(0.40f, range.bottomFraction)
    }

    @Test
    fun pageAnchorWithoutTextRangeUsesWholePage() {
        val anchor = DocumentAnchor(documentId = "doc-1", pageIndex = 2)

        val range = MarginaliaGeometry.anchorVerticalRange(anchor, pageCharacterCount = 800)

        assertEquals(0f, range.topFraction)
        assertEquals(1f, range.bottomFraction)
    }

    @Test
    fun placedAssetsStayInsideCanvas() {
        val item = MarginaliaItem(
            id = "asset-1",
            anchor = DocumentAnchor("doc", 0),
            kind = MarginaliaItemKind.MODEL_3D,
            xFraction = 0.95f,
            yFraction = -0.2f,
            widthFraction = 0.20f,
            heightFraction = 0.30f,
            assetId = "pig-model",
        )

        val normalized = MarginaliaGeometry.normalize(item)

        assertEquals(0.80f, normalized.xFraction)
        assertEquals(0f, normalized.yFraction)
        assertEquals(0.20f, normalized.widthFraction)
        assertEquals(0.30f, normalized.heightFraction)
    }
}
