package com.reader.workspace.research

import java.text.Normalizer

enum class LexicalMatchMode {
    EXACT,
    PREFIX,
    CONTAINS,
}

enum class ProximityScope {
    CHARACTERS,
    SENTENCE,
    PARAGRAPH,
    PAGE,
}

data class LexicalAxis(
    val id: String,
    val title: String,
    val patterns: List<String>,
    val matchMode: LexicalMatchMode = LexicalMatchMode.PREFIX,
    val caseSensitive: Boolean = false,
    val diacriticsSensitive: Boolean = true,
    val suffixMatch: Boolean = false,
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
    val maxSpanChars: Int = 300,
    val scope: ProximityScope = ProximityScope.CHARACTERS,
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
        text: String? = null,
    ): List<ProximityMatch> {
        if (hits.isEmpty()) return emptyList()
        val orderedHits = hits.sortedBy(LexicalHit::startOffset)

        return rules.flatMap { rule ->
            if (rule.requiredAxisIds.isEmpty() || rule.maxSpanChars < 0) {
                emptyList()
            } else {
                when (rule.scope) {
                    ProximityScope.CHARACTERS -> minimalWindowsForRule(orderedHits, rule)
                    ProximityScope.SENTENCE -> scopedMatches(
                        hits = orderedHits,
                        rule = rule,
                        ranges = text?.let(::sentenceRanges).orEmpty(),
                    )
                    ProximityScope.PARAGRAPH -> scopedMatches(
                        hits = orderedHits,
                        rule = rule,
                        ranges = text?.let(::paragraphRanges).orEmpty(),
                    )
                    ProximityScope.PAGE -> {
                        val pageText = text ?: return@flatMap emptyList()
                        scopedMatches(
                            hits = orderedHits,
                            rule = rule,
                            ranges = listOf(0 until pageText.length),
                        )
                    }
                }
            }
        }.sortedBy(ProximityMatch::startOffset)
    }

    private fun findPattern(
        text: String,
        axis: LexicalAxis,
        pattern: String,
    ): List<LexicalHit> {
        val result = mutableListOf<LexicalHit>()
        val comparisonText = foldForComparison(text, axis)
        val comparisonPattern = foldForComparison(pattern, axis)
        val lastStart = comparisonText.length - comparisonPattern.length
        if (lastStart < 0) return result

        for (start in 0..lastStart) {
            if (!comparisonText.regionMatches(
                    thisOffset = start,
                    other = comparisonPattern,
                    otherOffset = 0,
                    length = comparisonPattern.length,
                    ignoreCase = false,
                )
            ) {
                continue
            }

            val end = start + comparisonPattern.length
            if (!matchesMode(text, pattern, start, end, axis)) continue

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

    private fun foldForComparison(text: String, axis: LexicalAxis): String = buildString(text.length) {
        text.forEach { source ->
            var folded = source
            if (!axis.diacriticsSensitive) folded = stripDiacritic(source)
            if (!axis.caseSensitive) folded = folded.lowercaseChar()
            append(folded)
        }
    }

    private fun stripDiacritic(source: Char): Char {
        val decomposed = Normalizer.normalize(source.toString(), Normalizer.Form.NFD)
        return decomposed.firstOrNull { char ->
            when (Character.getType(char)) {
                Character.NON_SPACING_MARK.toInt(),
                Character.COMBINING_SPACING_MARK.toInt(),
                Character.ENCLOSING_MARK.toInt() -> false
                else -> true
            }
        } ?: source
    }

    private fun matchesMode(
        text: String,
        pattern: String,
        start: Int,
        end: Int,
        axis: LexicalAxis,
    ): Boolean {
        if (axis.suffixMatch) {
            val needsRightBoundary = pattern.lastOrNull()?.isLetterOrDigit() == true
            return !needsRightBoundary || isBoundaryAfter(text, end)
        }

        return when (axis.matchMode) {
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
    }

    private fun isBoundaryBefore(text: String, offset: Int): Boolean =
        offset == 0 || !text[offset - 1].isLetterOrDigit()

    private fun isBoundaryAfter(text: String, offset: Int): Boolean =
        offset == text.length || !text[offset].isLetterOrDigit()

    private fun scopedMatches(
        hits: List<LexicalHit>,
        rule: ProximityRule,
        ranges: List<IntRange>,
    ): List<ProximityMatch> = ranges.mapNotNull { range ->
        if (range.isEmpty()) return@mapNotNull null
        val segmentHits = hits.filter { hit ->
            hit.startOffset >= range.first && hit.endOffsetExclusive <= range.last + 1
        }
        if (!segmentHits.map(LexicalHit::axisId).toSet().containsAll(rule.requiredAxisIds)) {
            return@mapNotNull null
        }

        minimalWindowsForRule(
            hits = segmentHits,
            rule = rule.copy(maxSpanChars = Int.MAX_VALUE),
        ).minWithOrNull(
            compareBy<ProximityMatch> { it.endOffsetExclusive - it.startOffset }
                .thenBy(ProximityMatch::startOffset),
        )
    }

    private fun sentenceRanges(text: String): List<IntRange> {
        if (text.isEmpty()) return emptyList()
        val result = mutableListOf<IntRange>()
        var start = 0

        text.forEachIndexed { index, char ->
            if (char == '.' || char == '!' || char == '?' || char == '…') {
                result += start..index
                start = index + 1
            }
        }
        if (start < text.length) result += start until text.length
        return result.filterNot { it.isEmpty() }
    }

    private fun paragraphRanges(text: String): List<IntRange> {
        if (text.isEmpty()) return emptyList()
        val separators = Regex("\\n[\\t \\r]*\\n+")
        val result = mutableListOf<IntRange>()
        var start = 0

        separators.findAll(text).forEach { match ->
            if (start < match.range.first) result += start until match.range.first
            start = match.range.last + 1
        }
        if (start < text.length) result += start until text.length
        return if (result.isEmpty()) listOf(0 until text.length) else result
    }

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
