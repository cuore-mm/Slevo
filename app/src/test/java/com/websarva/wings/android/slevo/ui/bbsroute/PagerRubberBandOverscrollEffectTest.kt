package com.websarva.wings.android.slevo.ui.bbsroute

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pager境界のラバーバンド計算とdelta解放を検証する。
 *
 * UI層へ依存せず、抵抗後の変位が端ほど逓減し、反対方向の入力で既存変位を戻すことを確認する。
 */
class PagerRubberBandOverscrollEffectTest {
    @Test
    fun calculateRubberBandOffset_deceleratesAsDistanceGrows() {
        val first = calculateRubberBandOffset(rawOffsetPx = 48f, resistanceLimitPx = 96f)
        val second = calculateRubberBandOffset(rawOffsetPx = 96f, resistanceLimitPx = 96f)
        val third = calculateRubberBandOffset(rawOffsetPx = 144f, resistanceLimitPx = 96f)

        assertTrue(first > 0f)
        assertTrue(second > first)
        assertTrue(third > second)
        assertTrue(second - first < first)
        assertTrue(third - second < second - first)
    }

    @Test
    fun calculateRubberBandOffset_preservesDirectionAndLimit() {
        val positive = calculateRubberBandOffset(rawOffsetPx = 10_000f, resistanceLimitPx = 96f)
        val negative = calculateRubberBandOffset(rawOffsetPx = -10_000f, resistanceLimitPx = 96f)

        assertTrue(positive > 0f)
        assertTrue(negative < 0f)
        assertTrue(abs(positive) < 96f)
        assertTrue(abs(negative) < 96f)
    }

    @Test
    fun applyToScroll_releasesExistingBoundaryOffsetBeforePagerScroll() {
        val scope = CoroutineScope(SupervisorJob())
        try {
            val effect = PagerRubberBandOverscrollEffect(scope, resistanceLimitPx = 96f)
            effect.applyToScroll(Offset(96f, 0f), NestedScrollSource.UserInput) { Offset.Zero }
            val offsetBeforeRelease = effect.offsetPx

            val consumed = effect.applyToScroll(Offset(-24f, 0f), NestedScrollSource.UserInput) {
                Offset.Zero
            }

            assertEquals(-24f, consumed.x, 0.001f)
            assertTrue(effect.offsetPx < offsetBeforeRelease)
            assertTrue(effect.offsetPx > 0f)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun applyToScroll_doesNotCreateBoundaryOffsetWhenPagerConsumesDelta() {
        val scope = CoroutineScope(SupervisorJob())
        try {
            val effect = PagerRubberBandOverscrollEffect(scope, resistanceLimitPx = 96f)
            val consumed = effect.applyToScroll(Offset(24f, 0f), NestedScrollSource.UserInput) {
                it
            }

            assertEquals(24f, consumed.x, 0.001f)
            assertEquals(0f, effect.offsetPx, 0f)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun applyToScroll_duringReturnAnimationUsesCurrentRawOffset() = runTest {
        val effect = PagerRubberBandOverscrollEffect(
            scope = this,
            resistanceLimitPx = 96f,
        )
        effect.applyToScroll(Offset(768f, 0f), NestedScrollSource.UserInput) { Offset.Zero }
        val initialOffset = effect.offsetPx

        effect.applyToFling(Velocity.Zero) { Velocity.Zero }
        runCurrent()
        advanceTimeBy(100L)
        runCurrent()
        val offsetDuringReturn = effect.offsetPx

        effect.applyToScroll(Offset(1f, 0f), NestedScrollSource.UserInput) { Offset.Zero }

        assertTrue(offsetDuringReturn < initialOffset)
        assertTrue(effect.offsetPx < initialOffset)
    }

    @Test
    fun calculateRubberBandOffset_returnsZeroForInvalidLimit() {
        assertEquals(0f, calculateRubberBandOffset(rawOffsetPx = 10f, resistanceLimitPx = 0f), 0f)
        assertEquals(0f, calculateRubberBandOffset(rawOffsetPx = 10f, resistanceLimitPx = -1f), 0f)
    }
}
