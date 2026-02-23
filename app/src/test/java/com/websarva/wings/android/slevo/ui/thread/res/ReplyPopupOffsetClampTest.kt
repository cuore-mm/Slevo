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
    fun calculatePopupPlacementOffsetX_keepsDesiredXWhenWithinLimits() {
        val result = calculatePopupPlacementOffsetX(
            desiredX = 20,
            popupWidthPx = 280,
            screenWidthPx = 400,
            rightMarginPx = 4,
            leftMarginPx = 0,
        )

        assertEquals(20, result)
    }

    @Test
    fun calculatePopupPlacementOffsetX_clampsToRightEdge() {
        val result = calculatePopupPlacementOffsetX(
            desiredX = 200,
            popupWidthPx = 320,
            screenWidthPx = 360,
            rightMarginPx = 4,
            leftMarginPx = 8,
        )

        assertEquals(28, result)
    }


    @Test
    fun calculatePopupPlacementOffsetX_subtractsLeftMarginFromDesiredOffset() {
        val result = calculatePopupPlacementOffsetX(
            desiredX = 20,
            popupWidthPx = 200,
            screenWidthPx = 400,
            rightMarginPx = 4,
            leftMarginPx = 12,
        )

        assertEquals(8, result)
    }

    @Test
    fun calculatePopupPlacementOffsetX_returnsBaseWhenWidthUnmeasured() {
        val result = calculatePopupPlacementOffsetX(
            desiredX = 18,
            popupWidthPx = 0,
            screenWidthPx = 360,
            rightMarginPx = 4,
            leftMarginPx = 8,
        )

        assertEquals(10, result)
    }

    @Test
    fun calculatePopupLeftMargin_increasesByStepAndCapsAtMax() {
        val first = calculatePopupLeftMargin(popupIndex = 0)
        val second = calculatePopupLeftMargin(popupIndex = 1)
        val deep = calculatePopupLeftMargin(popupIndex = 10)

        assertEquals(4.dp, first)
        assertEquals(12.dp, second)
        assertEquals(28.dp, deep)
    }
}
