package com.websarva.wings.android.slevo.ui.thread.res

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * レスポップアップの X 座標クランプ計算を検証するユニットテスト。
 *
 * 右余白固定と左余白上限の両制約が同時に適用されることを確認する。
 */
class ReplyPopupOffsetClampTest {

    @Test
    fun calculateClampedPopupOffsetX_keepsDesiredXWhenWithinLimits() {
        val result = calculateClampedPopupOffsetX(
            desiredX = 20,
            popupWidthPx = 280,
            screenWidthPx = 400,
            rightMarginPx = 4,
            maxLeftMarginPx = 32,
        )

        assertEquals(20, result)
    }

    @Test
    fun calculateClampedPopupOffsetX_clampsToRightEdgeBeforeLeftLimit() {
        val result = calculateClampedPopupOffsetX(
            desiredX = 200,
            popupWidthPx = 320,
            screenWidthPx = 360,
            rightMarginPx = 4,
            maxLeftMarginPx = 32,
        )

        assertEquals(32, result)
    }

    @Test
    fun calculateClampedPopupOffsetX_clampsToMaxLeftMargin() {
        val result = calculateClampedPopupOffsetX(
            desiredX = 80,
            popupWidthPx = 200,
            screenWidthPx = 500,
            rightMarginPx = 4,
            maxLeftMarginPx = 32,
        )

        assertEquals(32, result)
    }
}
