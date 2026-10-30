package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.TextDisplaySettingsConstraints
import com.websarva.wings.android.slevo.ui.common.transition.ImageReplyPreviewType
import com.websarva.wings.android.slevo.ui.thread.state.ReplyPreviewPostUiModel
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetUnavailableRange
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /** 未取得文言の位置を押しても候補一覧を開き、投稿との間に区切り線を表示する。 */
    @Test
    fun combinedFooterOpensListFromUnavailableText() {
        val opened = mutableListOf<List<Int>>()
        composeRule.setContent {
            PreviewCardHarness(
                post = ReplyPreviewPostUiModel(1, "", "", "本文", emptyList()),
                previewType = ImageReplyPreviewType.REPLY_TARGET,
                overflowCount = 1,
                overflowNumbers = listOf(1, 2),
                unavailableRanges = listOf(ReplyTargetUnavailableRange(101, 103)),
                onOpenPost = {},
                onOpenOverflow = { opened.add(it) },
            )
        }
        composeRule.onNodeWithTag("reply-preview-footer-divider", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("reply-preview-footer").performTouchInput {
            click(Offset(width * 0.25f, height * 0.8f))
        }
        composeRule.runOnIdle { assertEquals(listOf(listOf(1, 2)), opened) }
    }

    /** ヘッダーを画像の上に保ち、レス・画像・省略を別操作として扱う。 */
    @Test
    fun cardSeparatesHeaderImageAndOverflowActions() {
        // --- Fixture ---
        val imageUrl = "https://example.com/first.jpg"
        val lineHeightEm = TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT
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
                lineHeightEm = lineHeightEm,
                imageFailures = failures.value,
                onOpenPost = { openedPosts += 12 },
                onOpenOverflow = { openedLists += it },
                onImageRetry = { retriedImages += it },
            )
        }

        val headerText = composeRule.onNodeWithText(
            "12  投稿者名  ID:sample-id",
            useUnmergedTree = true,
        )
        val headerAction = composeRule.onNodeWithText("12  投稿者名  ID:sample-id")
        val image = composeRule.onNodeWithContentDescription(
            "レス12の画像3枚中1枚目、ほか2枚を表示",
            useUnmergedTree = true,
        )
        val headerBounds = headerText.fetchSemanticsNode().boundsInRoot
        val headerActionBounds = headerAction.fetchSemanticsNode().boundsInRoot
        val imageBounds = image.fetchSemanticsNode().boundsInRoot
        val overflowBounds = composeRule.onNodeWithText("全5件を表示").fetchSemanticsNode().boundsInRoot
        assertTrue("The thumbnail must be below the full-width header", imageBounds.top >= headerBounds.bottom)
        val minimumActionHeight = with(composeRule.density) { 48.dp.toPx() }
        assertTrue(headerActionBounds.height >= minimumActionHeight)
        assertTrue(overflowBounds.height >= minimumActionHeight)
        composeRule.onNodeWithText("プレビュー本文").assertExists()
        composeRule.onNodeWithText("+2", useUnmergedTree = true).assertExists()

        // --- Independent post, overflow, and image actions ---
        composeRule.onNodeWithText("プレビュー本文").performClick()
        composeRule.onNodeWithText("全5件を表示").performClick()
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

    /** 開閉で共通Footerの水平位置を変えず、候補数と未取得範囲を両状態に表示する。 */
    @Test
    fun footerKeepsSameHorizontalPositionWhenReplyTargetIsHidden() {
        val hidden = mutableStateOf(true)
        composeRule.setContent {
            PreviewCardHarness(
                post = ReplyPreviewPostUiModel(
                    postNumber = 1,
                    name = "返信先",
                    id = "target-id",
                    body = "開示本文",
                    imageUrls = emptyList(),
                ),
                previewType = ImageReplyPreviewType.REPLY_TARGET,
                isNgHidden = hidden.value,
                onReveal = { hidden.value = false },
                onHide = { hidden.value = true },
                overflowCount = 2,
                overflowNumbers = listOf(1, 2, 3),
                unavailableRanges = listOf(ReplyTargetUnavailableRange(101, 103)),
                onOpenPost = {},
            )
        }

        val overflow = composeRule.onNodeWithText("全3件を表示")
        val unavailable = composeRule.onNodeWithText(
            "レス101〜103は未取得です",
        )
        val hiddenOverflowLeft = overflow.fetchSemanticsNode().boundsInRoot.left
        val hiddenUnavailableLeft = unavailable.fetchSemanticsNode().boundsInRoot.left
        composeRule.onNodeWithText("表示").performClick()
        composeRule.onNodeWithText("開示本文").assertExists()
        assertEquals(
            hiddenOverflowLeft,
            overflow.fetchSemanticsNode().boundsInRoot.left,
        )
        assertEquals(
            hiddenUnavailableLeft,
            unavailable.fetchSemanticsNode().boundsInRoot.left,
        )

        composeRule.onNodeWithText("隠す").performClick()
        composeRule.onNodeWithText("開示本文").assertDoesNotExist()
        assertEquals(
            hiddenOverflowLeft,
            overflow.fetchSemanticsNode().boundsInRoot.left,
        )
        assertEquals(
            hiddenUnavailableLeft,
            unavailable.fetchSemanticsNode().boundsInRoot.left,
        )
    }

    /** 返信元Footerは省略件数だけを表示し、返信先専用の未取得範囲を描画しない。 */
    @Test
    fun replySourceFooterOmitsUnavailableTargetRanges() {
        composeRule.setContent {
            PreviewCardHarness(
                post = ReplyPreviewPostUiModel(
                    postNumber = 4,
                    name = "返信元",
                    id = "source-id",
                    body = "返信本文",
                    imageUrls = emptyList(),
                ),
                previewType = ImageReplyPreviewType.REPLY_SOURCE,
                overflowCount = 1,
                overflowNumbers = listOf(4, 5),
                unavailableRanges = listOf(ReplyTargetUnavailableRange(101, 103)),
                onOpenPost = {},
            )
        }

        composeRule.onNodeWithText("全2件を表示").assertExists()
        composeRule.onNodeWithText("レス101〜103は未取得です")
            .assertDoesNotExist()
    }

    /** 画像なしでは本文がカードの全幅を使う。 */
    @Test
    fun cardWithoutImagesUsesFullWidthBody() {
        composeRule.setContent {
            SlevoTheme {
                SharedTransitionLayout {
                    val sharedScope = this
                    AnimatedVisibility(visible = true) {
                        ReplyPreviewPostContent(
                            post = ReplyPreviewPostUiModel(1, "", "", "本文全幅", emptyList()),
                            hostNamespace = "thread-post-2",
                            previewType = ImageReplyPreviewType.REPLY_SOURCE,
                            onHide = null,
                            headerTextScale = 1f,
                            bodyTextScale = 1f,
                            lineHeightEm = TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT,
                            onOpenPost = {},
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

    /** 投稿コンテナと自レス表示線の内側で二行本文が高さ制約により省略されない。 */
    @Test
    fun twoLineBodyDoesNotEllipsizeInsidePostItemContainer() {
        val body = "にしてもデブ過ぎやろ\nカロリー過多や"
        composeRule.setContent {
            PreviewCardHarness(
                post = ReplyPreviewPostUiModel(169, "", "bj6p0cIb7", body, emptyList()),
                previewType = ImageReplyPreviewType.REPLY_SOURCE,
                insidePostItemContainer = true,
                onOpenPost = {},
            )
        }

        val layout = textLayoutResult(body)
        assertEquals(2, layout.lineCount)
        assertFalse("Two fitting lines must not overflow vertically", layout.didOverflowHeight)
        (0 until layout.lineCount).forEach { line ->
            assertFalse("Line $line must not be ellipsized", layout.isLineEllipsized(line))
            val lineWidth = layout.multiParagraph.getLineRight(line) -
                layout.multiParagraph.getLineLeft(line)
            assertTrue("Line $line must fit the available width", lineWidth <= layout.size.width)
        }
        assertEquals(
            body.length,
            layout.multiParagraph.getLineEnd(layout.lineCount - 1, visibleEnd = false),
        )

        val containerBounds = composeRule.onNodeWithTag("reply-preview-post-container")
            .fetchSemanticsNode().boundsInRoot
        val bodyBounds = composeRule.onNodeWithText(body).fetchSemanticsNode().boundsInRoot
        assertTrue(bodyBounds.top > containerBounds.top)
        assertTrue(bodyBounds.bottom < containerBounds.bottom)

        val containerImage = composeRule.onNodeWithTag("reply-preview-post-container")
            .captureToImage()
            .toPixelMap()
        assertTrue(containerImage[0, containerImage.height / 2].alpha > 0.9f)
    }

    /** 同じ投稿コンテナ内でも三行上限と末尾省略を維持する。 */
    @Test
    fun longBodyStillEllipsizesAtThreeLinesInsidePostItemContainer() {
        val body = "一行目\n二行目\n三行目\n四行目"
        composeRule.setContent {
            PreviewCardHarness(
                post = ReplyPreviewPostUiModel(169, "", "sample-id", body, emptyList()),
                previewType = ImageReplyPreviewType.REPLY_SOURCE,
                insidePostItemContainer = true,
                onOpenPost = {},
            )
        }

        val layout = textLayoutResult(body)
        assertEquals(3, layout.lineCount)
        assertTrue("Four lines must exceed the three-line limit", layout.didOverflowHeight)
        assertTrue(layout.isLineEllipsized(2))
    }

    /** ヘッダーと本文へ別々の文字・行高設定を反映し、数字始まりの通常体ヘッダーを描画する。 */
    @Test
    fun headerAndBodyUseIndependentTextScalesWithNormalHeaderWeight() {
        val headerScale = 1.2f
        val bodyScale = 0.8f
        val lineHeightEm = TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT
        composeRule.setContent {
            SlevoTheme {
                Text("通常サイズ", style = MaterialTheme.typography.bodyMedium)
                PreviewCardHarness(
                    post = ReplyPreviewPostUiModel(17, "", "", "本文サイズ確認", emptyList()),
                    previewType = ImageReplyPreviewType.REPLY_SOURCE,
                    headerTextScale = headerScale,
                    bodyTextScale = bodyScale,
                    lineHeightEm = lineHeightEm,
                    onOpenPost = {},
                )
            }
        }

        val baselineSize = textLayoutResult("通常サイズ").layoutInput.style.fontSize.value
        val headerStyle = textLayoutResult("17").layoutInput.style
        val bodyStyle = textLayoutResult("本文サイズ確認").layoutInput.style
        assertEquals(baselineSize * headerScale * 0.9f, headerStyle.fontSize.value, 0.01f)
        assertEquals(baselineSize * bodyScale * 0.9f, bodyStyle.fontSize.value, 0.01f)
        assertEquals(lineHeightEm.em, headerStyle.lineHeight)
        assertEquals(lineHeightEm.em, bodyStyle.lineHeight)
        assertEquals(FontWeight.Normal, headerStyle.fontWeight)
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

        composeRule.onNodeWithText("13  ID:sample-id").assertExists()
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

    /** テキストノードから実際に適用されたComposeのレイアウト結果を取得する。 */
    private fun textLayoutResult(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                assertTrue(action(results))
            }
        return results.single()
    }
}

/** 実画面と同じ shared-transition scope で返信プレビューを表示する。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PreviewCardHarness(
    post: ReplyPreviewPostUiModel,
    previewType: ImageReplyPreviewType,
    headerTextScale: Float = 1f,
    bodyTextScale: Float = 1f,
    lineHeightEm: Float = TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT,
    overflowCount: Int = 0,
    overflowNumbers: List<Int> = emptyList(),
    unavailableRanges: List<ReplyTargetUnavailableRange> = emptyList(),
    isNgHidden: Boolean = false,
    onReveal: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    imageFailures: Map<String, ImageLoadFailureType> = emptyMap(),
    insidePostItemContainer: Boolean = false,
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
                    val previewContent: @Composable () -> Unit = {
                        if (previewType == ImageReplyPreviewType.REPLY_SOURCE) {
                            Column {
                            ReplyPreviewPostContent(
                                post = post,
                                hostNamespace = "thread-post-20",
                                previewType = ImageReplyPreviewType.REPLY_SOURCE,
                                onHide = null,
                                headerTextScale = headerTextScale,
                                bodyTextScale = bodyTextScale,
                                lineHeightEm = lineHeightEm,
                                onOpenPost = onOpenPost,
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
                            com.websarva.wings.android.slevo.ui.thread.state.replyPreviewFooterState(
                                overflowCount, overflowNumbers, emptyList(),
                            )?.let { footer ->
                                ReplyPreviewFooterContent(
                                    state = footer,
                                    modifier = Modifier.clickable { onOpenOverflow(overflowNumbers) },
                                )
                            }
                            }
                        } else {
                            ReplyTargetPreviewCard(
                                post = post,
                                hostNamespace = "thread-post-20",
                                overflowCount = overflowCount,
                                overflowNumbers = overflowNumbers,
                                unavailableRanges = unavailableRanges,
                                isNgHidden = isNgHidden,
                                onReveal = onReveal,
                                onHide = onHide,
                                headerTextScale = headerTextScale,
                                bodyTextScale = bodyTextScale,
                                lineHeightEm = lineHeightEm,
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
                    if (insidePostItemContainer) {
                        PostItemContainer(
                            modifier = Modifier.testTag("reply-preview-post-container"),
                            indentWidth = 0.dp,
                            dimmed = false,
                            isPressed = false,
                            scope = rememberCoroutineScope(),
                            onContentPressedChange = {},
                            onRequestMenu = {},
                            showMyPostIndicator = true,
                            onContentClick = {},
                            content = previewContent,
                        )
                    } else {
                        previewContent()
                    }
                }
            }
        }
    }
}
