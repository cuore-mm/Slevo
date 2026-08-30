package com.websarva.wings.android.slevo.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** [ReplyAnchorParser] のアンカー解釈を検証するテスト。 */
class ReplyAnchorParserTest {
    @Test
    fun extractsSingleNumber() {
        assertEquals(listOf(12), ReplyAnchorParser.extractReferencedNumbers(">>12"))
    }

    @Test
    fun extractsMultipleNumbersInOrder() {
        assertEquals(
            listOf(12, 3),
            ReplyAnchorParser.extractReferencedNumbers(">>12 and >>3"),
        )
    }

    @Test
    fun removesDuplicateNumbers() {
        assertEquals(
            listOf(12),
            ReplyAnchorParser.extractReferencedNumbers(">>12 >>12"),
        )
    }

    @Test
    fun doesNotExpandRangeNotation() {
        assertEquals(
            listOf(12),
            ReplyAnchorParser.extractReferencedNumbers(">>12-15"),
        )
    }
}
