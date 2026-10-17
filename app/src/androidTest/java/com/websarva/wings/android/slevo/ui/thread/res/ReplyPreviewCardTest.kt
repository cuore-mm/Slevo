package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.transition.ImageReplyPreviewType
import com.websarva.wings.android.slevo.ui.thread.state.ReplyPreviewPostUiModel
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 返信プレビューのレイアウト・操作・読み上げ対象を検証する。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(AndroidJUnit4::class)
class ReplyPreviewCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** ヘッダーを画像の上に保ち、レス・画像・省略を別操作として扱う。 */
    @Test
    fun cardSeparatesHeaderImageAndOverflowActions() {
        // --- Fixture ---
        val imageUrl = "https://example.com/first.jpg"
        val failures = mutableStateOf(mapOf(imageUrl to ImageLoadFailureType.UNKNOWN))
        val openedPosts = mutableListOf<Int>()
        val openedLists = mutableListOf<List<Int>>()
        val retriedImages = mutableListOf<String>()

        // --- Composition and semantics geometry ---
        composeRule.setContent {
            PreviewCardHarness(
                post = ReplyPreviewPostUiModel(
                    postNumber = 12,
                    name = "投稿者名",
                    id = "sample-id",
                    body = "プレビュー本文",
                    imageUrls = listOf(
                        imageUrl,
                        "https://example.com/second.png",
                        "https://example.com/third.gif",
                    ),
                ),
                previewType = ImageReplyPreviewType.REPLY_TARGET,
                overflowCount = 2,
                overflowNumbers = listOf(2, 4, 6, 8, 10),
                imageFailures = failures.value,
                onOpenPost = { openedPosts += 12 },
                onOpenOverflow = { openedLists += it },
                onImageRetry = { retriedImages += it },
            )
        }

        val headerText = composeRule.onNodeWithText(
            "返信先：レス12  投稿者名  ID:sample-id",
            useUnmergedTree = true,
        )
        val headerAction = composeRule.onNodeWithText("返信先：レス12  投稿者名  ID:sample-id")
        val image = composeRule.onNodeWithContentDescription(
            "レス12の画像3枚中1枚目、ほか2枚を表示",
            useUnmergedTree = true,
        )
        val headerBounds = headerText.fetchSemanticsNode().boundsInRoot
        val headerActionBounds = headerAction.fetchSemanticsNode().boundsInRoot
        val imageBounds = image.fetchSemanticsNode().boundsInRoot
        val overflowBounds = composeRule.onNodeWithText("ほか2件").fetchSemanticsNode().boundsInRoot
        assertTrue("The thumbnail must be below the full-width header", imageBounds.top >= headerBounds.bottom)
        val minimumActionHeight = with(composeRule.density) { 48.dp.toPx() }
        assertTrue(headerActionBounds.height >= minimumActionHeight)
        assertTrue(overflowBounds.height >= minimumActionHeight)
        composeRule.onNodeWithText("プレビュー本文").assertExists()
        composeRule.onNodeWithText("+2", useUnmergedTree = true).assertExists()

        // --- Independent post, overflow, and image actions ---
        composeRule.onNodeWithText("プレビュー本文").performClick()
        composeRule.onNodeWithText("ほか2件").performClick()
        image.performClick()
        composeRule.runOnIdle {
            failures.value = mapOf(imageUrl to ImageLoadFailureType.HTTP_404)
        }
        composeRule.waitForIdle()
        image.performClick()
        composeRule.runOnIdle {
            failures.value = mapOf(imageUrl to ImageLoadFailureType.HTTP_410)
        }
        composeRule.waitForIdle()
        image.performClick()

        // --- Expected callbacks ---
        assertEquals(listOf(12), openedPosts)
        assertEquals(listOf(listOf(2, 4, 6, 8, 10)), openedLists)
        assertEquals(listOf(imageUrl), retriedImages)
    }

    /** 画像なしでは本文がカードの全幅を使う。 */
    @Test
    fun cardWithoutImagesUsesFullWidthBody() {
        composeRule.setContent {
            SlevoTheme {
                SharedTransitionLayout {
                    val sharedScope = this
                    AnimatedVisibility(visible = true) {
                        ReplyPreviewCard(
                            post = ReplyPreviewPostUiModel(1, "", "", "本文全幅", emptyList()),
                            hostNamespace = "thread-post-2",
                            previewType = ImageReplyPreviewType.REPLY_SOURCE,
                            bodyTextScale = 1f,
                            onOpenPost = {},
                            onOpenOverflow = {},
                            onImageClick = { _, _, _, _ -> },
                            onImageLongPress = { _, _ -> },
                            imageLoadFailureByUrl = emptyMap(),
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

        val bodyBounds = composeRule.onNodeWithText("本文全幅").fetchSemanticsNode().boundsInRoot
        assertTrue(bodyBounds.width >= 250f)
        composeRule.onNodeWithContentDescription("レス1の画像1枚中1枚目、ほか0枚を表示")
            .assertDoesNotExist()
    }

    /** 画像のみのプレビューはヘッダーとサムネイルだけを配置する。 */
    @Test
    fun imageOnlyCardOmitsEmptyBodyText() {
        composeRule.setContent {
            PreviewCardHarness(
                post = ReplyPreviewPostUiModel(
                    postNumber = 13,
                    name = "",
                    id = "sample-id",
                    body = "",
                    imageUrls = listOf("https://example.com/only.jpg"),
                ),
                previewType = ImageReplyPreviewType.REPLY_SOURCE,
                imageFailures = mapOf("https://example.com/only.jpg" to ImageLoadFailureType.HTTP_404),
                onOpenPost = {},
            )
        }

        composeRule.onNodeWithText("レス13  ID:sample-id").assertExists()
        composeRule.onNodeWithContentDescription("レス13の画像1枚中1枚目、ほか0枚を表示").assertExists()
    }

    /** 成功した先頭画像だけが全画像一覧・先頭index・プレビューnamespaceで開く。 */
    @Test
    fun successfulThumbnailOpensFullImageListInItsOwnContext() {
        // --- Fixture ---
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val firstUrl = "android.resource://${context.packageName}/${R.drawable.ic_app_icon_about}"
        val urls = listOf(
            firstUrl,
            "https://example.com/second.png",
            "https://example.com/third.gif",
        )
        var imageLoaded = false
        var openedPost = false
        var imageClick: List<Any>? = null
        val requestedUrls = mutableListOf<String>()

        // --- Composition and image load ---
        composeRule.setContent {
            PreviewCardHarness(
                post = ReplyPreviewPostUiModel(13, "", "sample-id", "画像付き本文", urls),
                previewType = ImageReplyPreviewType.REPLY_SOURCE,
                onOpenPost = { openedPost = true },
                onImageClick = { url, imageUrls, index, namespace ->
                    imageClick = listOf(url, imageUrls, index, namespace)
                },
                onImageLoadStart = { requestedUrls += it },
                onImageLoadSuccess = { imageLoaded = true },
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { imageLoaded }
        assertTrue(requestedUrls.all { url -> url == firstUrl })
        composeRule.onNodeWithContentDescription(
            "レス13の画像3枚中1枚目、ほか2枚を表示",
            useUnmergedTree = true,
        ).performClick()

        // --- Route callback values ---
        assertEquals(
            listOf(firstUrl, urls, 0, "thread-post-20-reply-source-reply-13"),
            imageClick,
        )
        assertEquals(false, openedPost)
    }
}

/** 実画面と同じ shared-transition scope で返信プレビューを表示する。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PreviewCardHarness(
    post: ReplyPreviewPostUiModel,
    previewType: ImageReplyPreviewType,
    overflowCount: Int = 0,
    overflowNumbers: List<Int> = emptyList(),
    imageFailures: Map<String, ImageLoadFailureType> = emptyMap(),
    onOpenPost: () -> Unit,
    onOpenOverflow: (List<Int>) -> Unit = {},
    onImageRetry: (String) -> Unit = {},
    onImageClick: (String, List<String>, Int, String) -> Unit = { _, _, _, _ -> },
    onImageLoadStart: (String) -> Unit = {},
    onImageLoadSuccess: (String) -> Unit = {},
) {
    // --- Shared transition and preview card ---
    SlevoTheme {
        SharedTransitionLayout {
            val sharedScope = this
            AnimatedVisibility(visible = true) {
                val visibilityScope = this
                Box(modifier = Modifier.width(320.dp)) {
                    // --- Preview card parameters ---
                    ReplyPreviewCard(
                        post = post,
                        hostNamespace = "thread-post-20",
                        previewType = previewType,
                        overflowCount = overflowCount,
                        overflowNumbers = overflowNumbers,
                        bodyTextScale = 1f,
                        onOpenPost = onOpenPost,
                        onOpenOverflow = onOpenOverflow,
                        onImageClick = onImageClick,
                        onImageLongPress = { _, _ -> },
                        imageLoadFailureByUrl = imageFailures,
                        onImageLoadStart = onImageLoadStart,
                        onImageLoadError = { _, _ -> },
                        onImageLoadSuccess = onImageLoadSuccess,
                        onImageRetry = onImageRetry,
                        enableSharedElement = true,
                        sharedTransitionScope = sharedScope,
                        animatedVisibilityScope = visibilityScope,
                    )
                }
            }
        }
    }
}
