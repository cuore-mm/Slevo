package com.websarva.wings.android.slevo.data.util

import com.websarva.wings.android.slevo.data.datasource.local.entity.ThreadReadState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `ThreadNewResCalculator` の新着レス数導出規則を検証するテスト。
 * 履歴なし、最初の新着番号あり、最終既読番号のみの各ケースを確認する。
 */
class ThreadNewResCalculatorTest {
    @Test
    fun calculate_returnsZero_whenHistoryDoesNotExist() {
        assertEquals(0, ThreadNewResCalculator.calculate(100, null))
    }

    @Test
    fun calculate_usesFirstNewResNo_whenItIsValid() {
        val readState = ThreadReadState(
            prevResCount = 80,
            lastReadResNo = 90,
            firstNewResNo = 85,
        )

        assertEquals(16, ThreadNewResCalculator.calculate(100, readState))
    }

    @Test
    fun calculate_usesLastReadResNo_whenFirstNewResNoIsMissing() {
        val readState = ThreadReadState(
            prevResCount = 80,
            lastReadResNo = 90,
            firstNewResNo = null,
        )

        assertEquals(10, ThreadNewResCalculator.calculate(100, readState))
    }

    @Test
    fun calculate_returnsZero_whenLatestResCountIsAlreadyRead() {
        val readState = ThreadReadState(
            prevResCount = 100,
            lastReadResNo = 100,
            firstNewResNo = 95,
        )

        assertEquals(0, ThreadNewResCalculator.calculate(100, readState))
    }
}
