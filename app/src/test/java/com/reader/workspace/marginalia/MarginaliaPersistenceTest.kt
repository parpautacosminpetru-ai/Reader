package com.reader.workspace.marginalia

import org.junit.Assert.assertEquals
import org.junit.Test

class MarginaliaPersistenceTest {
    @Test
    fun itemRoundTripPreservesAnchorAndPayload() {
        val source = MarginaliaItem(
            id = "marker-1",
            anchor = DocumentAnchor(
                documentId = "document-1",
                pageIndex = 7,
                startOffset = 120,
                endOffsetExclusive = 160,
            ),
            kind = MarginaliaItemKind.TEXT,
            xFraction = 0.1f,
            yFraction = 0.2f,
            widthFraction = 0.6f,
            heightFraction = 0.15f,
            zIndex = 3,
            text = "persistent note",
            linkedDocumentId = "document-2",
            linkedPageIndex = 4,
        )

        val restored = source.toEntity(createdAtEpochMillis = 123L).toModel()

        assertEquals(source, restored)
    }

    @Test
    fun storedWidthIsAlwaysClamped() {
        assertEquals(
            MarginaliaGeometry.MIN_WIDTH_FRACTION,
            MarginaliaGeometry.clampWidthFraction(-1f),
            0f,
        )
        assertEquals(
            MarginaliaGeometry.MAX_WIDTH_FRACTION,
            MarginaliaGeometry.clampWidthFraction(2f),
            0f,
        )
    }
}
