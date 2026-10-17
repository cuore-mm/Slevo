package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.websarva.wings.android.slevo.data.model.TextDisplaySettingsConstraints
import com.websarva.wings.android.slevo.ui.thread.state.PopupInfo
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 返信ポップアップ本文の範囲・カンマ安価が全参照先へつながることを検証する。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(AndroidJUnit4::class)
class ReplyPopupMultiAnchorTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** 複合安価の後半をタップしても表示可能な全番号とpopup内座標を返す。 */
    @Test
    fun tappingLaterPartOfMultiAnchorInPopupOpensEveryReferencedPost() {
        // --- Fixture ---
        val posts = listOf(
            post(1, "first"),
            post(2, "second"),
            post(3, "third"),
            post(4, "fourth"),
            post(5, ">>1-3,5"),
        )
        val openedReplies = mutableListOf<Pair<List<Int>, IntOffset>>()

        // --- Popup composition ---
        composeRule.setContent {
            SlevoTheme {
                SharedTransitionLayout {
                    val sharedScope = this
                    AnimatedVisibility(visible = true) {
                        ReplyPopup(
                            popupStack = listOf(
                                PopupInfo(popupId = 1L, postNumbers = listOf(5), offset = IntOffset.Zero),
                            ),
                            posts = posts,
                            replySourceMap = emptyMap(),
                            idCountMap = emptyMap(),
                            idIndexList = List(posts.size) { 1 },
                            ngPostNumbers = emptySet(),
                            myPostNumbers = emptySet(),
                            headerTextScale = 1f,
                            bodyTextScale = 1f,
                            lineHeight = TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT,
                            onUrlClick = {},
                            onThreadUrlClick = {},
                            onImageClick = { _, _, _, _ -> },
                            onImageLongPress = { _, _ -> },
                            imageLoadFailureByUrl = emptyMap<String, ImageLoadFailureType>(),
                            onRequestMenu = {},
                            onShowTextMenu = { _, _ -> },
                            onRequestTreePopup = { _, _ -> },
                            onAddPopupForReplyFrom = { numbers, offset ->
                                openedReplies += numbers to offset
                            },
                            onAddPopupForReplyNumber = { _, _ -> },
                            onAddPopupForId = { _, _ -> },
                            onPopupSizeChange = { _, _ -> },
                            onClose = {},
                            sharedTransitionScope = sharedScope,
                            animatedVisibilityScope = this,
                        )
                    }
                }
            }
        }

        // --- Later anchor tap and callback ---
        composeRule.waitForIdle()
        composeRule.onNodeWithText("返信先：", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("返信元", substring = true).assertDoesNotExist()
        val anchor = composeRule.onNodeWithText(">>1-3,5", useUnmergedTree = true)
        val anchorHeight = anchor.fetchSemanticsNode().boundsInRoot.height
        anchor.performTouchInput {
            click(Offset(composeRule.density.density * 43f, anchorHeight / 2f))
        }

        assertEquals(1, openedReplies.size)
        assertEquals(listOf(1, 2, 3, 5), openedReplies.single().first)
    }
}

/** 指定レス番号と本文からポップアップ試験用投稿を作る。 */
private fun post(number: Int, content: String): ThreadPostUiModel = ThreadPostUiModel(
    header = ThreadPostUiModel.Header(
        name = "name$number",
        email = "",
        date = "2024/01/01 00:00:00",
        id = "id$number",
    ),
    body = ThreadPostUiModel.Body(content = content),
)
