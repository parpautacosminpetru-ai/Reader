package com.reader.workspace.marginalia

data class DocumentAnchor(
    val documentId: String,
    val pageIndex: Int,
    val startOffset: Int? = null,
    val endOffsetExclusive: Int? = null,
)

enum class MarginaliaItemKind {
    INK,
    TEXT,
    IMAGE,
    EMOJI,
    MODEL_3D,
    SHAPE,
}

data class MarginaliaItem(
    val id: String,
    val anchor: DocumentAnchor,
    val kind: MarginaliaItemKind,
    val xFraction: Float,
    val yFraction: Float,
    val widthFraction: Float,
    val heightFraction: Float,
    val zIndex: Int = 0,
    val text: String? = null,
    val assetId: String? = null,
    val linkedDocumentId: String? = null,
    val linkedPageIndex: Int? = null,
)

data class AnchorVerticalRange(
    val topFraction: Float,
    val bottomFraction: Float,
)

object MarginaliaGeometry {
    const val DEFAULT_WIDTH_FRACTION: Float = 0.28f
    const val MIN_WIDTH_FRACTION: Float = 0.10f
    const val MAX_WIDTH_FRACTION: Float = 0.80f

    fun clampWidthFraction(requested: Float): Float =
        requested.coerceIn(MIN_WIDTH_FRACTION, MAX_WIDTH_FRACTION)

    fun widthPx(containerWidthPx: Int, requestedFraction: Float): Int {
        if (containerWidthPx <= 0) return 0
        return (containerWidthPx * clampWidthFraction(requestedFraction)).toInt()
    }

    fun anchorVerticalRange(
        anchor: DocumentAnchor,
        pageCharacterCount: Int,
    ): AnchorVerticalRange {
        if (pageCharacterCount <= 0 || anchor.startOffset == null) {
            return AnchorVerticalRange(0f, 1f)
        }

        val start = anchor.startOffset.coerceIn(0, pageCharacterCount)
        val rawEnd = anchor.endOffsetExclusive ?: start
        val end = rawEnd.coerceIn(start, pageCharacterCount)

        return AnchorVerticalRange(
            topFraction = start.toFloat() / pageCharacterCount,
            bottomFraction = end.toFloat() / pageCharacterCount,
        )
    }

    fun normalize(item: MarginaliaItem): MarginaliaItem {
        val width = item.widthFraction.coerceIn(0f, 1f)
        val height = item.heightFraction.coerceIn(0f, 1f)
        val x = item.xFraction.coerceIn(0f, 1f - width)
        val y = item.yFraction.coerceIn(0f, 1f - height)
        return item.copy(
            xFraction = x,
            yFraction = y,
            widthFraction = width,
            heightFraction = height,
        )
    }
}
