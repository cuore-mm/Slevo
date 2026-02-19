package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * レスポップアップの X 座標クランプ計算を検証するユニットテスト。
 *
 * 右余白固定の制約と段数別左余白計算を確認する。
 */
class ReplyPopupOffsetClampTest {

    @Test
    fun calculateClampedPopupOffsetX_keepsDesiredXWhenWithinLimits() {
        val result = calculateClampedPopupOffsetX(
            desiredX = 20,
            popupWidthPx = 280,
            screenWidthPx = 400,
            rightMarginPx = 4,
        )

        assertEquals(20, result)
    }

    @Test
    fun calculateClampedPopupOffsetX_clampsToRightEdge() {
        val result = calculateClampedPopupOffsetX(
            desiredX = 200,
            popupWidthPx = 320,
            screenWidthPx = 360,
            rightMarginPx = 4,
        )

        assertEquals(36, result)
    }

    @Test
    fun calculatePopupLeftMargin_increasesByStepAndCapsAtMax() {
        val first = calculatePopupLeftMargin(popupIndex = 0)
        val second = calculatePopupLeftMargin(popupIndex = 1)
        val deep = calculatePopupLeftMargin(popupIndex = 10)

        assertEquals(4.dp, first)
        assertEquals(8.dp, second)
        assertEquals(32.dp, deep)
    }
}
