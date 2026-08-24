package com.reader.workspace.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchPersistenceTest {
    @Test
    fun `codec round trips arbitrary lexical forms`() {
        val source = listOf(
            "cauz",
            "motiv, factor",
            "linie\nnoua",
            "șir:cu:două-puncte",
        )

        assertEquals(source, ResearchCodec.decode(ResearchCodec.encode(source)))
    }

    @Test
    fun `codec rejects corrupted payload safely`() {
        assertTrue(ResearchCodec.decode("12:prea-scurt").isEmpty())
        assertTrue(ResearchCodec.decode("x:abc").isEmpty())
    }

    @Test
    fun `axis entity preserves reusable lexical configuration`() {
        val model = ResearchAxisDefinition(
            id = "axis-1",
            title = "Cauze",
            patterns = listOf("cauz", "motiv", "factor"),
            matchMode = LexicalMatchMode.PREFIX,
            caseSensitive = false,
            enabled = true,
            colorArgb = 0xFF1E88E5L,
            marker = "⚑",
            createdAtEpochMillis = 10L,
            updatedAtEpochMillis = 20L,
        )

        assertEquals(model, model.toEntity().toModel())
        assertEquals(model.id, model.toLexicalAxis().id)
        assertEquals(model.patterns, model.toLexicalAxis().patterns)
    }

    @Test
    fun `profile preserves ordered axis combination`() {
        val profile = ResearchProfile(
            id = "profile-1",
            title = "Reformation causes",
            axisIds = listOf("causes", "reformation", "luther"),
            proximityChars = 450,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 2L,
        )

        assertEquals(profile, profile.toEntity().toModel())
    }

    @Test
    fun `history preserves document range scope`() {
        val entry = ResearchHistoryEntry(
            id = "history-1",
            documentId = "doc-1",
            pageIndex = 9,
            profileId = "profile-1",
            axisIds = listOf("causes", "reformation"),
            proximityChars = 300,
            hitCount = 42,
            intersectionCount = 7,
            executedAtEpochMillis = 99L,
            scope = ResearchHistoryScope.RANGE,
            rangeStartPageIndex = 9,
            rangeEndPageIndex = 24,
        )

        assertEquals(entry, entry.toEntity().toModel())
        assertEquals("pages 10–25", entry.scopeLabel())
    }

    @Test
    fun `document history gets human readable label`() {
        val entry = ResearchHistoryEntry(
            id = "history-2",
            documentId = "doc-1",
            pageIndex = 0,
            profileId = null,
            axisIds = listOf("a"),
            proximityChars = 0,
            hitCount = 1,
            intersectionCount = 0,
            executedAtEpochMillis = 100L,
            scope = ResearchHistoryScope.DOCUMENT,
            rangeStartPageIndex = 0,
            rangeEndPageIndex = 99,
        )

        assertEquals("whole document", entry.scopeLabel())
    }
}
