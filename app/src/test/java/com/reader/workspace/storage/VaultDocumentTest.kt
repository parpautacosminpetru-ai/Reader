package com.reader.workspace.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultDocumentTest {
    @Test
    fun `extension is normalized for local storage`() {
        assertEquals("pdf", VaultFileNames.extensionFrom("Research.PDF"))
        assertEquals("docx", VaultFileNames.extensionFrom("notes.DOCX"))
        assertEquals("", VaultFileNames.extensionFrom("README"))
        assertEquals("", VaultFileNames.extensionFrom(".hidden"))
    }

    @Test
    fun `stored file name uses stable id instead of source name`() {
        assertEquals("abc-123.pdf", VaultFileNames.storedFileName("abc-123", "My Book.PDF"))
        assertEquals("abc-123", VaultFileNames.storedFileName("abc-123", "README"))
    }

    @Test
    fun `file sizes are readable`() {
        assertEquals("0 B", VaultDisplay.formatSize(0))
        assertEquals("1 KB", VaultDisplay.formatSize(1_024))
        assertEquals("1.5 KB", VaultDisplay.formatSize(1_536))
        assertEquals("1 MB", VaultDisplay.formatSize(1_048_576))
    }

    @Test
    fun `pdf documents route to the local reader`() {
        assertTrue(
            VaultDisplay.isPdf(
                VaultDocument("1", "book.PDF", "1.pdf", "application/octet-stream", 1, 0),
            ),
        )
        assertTrue(
            VaultDisplay.isPdf(
                VaultDocument("2", "book", "2", "application/pdf", 1, 0),
            ),
        )
        assertFalse(
            VaultDisplay.isPdf(
                VaultDocument("3", "notes.docx", "3.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 1, 0),
            ),
        )
    }
}
