package com.websarva.wings.android.slevo.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [OwnPostDateParser] のJST、小数秒、許容差境界を検証する。 */
class OwnPostDateParserTest {
    @Test
    fun parseDatDate_usesTokyoTimezoneAndPreservesFractionalMillis() {
        assertEquals(
            1_704_034_800_010L,
            OwnPostDateParser.parseDatDate("2024/01/01 00:00:00.01(月)"),
        )
        assertEquals(
            1_704_034_800_123L,
            OwnPostDateParser.parseDatDate("2024/01/01 00:00:00.123"),
        )
    }

    @Test
    fun parseDatDate_truncatesMoreThanMillisAndRejectsInvalidValues() {
        assertEquals(
            1_704_034_800_123L,
            OwnPostDateParser.parseDatDate("2024/01/01 00:00:00.123456789"),
        )
        assertNull(OwnPostDateParser.parseDatDate("2024/02/30 00:00:00"))
        assertNull(OwnPostDateParser.parseDatDate("invalid"))
    }

    @Test
    fun isWithinTolerance_checksInclusiveBoundary() {
        assertTrue(OwnPostDateParser.isWithinTolerance(1_000L, 0L))
        assertFalse(OwnPostDateParser.isWithinTolerance(1_001L, 0L))
        assertTrue(OwnPostDateParser.isWithinTolerance(0L, 1_000L))
    }
}
