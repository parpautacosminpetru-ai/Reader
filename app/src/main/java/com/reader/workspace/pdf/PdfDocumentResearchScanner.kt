package com.reader.workspace.pdf

import com.reader.workspace.research.LexicalAxis
import com.reader.workspace.research.LexicalSearchEngine
import com.reader.workspace.research.ProximityRule
import com.reader.workspace.research.ProximityScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class PdfResearchTextState {
    AVAILABLE,
    OCR,
    EMPTY,
    UNSUPPORTED,
}

data class PdfResearchPageScan(
    val pageIndex: Int,
    val textState: PdfResearchTextState,
    val characterCount: Int,
    val hitCount: Int,
    val intersectionCount: Int,
    val axisHitCounts: Map<String, Int>,
) {
    val hasHits: Boolean
        get() = hitCount > 0
}

data class PdfResearchScanResult(
    val startPageIndex: Int,
    val endPageIndex: Int,
    val pages: List<PdfResearchPageScan>,
) {
    val pageCount: Int
        get() = pages.size

    val totalHits: Int
        get() = pages.sumOf(PdfResearchPageScan::hitCount)

    val totalIntersections: Int
        get() = pages.sumOf(PdfResearchPageScan::intersectionCount)

    val pagesWithHits: Int
        get() = pages.count(PdfResearchPageScan::hasHits)

    val nativeTextPages: Int
        get() = pages.count { it.textState == PdfResearchTextState.AVAILABLE }

    val ocrPages: Int
        get() = pages.count { it.textState == PdfResearchTextState.OCR }

    val emptyTextPages: Int
        get() = pages.count { it.textState == PdfResearchTextState.EMPTY }

    val unsupportedTextPages: Int
        get() = pages.count { it.textState == PdfResearchTextState.UNSUPPORTED }

    val maxPageHits: Int
        get() = pages.maxOfOrNull(PdfResearchPageScan::hitCount) ?: 0

    fun densityFraction(page: PdfResearchPageScan): Float =
        if (maxPageHits <= 0) 0f else page.hitCount.toFloat() / maxPageHits
}

object PdfResearchPageRange {
    /** Resolves user-facing 1-based page numbers to a safe 0-based inclusive range. */
    fun resolve(
        pageCount: Int,
        requestedStartPage: Int,
        requestedEndPage: Int,
    ): IntRange {
        if (pageCount <= 0) return 1..0

        val start = requestedStartPage.coerceIn(1, pageCount)
        val end = requestedEndPage.coerceIn(1, pageCount)
        val first = minOf(start, end) - 1
        val last = maxOf(start, end) - 1
        return first..last
    }
}

class PdfDocumentResearchScanner(
    private val session: PdfRendererSession,
    private val textResolver: PdfPageTextResolver,
) {
    suspend fun scan(
        axes: List<LexicalAxis>,
        pageRange: IntRange,
        proximityChars: Int,
        proximityScope: ProximityScope = ProximityScope.CHARACTERS,
        onProgress: (completedPages: Int, totalPages: Int) -> Unit = { _, _ -> },
    ): PdfResearchScanResult {
        val enabledAxes = axes.filter(LexicalAxis::enabled)
        val safeRange = pageRange
            .filter { it in 0 until session.pageCount }
            .let { indices ->
                if (indices.isEmpty()) emptyList() else indices
            }
        val total = safeRange.size
        val results = ArrayList<PdfResearchPageScan>(total)

        safeRange.forEachIndexed { position, pageIndex ->
            currentCoroutineContext().ensureActive()
            val pageResult = scanPage(
                pageIndex = pageIndex,
                axes = enabledAxes,
                proximityChars = proximityChars.coerceAtLeast(0),
                proximityScope = proximityScope,
            )
            results += pageResult
            onProgress(position + 1, total)
        }

        val first = results.firstOrNull()?.pageIndex ?: 0
        val last = results.lastOrNull()?.pageIndex ?: first
        return PdfResearchScanResult(
            startPageIndex = first,
            endPageIndex = last,
            pages = results,
        )
    }

    private suspend fun scanPage(
        pageIndex: Int,
        axes: List<LexicalAxis>,
        proximityChars: Int,
        proximityScope: ProximityScope,
    ): PdfResearchPageScan {
        val resolved = textResolver.resolve(pageIndex)
        val text = resolved.text
        if (text.isBlank()) {
            return PdfResearchPageScan(
                pageIndex = pageIndex,
                textState = PdfResearchTextState.EMPTY,
                characterCount = text.length,
                hitCount = 0,
                intersectionCount = 0,
                axisHitCounts = emptyMap(),
            )
        }

        val hits = LexicalSearchEngine.search(text, axes)
        val activeAxisIds = axes.map(LexicalAxis::id).toSet()
        val intersections = if (activeAxisIds.size < 2) {
            emptyList()
        } else {
            LexicalSearchEngine.findProximityMatches(
                hits = hits,
                rules = listOf(
                    ProximityRule(
                        id = "document-scan",
                        requiredAxisIds = activeAxisIds,
                        maxSpanChars = proximityChars,
                        scope = proximityScope,
                    ),
                ),
                text = text,
            )
        }

        val textState = when (resolved.source) {
            PdfPageTextSource.NATIVE -> PdfResearchTextState.AVAILABLE
            PdfPageTextSource.OCR -> PdfResearchTextState.OCR
            PdfPageTextSource.NONE -> PdfResearchTextState.EMPTY
        }

        return PdfResearchPageScan(
            pageIndex = pageIndex,
            textState = textState,
            characterCount = text.length,
            hitCount = hits.size,
            intersectionCount = intersections.size,
            axisHitCounts = hits.groupingBy { it.axisId }.eachCount(),
        )
    }
}
