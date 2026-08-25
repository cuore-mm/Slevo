package com.websarva.wings.android.slevo.data.util

import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [FiveChPostReceiptParser] の任意ヘッダーと数値検証を確認する。 */
class FiveChPostReceiptParserTest {
    private val parser = FiveChPostReceiptParser()

    @Test
    fun parse_acceptsCaseInsensitiveHeadersAndPostPlace() {
        val receipt = parser.parse(
            Headers.headersOf(
                "x-resnum", "12",
                "X-POSTPLACE", "/test/123/",
                "x-postdate", "1704067200.01",
                "x-posterid", "  ABC  ",
            ),
            expectedPostPlace = "test/123",
        )

        assertEquals(12, receipt.confirmedResNum)
        assertEquals(1_704_067_200_010L, receipt.serverPostDateMillis)
        assertEquals("ABC", receipt.posterIdHint)
    }

    @Test
    fun parse_discardsResNumWhenPostPlaceDoesNotMatch() {
        val receipt = parser.parse(
            Headers.headersOf("X-Resnum", "12", "X-Postplace", "other/999"),
            expectedPostPlace = "test/123",
        )

        assertNull(receipt.confirmedResNum)
    }

    @Test
    fun parse_acceptsResNumWhenPostPlaceIsMissingForCompatibility() {
        val receipt = parser.parse(
            Headers.headersOf("X-Resnum", "12"),
            expectedPostPlace = "test/123",
        )

        assertEquals(12, receipt.confirmedResNum)
    }

    @Test
    fun parse_rejectsInvalidResNumAndDateAndEmptyPosterId() {
        val receipt = parser.parse(
            Headers.headersOf(
                "X-Resnum", "0",
                "X-Postdate", "not-a-number",
                "X-Posterid", "   ",
            ),
        )

        assertNull(receipt.confirmedResNum)
        assertNull(receipt.serverPostDateMillis)
        assertNull(receipt.posterIdHint)
    }

    @Test
    fun parse_rejectsNegativeAndOverflowValues() {
        val receipt = parser.parse(
            Headers.headersOf(
                "X-Resnum", "2147483648",
                "X-Postdate", "-1",
            ),
        )

        assertNull(receipt.confirmedResNum)
        assertNull(receipt.serverPostDateMillis)
    }

    @Test
    fun parse_truncatesSubMillisecondPostDateWithoutFloatingPointRounding() {
        val receipt = parser.parse(Headers.headersOf("X-Postdate", "1.123456789"))

        assertEquals(1_123L, receipt.serverPostDateMillis)
    }

    @Test
    fun parse_ignoresRegionInfoAndUnknownHeaders() {
        val receipt = parser.parse(
            Headers.headersOf(
                "X-Regioninfo", "private-region",
                "X-Unknown", "not-persisted",
            ),
        )

        assertEquals(com.websarva.wings.android.slevo.data.model.PostReceipt(), receipt)
    }
}
