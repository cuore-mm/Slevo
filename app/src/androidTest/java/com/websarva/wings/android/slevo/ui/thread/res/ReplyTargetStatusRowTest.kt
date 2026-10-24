package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetCandidateStatus
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetUnavailableRange
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
                    unavailableRanges = emptyList(),
                    onReveal = { revealCount++ },
                    onOpenOverflow = null,
                )
            }
        }

        composeRule.onNodeWithText("#12").assertExists()
        composeRule.onNodeWithText("NGで非表示").assertExists()
        composeRule.onNodeWithTag("reply-target-disclosure-card-12").assertExists()
        val revealButton = composeRule.onNodeWithTag("reply-target-disclosure-button-12")
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
                    unavailableRanges = emptyList(),
                    onReveal = null,
                    onOpenOverflow = null,
                )
            }
        }

        composeRule.onNodeWithText("99  未取得のため参照できません").assertExists()
        composeRule.onAllNodesWithText("表示").assertCountEquals(0)
    }

    /** 残りの取得済み候補数から一覧操作を呼び出す。 */
    @Test
    fun acquiredCandidateOverflowOffersList() {
        var overflowCount = 0
        composeRule.setContent {
            SlevoTheme {
                ReplyTargetStatusRow(
                    postNumber = 12,
                    status = ReplyTargetCandidateStatus.NG,
                    overflowCount = 999,
                    overflowNumbers = (1..1_000).toList(),
                    unavailableRanges = listOf(ReplyTargetUnavailableRange(1_001, Int.MAX_VALUE)),
                    onReveal = {},
                    onOpenOverflow = { overflowCount++ },
                )
            }
        }

        composeRule.onNodeWithText("ほか999件").assertExists()
        composeRule.onNodeWithText("レス1001〜2147483647は未取得のため参照できません").assertExists()
        composeRule.onNodeWithText("ほか999件").performClick()
        assertEquals(1, overflowCount)
    }

    /** 候補がない場合も巨大未取得範囲を非操作の状態文言で表示する。 */
    @Test
    fun unavailableRangeIsShownWithoutEnumeratingItsNumbers() {
        composeRule.setContent {
            SlevoTheme {
                ReplyTargetUnavailableRangesRow(
                    ranges = listOf(ReplyTargetUnavailableRange(101, Int.MAX_VALUE)),
                )
            }
        }

        val unavailableRow = composeRule.onNodeWithText("レス101〜2147483647は未取得のため参照できません")
        unavailableRow.assertExists()
        unavailableRow.assertHasNoClickAction()
        composeRule.onAllNodesWithText("ほか", substring = true).assertCountEquals(0)
    }
}
