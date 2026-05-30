package com.websarva.wings.android.slevo.data.util

import com.websarva.wings.android.slevo.data.model.THREAD_KEY_THRESHOLD
import com.websarva.wings.android.slevo.data.model.ThreadDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadInfoDerivedCalculatorTest {

    @Test
    fun `calculateDate - valid epoch thread key returns correct date`() {
        // 1714532040 → JST 2024-05-01 11:54:00
        val result = ThreadInfoDerivedCalculator.calculateDate("1714532040")
        assertEquals(2024, result.year)
        assertEquals(5, result.month)
        assertEquals(1, result.day)
        assertEquals(11, result.hour)
        assertEquals(54, result.minute)
        assertEquals("水", result.dayOfWeek)
    }

    @Test
    fun `calculateDate - invalid thread key returns default date`() {
        val result = ThreadInfoDerivedCalculator.calculateDate("abc")
        assertEquals(ThreadDate(0, 0, 0, 0, 0, ""), result)
    }

    @Test
    fun `calculateDate - thread key above threshold returns default date`() {
        val result = ThreadInfoDerivedCalculator.calculateDate("${THREAD_KEY_THRESHOLD + 1}")
        assertEquals(ThreadDate(0, 0, 0, 0, 0, ""), result)
    }

    @Test
    fun `calculateDate - zero thread key returns default date`() {
        val result = ThreadInfoDerivedCalculator.calculateDate("0")
        assertEquals(ThreadDate(0, 0, 0, 0, 0, ""), result)
    }

    @Test
    fun `calculateMomentum - valid key with positive resCount returns momentum`() {
        // 1日経過、レス数100 → 勢い100.0
        val threadKey = "1714532040" // 2024-05-01 12:34:00
        val nowSeconds = threadKey.toLong() + 86400 // 1日後
        val result = ThreadInfoDerivedCalculator.calculateMomentum(
            threadKey = threadKey,
            resCount = 100,
            nowSeconds = nowSeconds,
        )
        assertEquals(100.0, result, 0.1)
    }

    @Test
    fun `calculateMomentum - zero resCount returns zero`() {
        val result = ThreadInfoDerivedCalculator.calculateMomentum(
            threadKey = "1714532040",
            resCount = 0,
            nowSeconds = 1714532040 + 86400,
        )
        assertEquals(0.0, result, 0.0)
    }

    @Test
    fun `calculateMomentum - invalid thread key returns zero`() {
        val result = ThreadInfoDerivedCalculator.calculateMomentum(
            threadKey = "abc",
            resCount = 100,
            nowSeconds = 1714532040 + 86400,
        )
        assertEquals(0.0, result, 0.0)
    }

    @Test
    fun `calculateMomentum - thread key above threshold returns zero`() {
        val result = ThreadInfoDerivedCalculator.calculateMomentum(
            threadKey = "${THREAD_KEY_THRESHOLD + 1}",
            resCount = 100,
            nowSeconds = THREAD_KEY_THRESHOLD + 1 + 86400,
        )
        assertEquals(0.0, result, 0.0)
    }

    @Test
    fun `calculateMomentum - future thread key clamps to minimum 1 second`() {
        // 未来のキーでも elapsedSeconds は最低 1 秒として計算される
        val threadKey = 1714532040L
        val nowSeconds = threadKey // 同時刻
        val result = ThreadInfoDerivedCalculator.calculateMomentum(
            threadKey = threadKey.toString(),
            resCount = 100,
            nowSeconds = nowSeconds,
        )
        // 1秒 = 1/86400日 → 100 / (1/86400) = 8,640,000
        val expected = 100.0 / (1.0 / 86400.0)
        assertEquals(expected, result, 0.1)
    }

    @Test
    fun `calculate - returns both date and momentum`() {
        val threadKey = "1714532040"
        val nowSeconds = threadKey.toLong() + 86400
        val result = ThreadInfoDerivedCalculator.calculate(
            threadKey = threadKey,
            resCount = 100,
            nowSeconds = nowSeconds,
        )
        assertEquals(2024, result.date.year)
        assertEquals(100.0, result.momentum, 0.1)
    }

    @Test
    fun `isEpochThreadKey - valid key returns true`() {
        assertEquals(true, ThreadInfoDerivedCalculator.isEpochThreadKey("1714532040"))
    }

    @Test
    fun `isEpochThreadKey - invalid key returns false`() {
        assertEquals(false, ThreadInfoDerivedCalculator.isEpochThreadKey("abc"))
        assertEquals(false, ThreadInfoDerivedCalculator.isEpochThreadKey("0"))
        assertEquals(false, ThreadInfoDerivedCalculator.isEpochThreadKey("${THREAD_KEY_THRESHOLD + 1}"))
    }
}
