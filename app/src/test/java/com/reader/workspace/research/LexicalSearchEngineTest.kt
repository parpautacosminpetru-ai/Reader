package com.reader.workspace.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LexicalSearchEngineTest {
    @Test
    fun prefixModeFindsInflectedFormsWithoutMatchingInsideWords() {
        val axis = LexicalAxis(
            id = "cause",
            title = "Causes",
            patterns = listOf("cauz"),
            matchMode = LexicalMatchMode.PREFIX,
        )

        val hits = LexicalSearchEngine.search(
            text = "Cauza, cauzei și precauză nu sunt aceeași potrivire.",
            axes = listOf(axis),
        )

        assertEquals(listOf("Cauz", "cauz"), hits.map(LexicalHit::matchedText))
    }

    @Test
    fun suffixOptionFindsWordEndingsWithoutMatchingWordMiddles() {
        val axis = LexicalAxis(
            id = "ending",
            title = "Endings",
            patterns = listOf("ism"),
            matchMode = LexicalMatchMode.CONTAINS,
            suffixMatch = true,
        )

        val hits = LexicalSearchEngine.search(
            "calvinism, realism, ismul și calvinistic",
            listOf(axis),
        )

        assertEquals(listOf("ism", "ism"), hits.map(LexicalHit::matchedText))
    }

    @Test
    fun punctuationCanBeItsOwnAxis() {
        val punctuation = LexicalAxis(
            id = "period",
            title = "Periods",
            patterns = listOf("."),
            matchMode = LexicalMatchMode.EXACT,
        )

        val hits = LexicalSearchEngine.search("Una. Două. Trei", listOf(punctuation))

        assertEquals(2, hits.size)
        assertTrue(hits.all { it.matchedText == "." })
    }

    @Test
    fun diacriticsCanBeIgnoredWithoutChangingOffsets() {
        val axis = LexicalAxis(
            id = "romanian",
            title = "Romanian",
            patterns = listOf("cauza"),
            matchMode = LexicalMatchMode.EXACT,
            diacriticsSensitive = false,
        )

        val hits = LexicalSearchEngine.search("cauză cauza CAUZĂ", listOf(axis))

        assertEquals(listOf("cauză", "cauza", "CAUZĂ"), hits.map(LexicalHit::matchedText))
        assertEquals(listOf(0, 6, 12), hits.map(LexicalHit::startOffset))
    }

    @Test
    fun multipleAxesAreReturnedTogether() {
        val axes = listOf(
            LexicalAxis("cause", "Causes", listOf("motiv"), LexicalMatchMode.PREFIX),
            LexicalAxis("place", "Places", listOf("Roma"), LexicalMatchMode.EXACT),
            LexicalAxis("time", "Time", listOf("1517"), LexicalMatchMode.EXACT),
        )

        val hits = LexicalSearchEngine.search(
            "În Roma, în 1517, motivele au fost discutate.",
            axes,
        )

        assertEquals(setOf("cause", "place", "time"), hits.map(LexicalHit::axisId).toSet())
    }

    @Test
    fun exactModeRespectsTokenBoundaries() {
        val axis = LexicalAxis(
            id = "pope",
            title = "Pope",
            patterns = listOf("papă"),
            matchMode = LexicalMatchMode.EXACT,
        )

        val hits = LexicalSearchEngine.search("papă, papal, o papă.", listOf(axis))

        assertEquals(2, hits.size)
    }

    @Test
    fun proximityRuleFindsDenseIntersection() {
        val axes = listOf(
            LexicalAxis("cause", "Causes", listOf("motiv"), LexicalMatchMode.PREFIX),
            LexicalAxis("topic", "Reform", listOf("reform"), LexicalMatchMode.PREFIX),
            LexicalAxis("person", "Calvin", listOf("Calvin"), LexicalMatchMode.EXACT),
        )
        val text = "Motivele reformei sunt analizate lângă Calvin, apoi urmează mult text."
        val hits = LexicalSearchEngine.search(text, axes)

        val matches = LexicalSearchEngine.findProximityMatches(
            hits = hits,
            rules = listOf(
                ProximityRule(
                    id = "dense",
                    requiredAxisIds = setOf("cause", "topic", "person"),
                    maxSpanChars = 50,
                ),
            ),
            text = text,
        )

        assertEquals(1, matches.size)
        assertEquals(setOf("cause", "topic", "person"), matches.single().hits.map(LexicalHit::axisId).toSet())
    }

    @Test
    fun sentenceScopeRequiresAllAxesInsideSameSentence() {
        val axes = listOf(
            LexicalAxis("cause", "Cause", listOf("motiv"), LexicalMatchMode.PREFIX),
            LexicalAxis("reform", "Reform", listOf("reform"), LexicalMatchMode.PREFIX),
        )
        val text = "Motivul apare aici. Reforma apare separat. Motivul și reforma apar împreună!"
        val hits = LexicalSearchEngine.search(text, axes)

        val matches = LexicalSearchEngine.findProximityMatches(
            hits = hits,
            rules = listOf(
                ProximityRule(
                    id = "sentence",
                    requiredAxisIds = setOf("cause", "reform"),
                    scope = ProximityScope.SENTENCE,
                ),
            ),
            text = text,
        )

        assertEquals(1, matches.size)
        assertEquals(setOf("cause", "reform"), matches.single().hits.map(LexicalHit::axisId).toSet())
        assertTrue(matches.single().startOffset > text.indexOf("separat."))
    }

    @Test
    fun paragraphAndPageScopesAreDeterministic() {
        val axes = listOf(
            LexicalAxis("a", "A", listOf("Luther"), LexicalMatchMode.EXACT),
            LexicalAxis("b", "B", listOf("Calvin"), LexicalMatchMode.EXACT),
        )
        val text = "Luther este aici.\n\nCalvin este în alt paragraf."
        val hits = LexicalSearchEngine.search(text, axes)

        val paragraphMatches = LexicalSearchEngine.findProximityMatches(
            hits,
            listOf(ProximityRule("p", setOf("a", "b"), scope = ProximityScope.PARAGRAPH)),
            text,
        )
        val pageMatches = LexicalSearchEngine.findProximityMatches(
            hits,
            listOf(ProximityRule("page", setOf("a", "b"), scope = ProximityScope.PAGE)),
            text,
        )

        assertTrue(paragraphMatches.isEmpty())
        assertEquals(1, pageMatches.size)
    }
}
