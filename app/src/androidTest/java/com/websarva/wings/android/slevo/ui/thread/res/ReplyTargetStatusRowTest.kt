package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetCandidateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 返信先のNG・未取得状態行と表示操作を検証する。 */
@RunWith(AndroidJUnit4::class)
class ReplyTargetStatusRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** NG状態は48dp以上の表示ボタンを持ち、タップを呼び出し元へ通知する。 */
    @Test
    fun ngStateOffersAccessibleRevealAction() {
        var revealCount = 0
        composeRule.setContent {
            SlevoTheme {
                ReplyTargetStatusRow(
                    postNumber = 12,
                    status = ReplyTargetCandidateStatus.NG,
                    overflowCount = 0,
                    overflowNumbers = emptyList(),
                    hasMoreCandidates = false,
                    onReveal = { revealCount++ },
                    onOpenOverflow = null,
                )
            }
        }

        composeRule.onNodeWithText("12  NGで非表示").assertExists()
        val revealButton = composeRule.onNodeWithText("表示")
        revealButton.assertExists()
        assertTrue(revealButton.fetchSemanticsNode().boundsInRoot.height >= composeRule.density.density * 48f)
        revealButton.performClick()

        assertEquals(1, revealCount)
    }

    /** 未取得状態は番号と説明だけを示し、NG表示操作を持たない。 */
    @Test
    fun unavailableStateDoesNotOfferRevealAction() {
        composeRule.setContent {
            SlevoTheme {
                ReplyTargetStatusRow(
                    postNumber = 99,
                    status = ReplyTargetCandidateStatus.UNAVAILABLE,
                    overflowCount = 0,
                    overflowNumbers = emptyList(),
                    hasMoreCandidates = false,
                    onReveal = null,
                    onOpenOverflow = null,
                )
            }
        }

        composeRule.onNodeWithText("99  未取得のため参照できません").assertExists()
        composeRule.onAllNodesWithText("表示").assertCountEquals(0)
    }

    /** 候補が上限を超えた状態では残数と超過通知を表示し、一覧操作を呼び出す。 */
    @Test
    fun truncatedCandidateStatusOffersOverflowAndNotice() {
        var overflowCount = 0
        composeRule.setContent {
            SlevoTheme {
                ReplyTargetStatusRow(
                    postNumber = 12,
                    status = ReplyTargetCandidateStatus.NG,
                    overflowCount = 999,
                    overflowNumbers = (1..1_000).toList(),
                    hasMoreCandidates = true,
                    onReveal = {},
                    onOpenOverflow = { overflowCount++ },
                )
            }
        }

        composeRule.onNodeWithText("ほか999件").assertExists()
        composeRule.onNodeWithText("参照先が上限を超えたため、一部を省略しています").assertExists()
        composeRule.onNodeWithText("ほか999件").performClick()
        assertEquals(1, overflowCount)
    }
}
