package com.websarva.wings.android.slevo.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** [ReplyAnchorParser] のアンカー解釈を検証するテスト。 */
class ReplyAnchorParserTest {
    @Test
    fun extractsSingleNumber() {
        assertEquals(listOf(12), ReplyAnchorParser.extractReferencedNumbers(">>12", maxPostNumber = 20))
    }

    @Test
    fun extractsMultipleNumbersInOrder() {
        assertEquals(
            listOf(12, 3),
            ReplyAnchorParser.extractReferencedNumbers(">>12 and >>3", maxPostNumber = 20),
        )
    }

    @Test
    fun removesDuplicateNumbers() {
        assertEquals(
            listOf(12),
            ReplyAnchorParser.extractReferencedNumbers(">>12 >>12", maxPostNumber = 20),
        )
    }

    @Test
    fun parsesAnchorTokenRangesAndOffsets() {
        val token = ReplyAnchorParser.parseAnchors("before >>12-15,18 after").single()

        assertEquals(7, token.start)
        assertEquals(17, token.end)
        assertEquals(">>12-15,18", token.rawText)
        assertEquals(
            listOf(
                ReplyAnchorParser.NumberRange(12, 15),
                ReplyAnchorParser.NumberRange(18, 18),
            ),
            token.ranges,
        )
    }

    @Test
    fun parsesMixedAnchorsAcrossNewlinesAndInlineTextInOccurrenceOrder() {
        val content = ">>2-4\nreply to >>1,3"
        val tokens = ReplyAnchorParser.parseAnchors(content)

        assertEquals(listOf(">>2-4", ">>1,3"), tokens.map { it.rawText })
        assertEquals(listOf(0, 15), tokens.map { it.start })
        assertEquals(listOf(5, 20), tokens.map { it.end })
        assertEquals(listOf(2, 3, 4, 1), ReplyAnchorParser.extractReferencedNumbers(content, 4))
    }

    @Test
    fun expandsRangesAndCommaListsWithinAvailablePosts() {
        assertEquals(
            listOf(2, 3, 4, 7, 1),
            ReplyAnchorParser.extractReferencedNumbers(">>2-4,7 >>1,3", maxPostNumber = 7),
        )
    }

    @Test
    fun rejectsInvalidAnchorRatherThanMatchingItsNumericPrefix() {
        val invalid = listOf(">>0", ">>5-1", ">>1-", ">>1,", ">>1,,3", ">>99999999999999999999")

        invalid.forEach { content ->
            assertEquals(content, emptyList<Int>(), ReplyAnchorParser.extractReferencedNumbers(content, 100))
        }
    }

    @Test
    fun hugeRangeIsBoundedByAvailablePosts() {
        assertEquals(
            (1..100).toList(),
            ReplyAnchorParser.extractReferencedNumbers(">>1-2147483647", maxPostNumber = 100),
        )
    }

    @Test
    fun doesNotExpandWhenNoPostsAreAvailable() {
        assertEquals(emptyList<Int>(), ReplyAnchorParser.extractReferencedNumbers(">>1-2147483647", 0))
    }
}
