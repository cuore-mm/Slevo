package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetUnavailableRange
import com.websarva.wings.android.slevo.ui.thread.state.ReplyPreviewFooterUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 未取得範囲だけを表示する行の状態と操作有無を検証する。 */
@RunWith(AndroidJUnit4::class)
class ReplyTargetUnavailableRangesRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** 巨大な未取得範囲を番号へ展開せず、非操作の状態文言として表示する。 */
    @Test
    fun unavailableRangeIsShownWithoutEnumeratingItsNumbers() {
        composeRule.setContent {
            SlevoTheme {
                ReplyPreviewFooterContent(
                    state = ReplyPreviewFooterUiState.Unavailable(
                        listOf(ReplyTargetUnavailableRange(101, Int.MAX_VALUE)),
                    ),
                )
            }
        }

        val unavailableRow = composeRule.onNodeWithText(
            "レス101〜2147483647は未取得です",
        )
        unavailableRow.assertExists()
        unavailableRow.assertHasNoClickAction()
        composeRule.onAllNodesWithText("ほか", substring = true).assertCountEquals(0)
    }
}
