package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.thread.state.PopupContentType
import com.websarva.wings.android.slevo.ui.thread.state.PopupInfo
import com.websarva.wings.android.slevo.ui.thread.state.PostReplyPreviewUiState
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetUnavailableRange
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

    /** 開示中と非表示中の両方で、フェード中の旧ヘッダーに操作が残る。 */
    @Test
    fun ngDisclosureKeepsOutgoingActionDuringFade() {
        val posts = listOf(post(1, "private body"), post(2, ">>1"))
        val previewState = derivePreview(posts, ngPostNumbers = setOf(1))
        val revealed = mutableStateOf(emptySet<Int>())
        composeRule.mainClock.autoAdvance = false
        try {
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
                                onReplyClick = {},
                                revealedReplyTargetNumbers = revealed.value,
                                onDisclosureChange = { number, show ->
                                    revealed.value = if (show) revealed.value + number else revealed.value - number
                                },
                            )
                        }
                    }
                }
            }
            composeRule.runOnIdle { revealed.value = setOf(1) }
            composeRule.mainClock.advanceTimeBy(64)
            composeRule.onNodeWithText("表示", useUnmergedTree = true).assertExists()
            composeRule.onNodeWithText("隠す", useUnmergedTree = true).assertExists()
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.runOnIdle { revealed.value = emptySet() }
            composeRule.mainClock.advanceTimeBy(64)
            composeRule.onNodeWithText("表示", useUnmergedTree = true).assertExists()
            composeRule.onNodeWithText("隠す", useUnmergedTree = true).assertExists()
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    /** 単一安価を重ねず一体化し、前後本文の順序と対象レス操作を維持する。 */
    @Test
    fun singleAnchorIsReplacedByOneInlineTargetCard() {
        // --- Fixture ---
        val posts = listOf(
            post(1, "target preview"),
            post(2, "before\n  >>1  \nafter"),
        )
        val previewState = derivePreview(posts)
        val openedPosts = mutableListOf<Int>()

        // --- Compose content ---
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

        // --- Layout and target action ---
        composeRule.onNodeWithTag("reply-preview-number-1", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("  name1  ID:id1", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText("レス", substring = true, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithText(">>1", substring = true, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("ほか", substring = true).assertCountEquals(0)
        val before = composeRule.onNodeWithText("before").fetchSemanticsNode().boundsInRoot
        val target = composeRule.onNodeWithText("target preview").fetchSemanticsNode().boundsInRoot
        val after = composeRule.onNodeWithText("after").fetchSemanticsNode().boundsInRoot
        assertTrue(before.bottom <= target.top)
        assertTrue(target.bottom <= after.top)

        composeRule.onNodeWithText("target preview").performClick()
        assertEquals(listOf(1), openedPosts)
    }

    /** 複数安価の元行をカードへ置換し、省略操作から全候補を返す。 */
    @Test
    fun multipleAnchorLineIsReplacedAndOverflowOpensAllCandidates() {
        // --- Fixture ---
        val posts = listOf(
            post(1, "target one"),
            post(2, "target two"),
            post(3, "before\n>>1,2\nafter"),
        )
        val previewState = derivePreview(posts)
        val openedNumbers = mutableListOf<List<Int>>()
        val openedTargets = mutableListOf<Int>()

        // --- Compose content ---
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

        // --- Replaced row and candidate list action ---
        composeRule.onAllNodesWithText(">>1,2", substring = true, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithTag("reply-preview-number-1", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("  name1  ID:id1", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("全2件を表示").performClick()

        assertEquals(listOf(listOf(1, 2)), openedNumbers)
        assertTrue(openedTargets.isEmpty())
    }

    /** 本文分割前後のURL・安価と通常本文の長押しを維持する。 */
    @Test
    fun keepsLinksAndLongPressAroundInsertedPreview() {
        // --- Fixture ---
        val beforeUrl = "https://example.com/before"
        val middleUrl = "https://example.com/middle"
        val afterUrl = "https://example.com/after"
        val posts = listOf(
            post(1, "target one"),
            post(2, "target two"),
            post(3, "target three"),
            post(4, "target four"),
            post(5, "$beforeUrl\n>>1 inline\n>>2\n$middleUrl\n>>3\n$afterUrl\n>>4 inline after\nplain tail"),
        )
        val urlClicks = mutableListOf<String>()
        val replyClicks = mutableListOf<Int>()
        var longPressCount = 0

        // --- Compose content ---
        composeRule.setContent {
            SlevoTheme {
                SharedTransitionLayout {
                    val sharedScope = this
                    AnimatedVisibility(visible = true) {
                        PostBodyHarness(
                            post = posts[4],
                            previewState = derivePreview(posts),
                            maxPostNumber = posts.size,
                            sharedTransitionScope = sharedScope,
                            animatedVisibilityScope = this,
                            onReplyClick = { replyClicks += it },
                            onUrlClick = { urlClicks += it },
                            onRequestMenu = { longPressCount++ },
                        )
                    }
                }
            }
        }

        // --- Links before the inserted preview ---
        val beforeText = composeRule.onNodeWithText(beforeUrl, substring = true, useUnmergedTree = true)
        val beforeBounds = beforeText.fetchSemanticsNode().boundsInRoot
        beforeText.performTouchInput {
            click(Offset(composeRule.density.density * 20f, beforeBounds.height * 0.25f))
        }
        val beforeAnchor = composeRule.onNodeWithText(">>1 inline", substring = true, useUnmergedTree = true)
        val beforeAnchorBounds = beforeAnchor.fetchSemanticsNode().boundsInRoot
        beforeAnchor.performTouchInput {
            click(Offset(composeRule.density.density * 10f, beforeAnchorBounds.height * 0.75f))
        }

        // --- Links and long press after the inserted preview ---
        val middleText = composeRule.onNodeWithText(middleUrl, substring = true, useUnmergedTree = true)
        val middleBounds = middleText.fetchSemanticsNode().boundsInRoot
        middleText.performTouchInput {
            click(Offset(composeRule.density.density * 20f, middleBounds.height * 0.25f))
        }
        val afterText = composeRule.onNodeWithText(afterUrl, substring = true, useUnmergedTree = true)
        val afterBounds = afterText.fetchSemanticsNode().boundsInRoot
        afterText.performTouchInput {
            click(Offset(composeRule.density.density * 20f, afterBounds.height * 0.25f))
        }
        val afterAnchor = composeRule.onNodeWithText(">>4 inline after", substring = true, useUnmergedTree = true)
        val afterAnchorBounds = afterAnchor.fetchSemanticsNode().boundsInRoot
        afterAnchor.performTouchInput {
            click(Offset(composeRule.density.density * 10f, afterAnchorBounds.height * 0.5f))
        }
        val plainText = composeRule.onNodeWithText("plain tail", substring = true, useUnmergedTree = true)
        val plainBounds = plainText.fetchSemanticsNode().boundsInRoot
        plainText.performTouchInput {
            longClick(Offset(composeRule.density.density * 8f, plainBounds.height * 0.85f))
        }

        // --- Expected callbacks ---
        assertEquals(listOf(beforeUrl, middleUrl, afterUrl), urlClicks)
        assertEquals(listOf(1, 4), replyClicks)
        assertEquals(1, longPressCount)
    }

    /** NG本文は非表示時に描画せず、同じ行で表示・隠すを切り替える。 */
    @Test
    fun ngTargetContentIsRevealedAndHiddenInPlace() {
        // --- Fixture and disclosure key ---
        val privateBody = "private NG reply body\nsecond NG line\nthird NG line\nfourth NG line"
        val privateImageUrl = "https://example.com/private-ng.jpg"
        var imageLoadStartCount = 0
        val posts = listOf(
            post(1, "$privateBody\n$privateImageUrl"),
            post(2, ">>1"),
        )
        val previewState = derivePreview(posts, ngPostNumbers = setOf(1))
        val disclosureChanges = mutableListOf<Pair<Int, Boolean>>()
        val openedPosts = mutableListOf<Int>()

        // --- Compose disclosure state ---
        composeRule.setContent {
            var revealedNumbers by remember { mutableStateOf(emptySet<Int>()) }
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
                            revealedReplyTargetNumbers = revealedNumbers,
                            onImageLoadStart = { imageLoadStartCount++ },
                            onDisclosureChange = { postNumber, revealed ->
                                disclosureChanges += postNumber to revealed
                                revealedNumbers = if (revealed) {
                                    revealedNumbers + postNumber
                                } else {
                                    revealedNumbers - postNumber
                                }
                            },
                        )
                    }
                }
            }
        }

        // --- Privacy and local toggle assertions ---
        composeRule.onNodeWithText("NGで非表示", substring = true).assertExists()
        composeRule.onAllNodesWithText(privateBody, substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("name1", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("ID:id1", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("date1", substring = true).assertCountEquals(0)
        assertEquals(0, imageLoadStartCount)
        val cardTag = "reply-preview-target-1"
        val headerTag = "reply-preview-header-1"
        val buttonTag = "reply-target-disclosure-button-1"
        val hiddenCardBounds = composeRule.onNodeWithTag(cardTag).fetchSemanticsNode().boundsInRoot
        val hiddenHeaderBounds = composeRule.onNodeWithTag(
            headerTag,
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val hiddenButtonBounds = composeRule.onNodeWithTag(buttonTag).fetchSemanticsNode().boundsInRoot
        assertTrue(hiddenButtonBounds.height >= composeRule.density.density * 48f)
        assertTrue(hiddenButtonBounds.left > hiddenHeaderBounds.left)
        composeRule.onNodeWithText("表示").performClick()
        composeRule.onNodeWithText("private NG reply body", substring = true).assertExists()
        composeRule.onNodeWithText("name1  ID:id1", substring = true).assertExists()
        composeRule.onAllNodesWithText("date1", substring = true).assertCountEquals(0)
        val revealedBodyBounds = composeRule.onNodeWithTag(
            "reply-preview-body-1",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        assertTrue(revealedBodyBounds.height < composeRule.density.density * 64f)
        val revealedCardBounds = composeRule.onNodeWithTag(cardTag).fetchSemanticsNode().boundsInRoot
        assertEquals(
            hiddenHeaderBounds,
            composeRule.onNodeWithTag(headerTag, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot,
        )
        assertEquals(hiddenButtonBounds, composeRule.onNodeWithTag(buttonTag).fetchSemanticsNode().boundsInRoot)
        assertTrue(revealedCardBounds.height > hiddenCardBounds.height)
        composeRule.onNodeWithText("隠す").performClick()
        composeRule.onAllNodesWithText(privateBody, substring = true).assertCountEquals(0)
        assertEquals(listOf(1 to true, 1 to false), disclosureChanges)
        assertTrue(openedPosts.isEmpty())
    }

    /** Inlineと返信先popupのどちらから操作しても同じレスの開示状態を共有する。 */
    @Test
    fun ngTargetDisclosureIsSharedBetweenInlinePreviewAndPopup() {
        // --- Fixture ---
        val privateBody = "shared private reply body\nsecond line\nthird line\nfourth line"
        val posts = listOf(post(1, privateBody), post(2, ">>1"))
        val previewState = derivePreview(posts, ngPostNumbers = setOf(1))
        val popupInfo = PopupInfo(
            popupId = 99L,
            postNumbers = listOf(1),
            offset = IntOffset.Zero,
            contentType = PopupContentType.REPLY_TARGETS,
        )
        val changes = mutableListOf<Pair<Int, Boolean>>()

        // --- Shared disclosure state and both renderers ---
        composeRule.setContent {
            var revealedNumbers by remember { mutableStateOf(emptySet<Int>()) }
            val onDisclosureChange: (Int, Boolean) -> Unit = { postNumber, revealed ->
                changes += postNumber to revealed
                // 同じ番号集合をインラインとポップアップの描画元にする。
                revealedNumbers = if (revealed) {
                    revealedNumbers + postNumber
                } else {
                    revealedNumbers - postNumber
                }
            }
            SlevoTheme {
                SharedTransitionLayout {
                    val transitionScope = this
                    AnimatedVisibility(visible = true) {
                        val visibilityScope = this
                        Column {
                            PostBodyHarness(
                                post = posts[1],
                                previewState = previewState,
                                sharedTransitionScope = transitionScope,
                                animatedVisibilityScope = visibilityScope,
                                onReplyClick = {},
                                revealedReplyTargetNumbers = revealedNumbers,
                                onDisclosureChange = onDisclosureChange,
                            )
                            ReplyPopup(
                                popupStack = listOf(popupInfo),
                                posts = posts,
                                replySourceMap = emptyMap(),
                                idCountMap = emptyMap(),
                                idIndexList = emptyList(),
                                ngPostNumbers = setOf(1),
                                myPostNumbers = emptySet(),
                                revealedReplyTargetNumbers = revealedNumbers,
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
                                onAddPopupForReplyFrom = { _, _ -> },
                                onAddPopupForReplyNumber = { _, _ -> },
                                onReplyTargetDisclosureChange = onDisclosureChange,
                                onAddPopupForId = { _, _ -> },
                                onPopupSizeChange = { _, _ -> },
                                onClose = {},
                                sharedTransitionScope = transitionScope,
                                animatedVisibilityScope = visibilityScope,
                            )
                        }
                    }
                }
            }
        }

        // --- Cross-display state transitions ---
        val disclosureButtonTag = "reply-target-disclosure-button-1"
        composeRule.onAllNodesWithTag(disclosureButtonTag).assertCountEquals(2)
        composeRule.onAllNodesWithTag(disclosureButtonTag)[0].performClick()
        composeRule.onAllNodesWithText("shared private reply body", substring = true)
            .assertCountEquals(2)
        composeRule.onAllNodesWithTag(disclosureButtonTag).assertCountEquals(2)

        // The popup button updates the same number set consumed by the inline preview.
        composeRule.onAllNodesWithTag(disclosureButtonTag)[1].performClick()
        composeRule.onAllNodesWithText("shared private reply body", substring = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("表示").assertCountEquals(2)
        assertEquals(listOf(1 to true, 1 to false), changes)
    }

    /** 未取得範囲だけの行はインライン表示し、返信先一覧を開く操作を持たない。 */
    @Test
    fun unavailableOnlyInlineTargetDoesNotOpenPopup() {
        val posts = listOf(
            post(1, "ordinary post"),
            post(2, ">>3-2147483647"),
        )
        val openedNumbers = mutableListOf<List<Int>>()

        composeRule.setContent {
            SlevoTheme {
                SharedTransitionLayout {
                    val sharedScope = this
                    AnimatedVisibility(visible = true) {
                        PostBodyHarness(
                            post = posts[1],
                            previewState = derivePreview(posts),
                            sharedTransitionScope = sharedScope,
                            animatedVisibilityScope = this,
                            onReplyClick = {},
                            onReplyNumbersClick = { openedNumbers += it },
                            maxPostNumber = posts.size,
                        )
                    }
                }
            }
        }

        val unavailableRow = composeRule.onNodeWithText(
            "レス3〜2147483647は未取得です",
        )
        unavailableRow.assertExists()
        unavailableRow.assertHasNoClickAction()
        assertTrue(openedNumbers.isEmpty())
    }

    /** 取得済み千件はそのまま保持し、未取得範囲だけを別表示する。 */
    @Test
    fun inlineCardShowsAllFetchedCandidatesWithoutUnavailableRange() {
        val posts = (1..1_000).map { number ->
            post(number, if (number == 1_000) ">>1-1000" else "target$number")
        }
        val previewState = derivePreview(posts)
        val target = previewState.replyTargets.single()

        assertEquals(1_000, target.candidates.size)
        assertEquals(emptyList<ReplyTargetUnavailableRange>(), target.unavailableRanges)
        composeRule.setContent {
            SlevoTheme {
                SharedTransitionLayout {
                    val sharedScope = this
                    AnimatedVisibility(visible = true) {
                        PostBodyHarness(
                            post = posts.last(),
                            previewState = previewState,
                            sharedTransitionScope = sharedScope,
                            animatedVisibilityScope = this,
                            onReplyClick = {},
                            maxPostNumber = posts.size,
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("全1000件を表示").assertExists()
        composeRule.onAllNodesWithText("参照先が上限を超えたため、一部を省略しています").assertCountEquals(0)
    }

    /** 旧上限を超える取得済み候補を扱い、未取得部分を番号列挙せず表示する。 */
    @Test
    fun inlineCardShowsUnavailableRangeAfterAllFetchedCandidates() {
        val posts = (1..1_001).map { number ->
            post(number, if (number == 1_001) ">>1-2147483647" else "target$number")
        }
        val previewState = derivePreview(posts)
        val target = previewState.replyTargets.single()

        assertEquals(1_001, target.candidates.size)
        assertEquals(
            listOf(ReplyTargetUnavailableRange(1_002, Int.MAX_VALUE)),
            target.unavailableRanges,
        )
        composeRule.setContent {
            SlevoTheme {
                SharedTransitionLayout {
                    val sharedScope = this
                    AnimatedVisibility(visible = true) {
                        PostBodyHarness(
                            post = posts.last(),
                            previewState = previewState,
                            sharedTransitionScope = sharedScope,
                            animatedVisibilityScope = this,
                            onReplyClick = {},
                            maxPostNumber = posts.size,
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("全1001件を表示").assertExists()
        composeRule.onNodeWithText("レス1002〜2147483647は未取得です").assertExists()
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
    revealedReplyTargetNumbers: Set<Int> = emptySet(),
    onDisclosureChange: (Int, Boolean) -> Unit = { _, _ -> },
    onImageLoadStart: (String) -> Unit = {},
    maxPostNumber: Int = 3,
    onRequestMenu: () -> Unit = {},
    onUrlClick: (String) -> Unit = {},
) {
    // --- Component inputs and event callbacks ---
    val scope = rememberCoroutineScope()

    // --- Post body composition ---
    PostItemBody(
        post = post,
        bodyTextStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        headerTextScale = 1f,
        lineHeightEm = 1.25f,
        searchQuery = "",
        pressedUrl = null,
        pressedReply = null,
        scope = scope,
        onPressedUrlChange = {},
        onPressedReplyChange = {},
        onContentPressedChange = {},
        onRequestMenu = onRequestMenu,
        onReplyClick = onReplyClick,
        onReplyNumbersClick = onReplyNumbersClick,
        revealedReplyTargetNumbers = revealedReplyTargetNumbers,
        onReplyTargetDisclosureChange = onDisclosureChange,
        maxPostNumber = maxPostNumber,
        replyPreviewState = previewState,
        transitionNamespace = "thread-post-${post.body.content.hashCode()}",
        onImageClick = { _, _, _, _ -> },
        onImageLongPress = { _, _ -> },
        imageLoadFailureByUrl = emptyMap<String, ImageLoadFailureType>(),
        onImageLoadStart = onImageLoadStart,
        onImageLoadError = { _, _ -> },
        onImageLoadSuccess = {},
        onImageRetry = {},
        enableSharedElement = false,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onUrlClick = onUrlClick,
        onThreadUrlClick = {},
        onBodyClick = {},
    )
}

/** 小さな投稿データを作って純粋transformerへ渡す。 */
private fun derivePreview(
    posts: List<ThreadPostUiModel>,
    ngPostNumbers: Set<Int> = emptySet(),
): PostReplyPreviewUiState =
    ThreadReplyPreviewTransformer.derive(
        posts = posts,
        visibleReplySourceMap = emptyMap(),
        ngPostNumbers = ngPostNumbers,
        defaultName = "",
    ).getValue(posts.size)

/** テスト対象の投稿番号と本文から、表示に必要なモデルを構成する。 */
private fun post(number: Int, content: String): ThreadPostUiModel = ThreadPostUiModel(
    header = ThreadPostUiModel.Header(
        name = "name$number",
        email = "",
        date = "date$number",
        id = "id$number",
    ),
    body = ThreadPostUiModel.Body(content = content),
)
