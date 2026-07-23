package com.websarva.wings.android.slevo.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [TextDisplaySettingsConstraints] の canonical range、default、finite validation を検証する。 */
class TextDisplaySettingsConstraintsTest {

    /** 各 canonical range の両端を受理し、隣接する外側の Float を拒否する。 */
    @Test
    fun ranges_acceptEndpointsAndRejectAdjacentValues() {
        val ranges = listOf(
            TextDisplaySettingsConstraints.TEXT_SCALE_RANGE to
                { value: Float -> TextDisplaySettingsConstraints.isValidTextScale(value) },
            TextDisplaySettingsConstraints.LINE_HEIGHT_RANGE to
                { value: Float -> TextDisplaySettingsConstraints.isValidLineHeight(value) },
        )

        for ((range, validator) in ranges) {
            assertTrue(validator(range.start))
            assertTrue(validator(range.endInclusive))
            assertFalse(validator(range.start.nextDown()))
            assertFalse(validator(range.endInclusive.nextUp()))
        }
    }

    /** 全 default が対応する canonical range 内の有限値であることを検証する。 */
    @Test
    fun defaults_areFiniteAndWithinTheirRanges() {
        val textDefaults = listOf(
            TextDisplaySettingsConstraints.DEFAULT_TEXT_SCALE,
            TextDisplaySettingsConstraints.DEFAULT_HEADER_TEXT_SCALE,
            TextDisplaySettingsConstraints.DEFAULT_BODY_TEXT_SCALE,
        )
        for (default in textDefaults) {
            assertTrue(default.isFinite())
            assertTrue(TextDisplaySettingsConstraints.isValidTextScale(default))
        }

        val lineHeightDefault = TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT
        assertTrue(lineHeightDefault.isFinite())
        assertTrue(TextDisplaySettingsConstraints.isValidLineHeight(lineHeightDefault))
    }

    /** NaN と正負 infinity を text scale と line height の両方で拒否する。 */
    @Test
    fun validators_rejectNonFiniteValues() {
        val nonFiniteValues = listOf(
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
        )
        for (value in nonFiniteValues) {
            assertFalse(TextDisplaySettingsConstraints.isValidTextScale(value))
            assertFalse(TextDisplaySettingsConstraints.isValidLineHeight(value))
        }
    }

    /** Returns the adjacent representable Float below this value. */
    private fun Float.nextDown(): Float = Math.nextAfter(this, Double.NEGATIVE_INFINITY)

    /** Returns the adjacent representable Float above this value. */
    private fun Float.nextUp(): Float = Math.nextAfter(this, Double.POSITIVE_INFINITY)
}
