package com.reader.workspace.index

import com.reader.workspace.marginalia.DocumentAnchor
import com.reader.workspace.marginalia.MarginaliaItem
import com.reader.workspace.marginalia.MarginaliaItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalIndexTest {
    @Test
    fun `manual entry round trips and normalizes safely`() {
        val entry = IndexEntry(
            id = "entry-1",
            title = "  Luther  ",
            category = " Person ",
            documentId = "doc-1",
            pageIndex = -3,
            startOffset = -2,
            endOffsetExclusive = 1,
            note = "  reformator  ",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 2L,
        ).normalized()

        assertEquals("Luther", entry.title)
        assertEquals("Person", entry.category)
        assertEquals(0, entry.pageIndex)
        assertEquals(0, entry.startOffset)
        assertEquals(1, entry.endOffsetExclusive)
        assertEquals("reformator", entry.note)
        assertEquals(entry, entry.toEntity().toModel())
    }

    @Test
    fun `composer merges manual research and marginalia alphabetically`() {
        val manual = IndexEntry(
            id = "m1",
            title = "Calvin",
            category = "Person",
            documentId = "doc",
            pageIndex = 4,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )
        val research = MarginaliaItem(
            id = "r1",
            anchor = DocumentAnchor("doc", 2, 10, 20),
            kind = MarginaliaItemKind.RESEARCH_LINK,
            xFraction = 0f,
            yFraction = 0f,
            widthFraction = 0.3f,
            heightFraction = 0.1f,
            text = "Luther și reforma",
        )
        val note = MarginaliaItem(
            id = "n1",
            anchor = DocumentAnchor("doc", 8),
            kind = MarginaliaItemKind.TEXT,
            xFraction = 0f,
            yFraction = 0f,
            widthFraction = 0.3f,
            heightFraction = 0.1f,
            text = "Augsburg",
        )

        val items = UniversalIndexComposer.compose(listOf(manual), listOf(research, note))

        assertEquals(listOf("Augsburg", "Calvin", "Luther și reforma"), items.map { it.title })
        assertEquals(UniversalIndexSource.RESEARCH, items.last().source)
        assertEquals(UniversalIndexSource.MARGINALIA, items.first().source)
    }

    @Test
    fun `filter searches category title and note deterministically`() {
        val items = listOf(
            UniversalIndexItem("1", "Roma", "Place", "d", 0, null, null, "Italia", UniversalIndexSource.MANUAL),
            UniversalIndexItem("2", "Calvin", "Person", "d", 1, null, null, "Geneva", UniversalIndexSource.MANUAL),
        )

        assertEquals(listOf("Roma"), UniversalIndexComposer.filter(items, "italia", null).map { it.title })
        assertEquals(listOf("Calvin"), UniversalIndexComposer.filter(items, "", "person").map { it.title })
        assertTrue(UniversalIndexComposer.filter(items, "missing", null).isEmpty())
    }

    @Test
    fun `alphabet bucket handles letters and symbols`() {
        assertEquals("Ș", UniversalIndexComposer.alphabetBucket("școală"))
        assertEquals("#", UniversalIndexComposer.alphabetBucket("1517"))
        assertEquals("#", UniversalIndexComposer.alphabetBucket("   "))
    }
}
