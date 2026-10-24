package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import com.websarva.wings.android.slevo.ui.thread.state.PopupContentType
import com.websarva.wings.android.slevo.ui.thread.state.PopupInfo
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetDisclosureKey
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 返信先候補ポップアップの状態行と一時開示を検証する。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(AndroidJUnit4::class)
class ReplyTargetPopupTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** NG本文は開示前に描画せず、一覧内で開示して隠せる。 */
    @Test
    fun ngCandidateIsRevealedAndHiddenInTheSamePopupRow() {
        val privateBody = "private popup body"
        val posts = listOf(
            post(1, "normal candidate"),
            post(2, privateBody),
        )
        val info = PopupInfo(
            popupId = 42L,
            postNumbers = listOf(1, 2),
            offset = IntOffset.Zero,
            size = IntSize.Zero,
            contentType = PopupContentType.REPLY_TARGETS,
        )
        val key = ReplyTargetDisclosureKey(
            hostPostNumber = 0,
            lineIndex = 1,
            postNumber = 2,
            popupId = 42L,
        )
        val changes = mutableListOf<Pair<ReplyTargetDisclosureKey, Boolean>>()
        var nestedPopupCount = 0
        val currentPostsState = mutableStateOf(posts)
        val currentNgPostNumbersState = mutableStateOf(setOf(2))

        composeRule.setContent {
            var revealedKeys by remember { mutableStateOf(emptySet<ReplyTargetDisclosureKey>()) }
            SlevoTheme {
                SharedTransitionLayout {
                    val transitionScope = this
                    AnimatedVisibility(visible = true) {
                        ReplyTargetPopupHarness(
                            info = info,
                            posts = currentPostsState.value,
                            ngPostNumbers = currentNgPostNumbersState.value,
                            revealedKeys = revealedKeys,
                            onDisclosureChange = { changedKey, revealed ->
                                changes += changedKey to revealed
                                revealedKeys = if (revealed) {
                                    revealedKeys + changedKey
                                } else {
                                    revealedKeys - changedKey
                                }
                            },
                            onPopupOpen = { nestedPopupCount++ },
                            sharedTransitionScope = transitionScope,
                            animatedVisibilityScope = this,
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("normal candidate", substring = true).assertExists()
        composeRule.onNodeWithText("NGで非表示", substring = true).assertExists()
        composeRule.onAllNodesWithText(privateBody, substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("name2", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("ID:id2", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("参照先が上限を超えたため、一部を省略しています").assertCountEquals(0)

        val cardTag = "reply-target-disclosure-card-2"
        val headerTag = "reply-target-disclosure-header-2"
        val numberTag = "reply-target-disclosure-number-2"
        val buttonTag = "reply-target-disclosure-button-2"
        val hiddenCardBounds = composeRule.onNodeWithTag(cardTag).fetchSemanticsNode().boundsInRoot
        val hiddenHeaderBounds = composeRule.onNodeWithTag(headerTag).fetchSemanticsNode().boundsInRoot
        val hiddenNumberBounds = composeRule.onNodeWithTag(numberTag).fetchSemanticsNode().boundsInRoot
        val hiddenButtonBounds = composeRule.onNodeWithTag(buttonTag).fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithText("表示").performClick()
        composeRule.onNodeWithText(privateBody, substring = true).assertExists()
        val revealedCardBounds = composeRule.onNodeWithTag(cardTag).fetchSemanticsNode().boundsInRoot
        assertEquals(hiddenHeaderBounds, composeRule.onNodeWithTag(headerTag).fetchSemanticsNode().boundsInRoot)
        assertEquals(hiddenNumberBounds, composeRule.onNodeWithTag(numberTag).fetchSemanticsNode().boundsInRoot)
        assertEquals(hiddenButtonBounds, composeRule.onNodeWithTag(buttonTag).fetchSemanticsNode().boundsInRoot)
        composeRule.onAllNodesWithText("#2").assertCountEquals(1)
        assertTrue(revealedCardBounds.height > hiddenCardBounds.height)
        composeRule.onNodeWithText("隠す").performClick()
        composeRule.onAllNodesWithText(privateBody, substring = true).assertCountEquals(0)
        assertEquals(listOf(key to true, key to false), changes)
        assertEquals(0, nestedPopupCount)

        // --- Latest post resolution ---
        composeRule.runOnIdle {
            currentPostsState.value = posts + post(3, "newly fetched candidate")
            currentNgPostNumbersState.value = emptySet()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("normal candidate", substring = true).assertExists()
        composeRule.onAllNodesWithText("newly fetched candidate", substring = true).assertCountEquals(0)

        // --- Missing stored candidates stay out of the popup ---
        composeRule.runOnIdle {
            currentPostsState.value = posts.take(1)
            currentNgPostNumbersState.value = setOf(2)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("normal candidate", substring = true).assertExists()
        composeRule.onAllNodesWithText("NGで非表示", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("name2", substring = true).assertCountEquals(0)
    }
}

/** 指定候補を返信先一覧として表示するCompose test harness。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ReplyTargetPopupHarness(
    info: PopupInfo,
    posts: List<ThreadPostUiModel>,
    ngPostNumbers: Set<Int>,
    revealedKeys: Set<ReplyTargetDisclosureKey>,
    onDisclosureChange: (ReplyTargetDisclosureKey, Boolean) -> Unit,
    onPopupOpen: () -> Unit,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
) {
    ReplyPopup(
        popupStack = listOf(info),
        posts = posts,
        replySourceMap = emptyMap(),
        idCountMap = emptyMap(),
        idIndexList = emptyList(),
        ngPostNumbers = ngPostNumbers,
        myPostNumbers = emptySet(),
        revealedReplyTargetKeys = revealedKeys,
        headerTextScale = 1f,
        bodyTextScale = 1f,
        lineHeight = 1.25f,
        onUrlClick = {},
        onThreadUrlClick = { _: AppRoute.Thread -> },
        onImageClick = { _, _, _, _ -> },
        onImageLongPress = { _, _ -> },
        onRequestMenu = {},
        onShowTextMenu = { _, _ -> },
        onRequestTreePopup = { _, _ -> },
        onAddPopupForReplyFrom = { _, _ -> onPopupOpen() },
        onAddPopupForReplyNumber = { _, _ -> onPopupOpen() },
        onReplyTargetDisclosureChange = onDisclosureChange,
        onAddPopupForId = { _, _ -> },
        onPopupSizeChange = { _, _ -> },
        onClose = {},
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
    )
}

/** 最小投稿本文を返信先ポップアップの表示用に構成する。 */
private fun post(number: Int, content: String): ThreadPostUiModel = ThreadPostUiModel(
    header = ThreadPostUiModel.Header(
        name = "name$number",
        email = "",
        date = "",
        id = "id$number",
    ),
    body = ThreadPostUiModel.Body(content = content),
)
