package com.reader.workspace.research

import com.reader.workspace.marginalia.DocumentAnchor
import com.reader.workspace.marginalia.MarginaliaItem
import com.reader.workspace.marginalia.MarginaliaItemKind

object ResearchMarginaliaBridge {
    private const val CARD_HEIGHT_FRACTION = 0.14f
    private const val CARD_WIDTH_FRACTION = 0.92f

    fun fromHit(
        id: String,
        documentId: String,
        pageIndex: Int,
        pageText: String,
        hit: LexicalHit,
    ): MarginaliaItem = buildItem(
        id = id,
        documentId = documentId,
        pageIndex = pageIndex,
        pageText = pageText,
        startOffset = hit.startOffset,
        endOffsetExclusive = hit.endOffsetExclusive,
        title = "Research hit · ${hit.axisTitle}",
    )

    fun fromIntersection(
        id: String,
        documentId: String,
        pageIndex: Int,
        pageText: String,
        match: ProximityMatch,
    ): MarginaliaItem = buildItem(
        id = id,
        documentId = documentId,
        pageIndex = pageIndex,
        pageText = pageText,
        startOffset = match.startOffset,
        endOffsetExclusive = match.endOffsetExclusive,
        title = "Research intersection · ${match.hits.map { it.axisTitle }.distinct().joinToString(" + ")}",
    )

    fun sourceSnippet(
        text: String,
        startOffset: Int,
        endOffsetExclusive: Int,
        contextChars: Int = 48,
    ): String {
        if (text.isEmpty()) return ""
        val start = startOffset.coerceIn(0, text.length)
        val end = endOffsetExclusive.coerceIn(start, text.length)
        val snippetStart = (start - contextChars.coerceAtLeast(0)).coerceAtLeast(0)
        val snippetEnd = (end + contextChars.coerceAtLeast(0)).coerceAtMost(text.length)
        val body = text.substring(snippetStart, snippetEnd)
            .replace(Regex("\\s+"), " ")
            .trim()
        return buildString {
            if (snippetStart > 0) append("…")
            append(body)
            if (snippetEnd < text.length) append("…")
        }
    }

    private fun buildItem(
        id: String,
        documentId: String,
        pageIndex: Int,
        pageText: String,
        startOffset: Int,
        endOffsetExclusive: Int,
        title: String,
    ): MarginaliaItem {
        val safeStart = startOffset.coerceIn(0, pageText.length)
        val safeEnd = endOffsetExclusive.coerceIn(safeStart, pageText.length)
        val y = if (pageText.isEmpty()) {
            0.05f
        } else {
            (safeStart.toFloat() / pageText.length).coerceIn(0f, 1f - CARD_HEIGHT_FRACTION)
        }
        val snippet = sourceSnippet(pageText, safeStart, safeEnd)
        return MarginaliaItem(
            id = id,
            anchor = DocumentAnchor(
                documentId = documentId,
                pageIndex = pageIndex,
                startOffset = safeStart,
                endOffsetExclusive = safeEnd,
            ),
            kind = MarginaliaItemKind.RESEARCH_LINK,
            xFraction = 0.04f,
            yFraction = y,
            widthFraction = CARD_WIDTH_FRACTION,
            heightFraction = CARD_HEIGHT_FRACTION,
            text = if (snippet.isEmpty()) title else "$title\n$snippet",
            linkedDocumentId = documentId,
            linkedPageIndex = pageIndex,
        )
    }
}
