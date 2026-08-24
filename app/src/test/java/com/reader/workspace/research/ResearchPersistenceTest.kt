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
            matchMode = LexicalMatchMode.CONTAINS,
            caseSensitive = true,
            diacriticsSensitive = false,
            suffixMatch = true,
            enabled = true,
            colorArgb = 0xFF1E88E5L,
            marker = "⚑",
            createdAtEpochMillis = 10L,
            updatedAtEpochMillis = 20L,
        )

        assertEquals(model, model.toEntity().toModel())
        assertEquals(model.id, model.toLexicalAxis().id)
        assertEquals(model.patterns, model.toLexicalAxis().patterns)
        assertEquals(model.diacriticsSensitive, model.toLexicalAxis().diacriticsSensitive)
        assertEquals(model.suffixMatch, model.toLexicalAxis().suffixMatch)
    }

    @Test
    fun `profile preserves ordered axis combination and sentence scope`() {
        val profile = ResearchProfile(
            id = "profile-1",
            title = "Reformation causes",
            axisIds = listOf("causes", "reformation", "luther"),
            proximityChars = 450,
            proximityScope = ProximityScope.SENTENCE,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 2L,
        )

        assertEquals(profile, profile.toEntity().toModel())
    }

    @Test
    fun `history preserves document range and paragraph rule scope`() {
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
            proximityScope = ProximityScope.PARAGRAPH,
        )

        assertEquals(entry, entry.toEntity().toModel())
        assertEquals("pages 10–25", entry.scopeLabel())
        assertEquals("same paragraph", entry.ruleLabel())
    }

    @Test
    fun `document history gets human readable labels`() {
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
            proximityScope = ProximityScope.PAGE,
        )

        assertEquals("whole document", entry.scopeLabel())
        assertEquals("same page", entry.ruleLabel())
    }
}
