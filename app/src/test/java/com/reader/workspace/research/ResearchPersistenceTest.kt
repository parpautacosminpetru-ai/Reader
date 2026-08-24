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
}
