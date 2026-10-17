package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.websarva.wings.android.slevo.ui.thread.state.PostReplyPreviewUiState
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.thread.viewmodel.ThreadReplyPreviewTransformer
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 本文segmentation後の返信先枠と通常本文の順序・操作を検証する。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(AndroidJUnit4::class)
class PostItemBodyReplyPreviewTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** 単一安価を重ねず一体化し、前後本文の順序と対象レス操作を維持する。 */
    @Test
    fun singleAnchorIsReplacedByOneInlineTargetCard() {
        val posts = listOf(
            post(1, "target preview"),
            post(2, "before\n  >>1  \nafter"),
        )
        val previewState = derivePreview(posts)
        val openedPosts = mutableListOf<Int>()

        composeRule.setContent {
            SlevoTheme {
                SharedTransitionLayout {
                    val sharedScope = this
                    AnimatedVisibility(visible = true) {
                        PostBodyHarness(
                            post = posts[1],
                            previewState = previewState,
                            sharedTransitionScope = sharedScope,
                            animatedVisibilityScope = this,
                            onReplyClick = { openedPosts += it },
                        )
                    }
                }
            }
        }

        composeRule.onAllNodesWithText(">>1", substring = true, useUnmergedTree = true).assertCountEquals(1)
        val before = composeRule.onNodeWithText("before").fetchSemanticsNode().boundsInRoot
        val target = composeRule.onNodeWithText("target preview").fetchSemanticsNode().boundsInRoot
        val after = composeRule.onNodeWithText("after").fetchSemanticsNode().boundsInRoot
        assertTrue(before.bottom <= target.top)
        assertTrue(target.bottom <= after.top)

        composeRule.onNodeWithText("target preview").performClick()
        assertEquals(listOf(1), openedPosts)
    }

    /** 複数安価の元リンクを残した直後に枠を挿入し、省略操作から全候補を返す。 */
    @Test
    fun multipleAnchorLineRemainsOperableBeforeSeparateTargetCard() {
        val posts = listOf(
            post(1, "target one"),
            post(2, "target two"),
            post(3, "before\n>>1,2\nafter"),
        )
        val previewState = derivePreview(posts)
        val openedNumbers = mutableListOf<List<Int>>()
        val openedTargets = mutableListOf<Int>()

        composeRule.setContent {
            SlevoTheme {
                SharedTransitionLayout {
                    val sharedScope = this
                    AnimatedVisibility(visible = true) {
                        PostBodyHarness(
                            post = posts[2],
                            previewState = previewState,
                            sharedTransitionScope = sharedScope,
                            animatedVisibilityScope = this,
                            onReplyClick = { openedTargets += it },
                            onReplyNumbersClick = { openedNumbers += it },
                        )
                    }
                }
            }
        }

        composeRule.onAllNodesWithText(">>1,2", substring = true, useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNodeWithText(">>1,2", useUnmergedTree = true).performTouchInput { click() }
        composeRule.onNodeWithText("ほか1件").performClick()

        assertEquals(listOf(listOf(1, 2)), openedNumbers)
        assertTrue(openedTargets.isEmpty())
    }
}

/** テスト対象の本文を返信先・返信元派生stateと一緒に描画する。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PostBodyHarness(
    post: ThreadPostUiModel,
    previewState: PostReplyPreviewUiState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onReplyClick: (Int) -> Unit,
    onReplyNumbersClick: (List<Int>) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    PostItemBody(
        post = post,
        bodyTextStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        lineHeightEm = 1.25f,
        searchQuery = "",
        pressedUrl = null,
        pressedReply = null,
        scope = scope,
        onPressedUrlChange = {},
        onPressedReplyChange = {},
        onContentPressedChange = {},
        onRequestMenu = {},
        onReplyClick = onReplyClick,
        onReplyNumbersClick = onReplyNumbersClick,
        maxPostNumber = 3,
        replyPreviewState = previewState,
        transitionNamespace = "thread-post-${post.body.content.hashCode()}",
        onImageClick = { _, _, _, _ -> },
        onImageLongPress = { _, _ -> },
        imageLoadFailureByUrl = emptyMap<String, ImageLoadFailureType>(),
        onImageLoadStart = {},
        onImageLoadError = { _, _ -> },
        onImageLoadSuccess = {},
        onImageRetry = {},
        enableSharedElement = false,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onUrlClick = {},
        onThreadUrlClick = {},
        onBodyClick = {},
    )
}

/** 小さな投稿データを作って純粋transformerへ渡す。 */
private fun derivePreview(posts: List<ThreadPostUiModel>): PostReplyPreviewUiState =
    ThreadReplyPreviewTransformer.derive(
        posts = posts,
        visibleReplySourceMap = emptyMap(),
        ngPostNumbers = emptySet(),
        defaultName = "",
    ).getValue(posts.size)

/** テスト対象の投稿番号と本文から、表示に必要なモデルを構成する。 */
private fun post(number: Int, content: String): ThreadPostUiModel = ThreadPostUiModel(
    header = ThreadPostUiModel.Header(name = "name$number", email = "", date = "", id = "id$number"),
    body = ThreadPostUiModel.Body(content = content),
)
