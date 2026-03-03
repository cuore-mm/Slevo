package com.websarva.wings.android.slevo.ui.thread

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ツリーインデント計算ユーティリティの振る舞いを検証するテスト。
 */
class ThreadIndentationTest {

    @Test
    fun calculateTreeIndentStep_usesDefaultWhenUnderLimit() {
        val step = calculateTreeIndentStep(
            containerWidth = 200.dp,
            maxDepth = 2,
            defaultStep = 16.dp,
            maxIndentRatio = 0.25f,
        )

        assertEquals(16.dp, step)
    }

    @Test
    fun calculateTreeIndentStep_scalesDownWhenExceedingLimit() {
        val step = calculateTreeIndentStep(
            containerWidth = 200.dp,
            maxDepth = 10,
            defaultStep = 16.dp,
            maxIndentRatio = 0.25f,
        )

        assertEquals(5.dp, step)
    }

    @Test
    fun calculateTreeIndentStep_returnsZeroForRootOnly() {
        val step = calculateTreeIndentStep(
            containerWidth = 200.dp,
            maxDepth = 0,
            defaultStep = 16.dp,
            maxIndentRatio = 0.25f,
        )

        assertEquals(0.dp, step)
    }

    @Test
    fun mapTreeMaxDepths_groupsByRootDepth() {
        val result = mapTreeMaxDepths(listOf(0, 1, 2, 1, 0, 1))

        assertEquals(listOf(2, 2, 2, 2, 1, 1), result)
    }

    @Test
    fun calculateTreeIndentWidths_appliesPerTreeDepth() {
        val widths = calculateTreeIndentWidths(
            depths = listOf(0, 1, 2, 1, 0, 1),
            containerWidth = 200.dp,
            defaultStep = 16.dp,
            maxIndentRatio = 0.25f,
        )

        assertEquals(6, widths.size)
        assertTrue(widths[2] <= 32.dp)
        assertEquals(0.dp, widths[0])
        assertEquals(0.dp, widths[4])
    }
}
