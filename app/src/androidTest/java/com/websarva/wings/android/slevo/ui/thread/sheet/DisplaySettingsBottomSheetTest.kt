package com.websarva.wings.android.slevo.ui.thread.sheet

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.websarva.wings.android.slevo.data.model.TextDisplaySettingsConstraints
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** [DisplaySettingsContent] が canonical range/default から drift していないことを検証する。 */
@RunWith(AndroidJUnit4::class)
class DisplaySettingsBottomSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** 4 slider の semantics range が対応する canonical range と一致する。 */
    @Test
    fun sliders_useCanonicalRanges() {
        composeRule.setContent {
            SlevoTheme {
                DisplaySettingsContent(
                    textScale = TextDisplaySettingsConstraints.DEFAULT_TEXT_SCALE,
                    isIndividual = true,
                    headerTextScale = TextDisplaySettingsConstraints.DEFAULT_HEADER_TEXT_SCALE,
                    bodyTextScale = TextDisplaySettingsConstraints.DEFAULT_BODY_TEXT_SCALE,
                    lineHeight = TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT,
                    onTextScaleChange = {},
                    onIndividualChange = {},
                    onHeaderTextScaleChange = {},
                    onBodyTextScaleChange = {},
                    onLineHeightChange = {},
                )
            }
        }

        composeRule.onAllNodes(
            hasProgressBarRangeInfo(
                ProgressBarRangeInfo(
                    current = TextDisplaySettingsConstraints.DEFAULT_TEXT_SCALE,
                    range = TextDisplaySettingsConstraints.TEXT_SCALE_RANGE,
                    steps = 0,
                ),
            ),
        ).assertCountEquals(2)
        composeRule.onAllNodes(
            hasProgressBarRangeInfo(
                ProgressBarRangeInfo(
                    current = TextDisplaySettingsConstraints.DEFAULT_HEADER_TEXT_SCALE,
                    range = TextDisplaySettingsConstraints.TEXT_SCALE_RANGE,
                    steps = 0,
                ),
            ),
        ).assertCountEquals(1)
        composeRule.onAllNodes(
            hasProgressBarRangeInfo(
                ProgressBarRangeInfo(
                    current = TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT,
                    range = TextDisplaySettingsConstraints.LINE_HEIGHT_RANGE,
                    steps = 5,
                ),
            ),
        ).assertCountEquals(1)
    }

    /** Reset が canonical の 4 default を各 callback に渡す。 */
    @Test
    fun reset_passesCanonicalDefaults() {
        var resetValues: List<Float>? = null
        composeRule.setContent {
            SlevoTheme {
                DisplaySettingsContent(
                    textScale = TextDisplaySettingsConstraints.TEXT_SCALE_RANGE.endInclusive,
                    isIndividual = true,
                    headerTextScale = TextDisplaySettingsConstraints.TEXT_SCALE_RANGE.start,
                    bodyTextScale = TextDisplaySettingsConstraints.TEXT_SCALE_RANGE.endInclusive,
                    lineHeight = TextDisplaySettingsConstraints.LINE_HEIGHT_RANGE.start,
                    onTextScaleChange = { value ->
                        resetValues = listOf(
                            value,
                            resetValues?.getOrNull(1) ?: Float.NaN,
                            resetValues?.getOrNull(2) ?: Float.NaN,
                            resetValues?.getOrNull(3) ?: Float.NaN,
                        )
                    },
                    onIndividualChange = {},
                    onHeaderTextScaleChange = { value ->
                        resetValues = (resetValues ?: emptyList()).padToFour(value, 1)
                    },
                    onBodyTextScaleChange = { value ->
                        resetValues = (resetValues ?: emptyList()).padToFour(value, 2)
                    },
                    onLineHeightChange = { value ->
                        resetValues = (resetValues ?: emptyList()).padToFour(value, 3)
                    },
                )
            }
        }

        composeRule.onNodeWithText("リセット").performClick()

        assertEquals(
            listOf(
                TextDisplaySettingsConstraints.DEFAULT_TEXT_SCALE,
                TextDisplaySettingsConstraints.DEFAULT_HEADER_TEXT_SCALE,
                TextDisplaySettingsConstraints.DEFAULT_BODY_TEXT_SCALE,
                TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT,
            ),
            resetValues,
        )
    }

    /** callback の実行順に依存せず、指定位置へ値を格納する。 */
    private fun List<Float>.padToFour(value: Float, index: Int): List<Float> {
        val values = (this + List(4 - size) { Float.NaN }).toMutableList()
        values[index] = value
        return values
    }
}
