package com.reader.workspace.research

import com.reader.workspace.marginalia.MarginaliaItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchMarginaliaBridgeTest {
    @Test
    fun hitBecomesPersistentSourceAnchorWithExactOffsets() {
        val text = "Cauzele reformei sunt analizate aici."
        val hit = LexicalHit(
            axisId = "cause",
            axisTitle = "Cauze",
            pattern = "cauz",
            matchedText = "Cauz",
            startOffset = 0,
            endOffsetExclusive = 4,
        )

        val item = ResearchMarginaliaBridge.fromHit(
            id = "anchor-1",
            documentId = "doc-1",
            pageIndex = 7,
            pageText = text,
            hit = hit,
        )

        assertEquals(MarginaliaItemKind.RESEARCH_LINK, item.kind)
        assertEquals("doc-1", item.anchor.documentId)
        assertEquals(7, item.anchor.pageIndex)
        assertEquals(0, item.anchor.startOffset)
        assertEquals(4, item.anchor.endOffsetExclusive)
        assertEquals("doc-1", item.linkedDocumentId)
        assertEquals(7, item.linkedPageIndex)
        assertTrue(item.text.orEmpty().contains("Cauze"))
    }

    @Test
    fun intersectionStoresWholeDeterministicWindow() {
        val text = "Motivul și reforma apar în aceeași propoziție."
        val match = ProximityMatch(
            ruleId = "sentence",
            startOffset = 0,
            endOffsetExclusive = 18,
            hits = listOf(
                LexicalHit("cause", "Cauze", "motiv", "Motiv", 0, 5),
                LexicalHit("reform", "Reformă", "reform", "reform", 11, 17),
            ),
        )

        val item = ResearchMarginaliaBridge.fromIntersection(
            id = "anchor-2",
            documentId = "doc-2",
            pageIndex = 3,
            pageText = text,
            match = match,
        )

        assertEquals(0, item.anchor.startOffset)
        assertEquals(18, item.anchor.endOffsetExclusive)
        assertTrue(item.text.orEmpty().contains("Cauze + Reformă"))
    }

    @Test
    fun snippetsCollapseWhitespaceAndKeepContextMarkers() {
        val text = "0123456789   cauza\n reformei   abcdefghij"
        val snippet = ResearchMarginaliaBridge.sourceSnippet(
            text = text,
            startOffset = 13,
            endOffsetExclusive = 26,
            contextChars = 4,
        )

        assertTrue(snippet.startsWith("…"))
        assertTrue(snippet.endsWith("…"))
        assertTrue(!snippet.contains("\n"))
        assertTrue(!snippet.contains("  "))
    }
}
