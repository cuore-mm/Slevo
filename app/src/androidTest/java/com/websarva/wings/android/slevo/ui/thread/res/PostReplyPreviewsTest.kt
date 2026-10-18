package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.websarva.wings.android.slevo.ui.thread.state.PostReplyPreviewUiState
import com.websarva.wings.android.slevo.ui.thread.state.ReplyPreviewPostUiModel
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 返信元プレビューは古い順の三件に制限し、残りは全候補一覧へ委譲する。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(AndroidJUnit4::class)
class PostReplyPreviewsTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** 先頭三レスの個別操作と「ほか2件」からの全番号操作を検証する。 */
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
        composeRule.onNodeWithText("body2").assertExists()
        composeRule.onNodeWithText("body4").assertExists()
        composeRule.onNodeWithText("body6").assertExists()
        composeRule.onNodeWithText("ほか2件").performClick()
        composeRule.onNodeWithText("body2").performClick()

        assertEquals(listOf(sourceNumbers), openedLists)
        assertEquals(listOf(2), openedPosts)
    }
}
