package com.reader.workspace.core

data class ReaderModule(
    val id: String,
    val title: String,
    val summary: String,
    val status: String = "Planned",
)

object ModuleCatalog {
    val modules: List<ReaderModule> = listOf(
        ReaderModule(
            id = "documents",
            title = "Documents",
            summary = "App-owned offline vault with multi-file import and persistent document metadata.",
            status = "Active · v0.2",
        ),
        ReaderModule(
            id = "office",
            title = "Office",
            summary = "Documents, spreadsheets, presentations and handwriting/ink workflows.",
        ),
        ReaderModule(
            id = "pdf",
            title = "PDF Studio",
            summary = "Reader, editor, annotations, forms, signatures and professional PDF tools.",
        ),
        ReaderModule(
            id = "research",
            title = "Lexical Research",
            summary = "Parallel lexical axes, colored overlays, range/whole-document cartography, proximity rules and persistent profiles/history.",
            status = "Active · v0.7",
        ),
        ReaderModule(
            id = "marginalia",
            title = "Marginalia",
            summary = "A synchronized, resizable canvas attached to source content without altering it.",
        ),
        ReaderModule(
            id = "converter",
            title = "Converter",
            summary = "Interoperable offline conversion between supported document and media formats.",
        ),
        ReaderModule(
            id = "language",
            title = "Language & Voice",
            summary = "Offline dictation, read-aloud, translation and installable language/voice packs.",
        ),
        ReaderModule(
            id = "index",
            title = "Index",
            summary = "Practical indexes for documents, collections and memory palaces.",
        ),
        ReaderModule(
            id = "memory-palace",
            title = "Memory Palace",
            summary = "2D/3D spatial editor with reusable assets, anchors and later optional physics.",
        ),
    )
}
