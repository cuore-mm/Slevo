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

    /** 返信先候補は取得済み上限と独立して出現順・重複なしで上限まで展開する。 */
    @Test
    fun extractsDistinctReferencedNumbersUpToLimit() {
        val result = ReplyAnchorParser.extractReferencedNumbersWithLimit(
            content = ">>3-5,4 >>1,5",
            limit = 4,
        )

        assertEquals(listOf(3, 4, 5, 1), result.numbers)
        assertEquals(false, result.hasMore)
    }

    /** 候補がちょうど上限数なら打切り状態にしない。 */
    @Test
    fun exactLimitDoesNotReportMoreCandidates() {
        val result = ReplyAnchorParser.extractReferencedNumbersWithLimit(
            content = ">>1-1000 >>1-1000",
            limit = 1000,
        )

        assertEquals((1..1000).toList(), result.numbers)
        assertEquals(false, result.hasMore)
    }

    /** 上限を超える巨大範囲は上限+1件目を見つけた時点で打ち切る。 */
    @Test
    fun hugeRangeStopsAfterFindingCandidateBeyondLimit() {
        val result = ReplyAnchorParser.extractReferencedNumbersWithLimit(
            content = ">>1-2147483647",
            limit = 1000,
        )

        assertEquals((1..1000).toList(), result.numbers)
        assertEquals(true, result.hasMore)
    }

    /** 範囲端点がInt最大値でも増分をoverflowさせず、終端で停止する。 */
    @Test
    fun maximumIntAnchorDoesNotOverflow() {
        val result = ReplyAnchorParser.extractReferencedNumbersWithLimit(
            content = ">>2147483647",
            limit = 1000,
        )

        assertEquals(listOf(Int.MAX_VALUE), result.numbers)
        assertEquals(false, result.hasMore)
    }

    /** 不正tokenは候補を作らず、後続する有効tokenは処理する。 */
    @Test
    fun limitedExtractionIgnoresInvalidAnchors() {
        val result = ReplyAnchorParser.extractReferencedNumbersWithLimit(
            content = ">>0 >>5-1 >>1- >>2",
            limit = 10,
        )

        assertEquals(listOf(2), result.numbers)
        assertEquals(false, result.hasMore)
    }

    /** 空の候補上限は受け付けない。 */
    @Test(expected = IllegalArgumentException::class)
    fun limitedExtractionRequiresPositiveLimit() {
        ReplyAnchorParser.extractReferencedNumbersWithLimit(">>1", limit = 0)
    }
}
