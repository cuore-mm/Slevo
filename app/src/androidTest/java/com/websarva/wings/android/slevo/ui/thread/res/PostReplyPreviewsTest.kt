package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.websarva.wings.android.slevo.data.model.TextDisplaySettingsConstraints
import com.websarva.wings.android.slevo.ui.thread.state.PostReplyPreviewUiState
import com.websarva.wings.android.slevo.ui.thread.state.ReplyPreviewPostUiModel
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 返信元プレビューは古い順の三件に制限し、残りは全候補一覧へ委譲する。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(AndroidJUnit4::class)
class PostReplyPreviewsTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** 見出しのない引用風表示、区切り線、先頭三レスと省略操作を検証する。 */
    @Test
    fun showsThreeOldestSourcesAndOpensCompleteOverflowList() {
        // --- Fixture ---
        val sourceNumbers = listOf(2, 4, 6, 8, 10)
        val openedPosts = mutableListOf<Int>()
        val openedLists = mutableListOf<List<Int>>()
        val state = PostReplyPreviewUiState(
            replySourcePreviews = listOf(2, 4, 6).map { number ->
                ReplyPreviewPostUiModel(number, "name$number", "id$number", "body$number", emptyList())
            },
            replySourceNumbers = sourceNumbers,
        )

        // --- Compose content ---
        composeRule.setContent {
            SlevoTheme {
                SharedTransitionLayout {
                    val sharedScope = this
                    AnimatedVisibility(visible = true) {
                        PostReplyPreviews(
                            state = state,
                            hostNamespace = "thread-post-12",
                            headerTextScale = 1f,
                            bodyTextScale = 1f,
                            lineHeightEm = TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT,
                            onReplyClick = { openedPosts += it },
                            onReplyNumbersClick = { openedLists += it },
                            onImageClick = { _, _, _, _ -> },
                            onImageLongPress = { _, _ -> },
                            imageLoadFailureByUrl = emptyMap<String, ImageLoadFailureType>(),
                            onImageLoadStart = {},
                            onImageLoadError = { _, _ -> },
                            onImageLoadSuccess = {},
                            onImageRetry = {},
                            enableSharedElement = false,
                            sharedTransitionScope = sharedScope,
                            animatedVisibilityScope = this,
                        )
                    }
                }
            }
        }

        // --- Visible previews and actions ---
        composeRule.onNodeWithText("返信").assertDoesNotExist()
        composeRule.onNodeWithText("返信 3件").assertDoesNotExist()
        composeRule.onNodeWithText("2  name2  ID:id2").assertExists()
        composeRule.onNodeWithText("body2").assertExists()
        composeRule.onNodeWithText("body4").assertExists()
        composeRule.onNodeWithText("body6").assertExists()
        composeRule.onAllNodesWithTag("reply-preview-source-divider").assertCountEquals(2)
        val sourceRail = composeRule.onNodeWithTag("reply-preview-source-rail-2")
        assertTrue(sourceRail.fetchSemanticsNode().boundsInRoot.height > 0f)
        val overflow = composeRule.onNodeWithText("ほか2件")
        assertTrue(
            overflow.fetchSemanticsNode().boundsInRoot.height >=
                48f * composeRule.density.density,
        )
        overflow.performClick()
        composeRule.onNodeWithText("body2").performClick()

        assertEquals(listOf(sourceNumbers), openedLists)
        assertEquals(listOf(2), openedPosts)
    }
}
