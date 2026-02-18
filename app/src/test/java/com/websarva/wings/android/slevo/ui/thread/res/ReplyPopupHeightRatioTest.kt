package com.websarva.wings.android.slevo.ui.thread.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * レスポップアップ段数別の最大高さ比率計算を検証するユニットテスト。
 *
 * 段数増加に伴う単調減少と、深い段数での下限クランプ維持を確認する。
 */
class ReplyPopupHeightRatioTest {

    @Test
    fun calculatePopupMaxHeightRatio_decreasesByDepthUntilFloor() {
        val ratioLevel1 = calculatePopupMaxHeightRatio(popupIndex = 0)
        val ratioLevel2 = calculatePopupMaxHeightRatio(popupIndex = 1)
        val ratioLevel3 = calculatePopupMaxHeightRatio(popupIndex = 2)

        assertEquals(0.75f, ratioLevel1, 0.0001f)
        assertEquals(0.67f, ratioLevel2, 0.0001f)
        assertEquals(0.59f, ratioLevel3, 0.0001f)
        assertTrue(ratioLevel1 > ratioLevel2)
        assertTrue(ratioLevel2 > ratioLevel3)
    }

    @Test
    fun calculatePopupMaxHeightRatio_keepsFloorForDeepDepth() {
        val ratioLevel5 = calculatePopupMaxHeightRatio(popupIndex = 4)
        val ratioLevel8 = calculatePopupMaxHeightRatio(popupIndex = 7)
        val ratioLevel20 = calculatePopupMaxHeightRatio(popupIndex = 19)

        assertEquals(0.45f, ratioLevel5, 0.0001f)
        assertEquals(0.45f, ratioLevel8, 0.0001f)
        assertEquals(0.45f, ratioLevel20, 0.0001f)
    }

    @Test
    fun calculatePopupMaxHeightRatio_treatsNegativeDepthAsBaseLevel() {
        val ratio = calculatePopupMaxHeightRatio(popupIndex = -3)

        assertEquals(0.75f, ratio, 0.0001f)
    }
}
