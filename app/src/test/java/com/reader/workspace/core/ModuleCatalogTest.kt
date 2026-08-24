package com.reader.workspace.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleCatalogTest {
    @Test
    fun moduleIdsAreUnique() {
        val ids = ModuleCatalog.modules.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun foundationContainsAllMajorWorkspaces() {
        val ids = ModuleCatalog.modules.map { it.id }.toSet()
        assertTrue(
            ids.containsAll(
                setOf(
                    "documents",
                    "office",
                    "pdf",
                    "research",
                    "marginalia",
                    "converter",
                    "language",
                    "index",
                    "memory-palace",
                ),
            ),
        )
    }
}
