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

    /** 取得済み番号だけを出現順で返し、上限外番号を個別展開しない。 */
    @Test
    fun separatesAvailableNumbersFromUnavailableRanges() {
        val result = ReplyAnchorParser.extractReplyTargetReferences(
            content = ">>3-5,8 >>4,10-2147483647",
            maxPostNumber = 5,
        )

        assertEquals(listOf(3, 4, 5), result.numbers)
        assertEquals(
            listOf(
                ReplyAnchorParser.NumberRange(8, 8),
                ReplyAnchorParser.NumberRange(10, Int.MAX_VALUE),
            ),
            result.unavailableRanges,
        )
    }

    /** 重複・隣接する未取得区間を番号列挙せず統合する。 */
    @Test
    fun mergesOverlappingAndAdjacentUnavailableRanges() {
        val result = ReplyAnchorParser.extractReplyTargetReferences(
            content = ">>105-110 >>108-120 >>121-125 >>130",
            maxPostNumber = 100,
        )

        assertEquals(emptyList<Int>(), result.numbers)
        assertEquals(
            listOf(
                ReplyAnchorParser.NumberRange(105, 125),
                ReplyAnchorParser.NumberRange(130, 130),
            ),
            result.unavailableRanges,
        )
    }

    /** 取得済み候補は参照出現順を維持して重複番号を一度だけ返す。 */
    @Test
    fun preservesFetchedNumberOccurrenceOrderAcrossRanges() {
        val result = ReplyAnchorParser.extractReplyTargetReferences(
            content = ">>5,2-4,3 >>1",
            maxPostNumber = 5,
        )

        assertEquals(listOf(5, 2, 3, 4, 1), result.numbers)
        assertEquals(emptyList<ReplyAnchorParser.NumberRange>(), result.unavailableRanges)
    }

    /** Int最大値までの範囲を末尾一件だけの区間として保持する。 */
    @Test
    fun hugeFutureRangeRemainsOneUnavailableInterval() {
        val result = ReplyAnchorParser.extractReplyTargetReferences(
            content = ">>1-2147483647",
            maxPostNumber = 100,
        )

        assertEquals((1..100).toList(), result.numbers)
        assertEquals(
            listOf(ReplyAnchorParser.NumberRange(101, Int.MAX_VALUE)),
            result.unavailableRanges,
        )
    }

    /** 取得済み番号の抽出には固定候補数上限を設けない。 */
    @Test
    fun fetchedCandidatesCanExceedFormerThousandItemLimit() {
        val result = ReplyAnchorParser.extractReplyTargetReferences(
            content = ">>1-1001",
            maxPostNumber = 1001,
        )

        assertEquals((1..1001).toList(), result.numbers)
        assertEquals(emptyList<ReplyAnchorParser.NumberRange>(), result.unavailableRanges)
    }

    /** 取得済みレスがない場合も巨大安価を全番号へ展開しない。 */
    @Test
    fun noFetchedPostsKeepsWholeReferenceAsRange() {
        val result = ReplyAnchorParser.extractReplyTargetReferences(
            content = ">>1-2147483647",
            maxPostNumber = 0,
        )

        assertEquals(emptyList<Int>(), result.numbers)
        assertEquals(
            listOf(ReplyAnchorParser.NumberRange(1, Int.MAX_VALUE)),
            result.unavailableRanges,
        )
    }

    /** Int最大値一件は取得済み番号として扱い、未取得区間を作らない。 */
    @Test
    fun maximumIntNumberIsReturnedWhenItIsFetched() {
        val result = ReplyAnchorParser.extractReplyTargetReferences(
            content = ">>2147483647",
            maxPostNumber = Int.MAX_VALUE,
        )

        assertEquals(listOf(Int.MAX_VALUE), result.numbers)
        assertEquals(emptyList<ReplyAnchorParser.NumberRange>(), result.unavailableRanges)
    }

    /** 不正tokenを無視し、有効tokenの未取得範囲は保持する。 */
    @Test
    fun replyTargetExtractionIgnoresInvalidAnchors() {
        val result = ReplyAnchorParser.extractReplyTargetReferences(
            content = ">>0 >>5-1 >>1- >>2,",
            maxPostNumber = 1,
        )

        assertEquals(emptyList<Int>(), result.numbers)
        assertEquals(emptyList<ReplyAnchorParser.NumberRange>(), result.unavailableRanges)
    }
}
