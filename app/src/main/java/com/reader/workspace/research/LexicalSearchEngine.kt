package com.reader.workspace.research

enum class LexicalMatchMode {
    EXACT,
    PREFIX,
    CONTAINS,
}

data class LexicalAxis(
    val id: String,
    val title: String,
    val patterns: List<String>,
    val matchMode: LexicalMatchMode = LexicalMatchMode.PREFIX,
    val caseSensitive: Boolean = false,
    val enabled: Boolean = true,
)

data class LexicalHit(
    val axisId: String,
    val axisTitle: String,
    val pattern: String,
    val matchedText: String,
    val startOffset: Int,
    val endOffsetExclusive: Int,
)

data class ProximityRule(
    val id: String,
    val requiredAxisIds: Set<String>,
    val maxSpanChars: Int,
)

data class ProximityMatch(
    val ruleId: String,
    val startOffset: Int,
    val endOffsetExclusive: Int,
    val hits: List<LexicalHit>,
)

object LexicalSearchEngine {
    fun search(text: String, axes: List<LexicalAxis>): List<LexicalHit> {
        if (text.isEmpty()) return emptyList()

        return buildList {
            axes.asSequence()
                .filter { it.enabled }
                .forEach { axis ->
                    axis.patterns.asSequence()
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .distinct()
                        .forEach { pattern ->
                            findPattern(text, axis, pattern).forEach(::add)
                        }
                }
        }.sortedWith(compareBy(LexicalHit::startOffset, LexicalHit::endOffsetExclusive, LexicalHit::axisId))
    }

    fun findProximityMatches(
        hits: List<LexicalHit>,
        rules: List<ProximityRule>,
    ): List<ProximityMatch> {
        if (hits.isEmpty()) return emptyList()
        val orderedHits = hits.sortedBy(LexicalHit::startOffset)

        return rules.flatMap { rule ->
            if (rule.requiredAxisIds.isEmpty() || rule.maxSpanChars < 0) {
                emptyList()
            } else {
                minimalWindowsForRule(orderedHits, rule)
            }
        }.sortedBy(ProximityMatch::startOffset)
    }

    private fun findPattern(
        text: String,
        axis: LexicalAxis,
        pattern: String,
    ): List<LexicalHit> {
        val result = mutableListOf<LexicalHit>()
        val lastStart = text.length - pattern.length
        if (lastStart < 0) return result

        for (start in 0..lastStart) {
            if (!text.regionMatches(
                    thisOffset = start,
                    other = pattern,
                    otherOffset = 0,
                    length = pattern.length,
                    ignoreCase = !axis.caseSensitive,
                )
            ) {
                continue
            }

            val end = start + pattern.length
            if (!matchesMode(text, pattern, start, end, axis.matchMode)) continue

            result += LexicalHit(
                axisId = axis.id,
                axisTitle = axis.title,
                pattern = pattern,
                matchedText = text.substring(start, end),
                startOffset = start,
                endOffsetExclusive = end,
            )
        }
        return result
    }

    private fun matchesMode(
        text: String,
        pattern: String,
        start: Int,
        end: Int,
        mode: LexicalMatchMode,
    ): Boolean = when (mode) {
        LexicalMatchMode.CONTAINS -> true
        LexicalMatchMode.PREFIX -> {
            val needsLeftBoundary = pattern.firstOrNull()?.isLetterOrDigit() == true
            !needsLeftBoundary || isBoundaryBefore(text, start)
        }
        LexicalMatchMode.EXACT -> {
            val needsLeftBoundary = pattern.firstOrNull()?.isLetterOrDigit() == true
            val needsRightBoundary = pattern.lastOrNull()?.isLetterOrDigit() == true
            (!needsLeftBoundary || isBoundaryBefore(text, start)) &&
                (!needsRightBoundary || isBoundaryAfter(text, end))
        }
    }

    private fun isBoundaryBefore(text: String, offset: Int): Boolean =
        offset == 0 || !text[offset - 1].isLetterOrDigit()

    private fun isBoundaryAfter(text: String, offset: Int): Boolean =
        offset == text.length || !text[offset].isLetterOrDigit()

    private fun minimalWindowsForRule(
        hits: List<LexicalHit>,
        rule: ProximityRule,
    ): List<ProximityMatch> {
        val candidates = hits.filter { it.axisId in rule.requiredAxisIds }
        if (candidates.map(LexicalHit::axisId).toSet().containsAll(rule.requiredAxisIds).not()) {
            return emptyList()
        }

        val matches = mutableListOf<ProximityMatch>()
        var left = 0
        val counts = mutableMapOf<String, Int>()

        for (right in candidates.indices) {
            val rightHit = candidates[right]
            counts[rightHit.axisId] = (counts[rightHit.axisId] ?: 0) + 1

            while (left <= right && counts.keys.containsAll(rule.requiredAxisIds)) {
                val windowHits = candidates.subList(left, right + 1)
                val start = windowHits.minOf(LexicalHit::startOffset)
                val end = windowHits.maxOf(LexicalHit::endOffsetExclusive)
                if (end - start <= rule.maxSpanChars) {
                    matches += ProximityMatch(
                        ruleId = rule.id,
                        startOffset = start,
                        endOffsetExclusive = end,
                        hits = windowHits.toList(),
                    )
                }

                val leftHit = candidates[left]
                val nextCount = (counts[leftHit.axisId] ?: 1) - 1
                if (nextCount <= 0) counts.remove(leftHit.axisId) else counts[leftHit.axisId] = nextCount
                left += 1
            }
        }

        return matches.distinctBy { match ->
            Triple(match.ruleId, match.startOffset, match.endOffsetExclusive)
        }
    }
}
