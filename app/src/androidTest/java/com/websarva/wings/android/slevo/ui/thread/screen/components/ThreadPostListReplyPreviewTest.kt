package com.websarva.wings.android.slevo.ui.thread.screen.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.websarva.wings.android.slevo.ui.thread.state.DisplayPost
import com.websarva.wings.android.slevo.ui.thread.state.PostDisplayRole
import com.websarva.wings.android.slevo.ui.thread.state.ThreadListItem
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import com.websarva.wings.android.slevo.ui.thread.state.ThreadUiState
import com.websarva.wings.android.slevo.ui.thread.viewmodel.ThreadReplyPreviewTransformer
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 本体一覧の返信プレビューは番号順に限定され、ツリー順へ漏れないことを検証する。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(AndroidJUnit4::class)
class ThreadPostListReplyPreviewTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** ソート種別を変更してもプレビューstateをツリー行へ渡さない。 */
    @Test
    fun previewIsRenderedOnlyForNumberSortedPostRows() {
        val sortType = mutableStateOf(ThreadSortType.NUMBER)
        composeRule.setContent {
            SlevoTheme {
                SharedTransitionLayout {
                    val sharedScope = this
                    AnimatedVisibility(visible = true) {
                        val visibilityScope = this
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            threadPostListContent(
                                uiState = threadUiState(sortType.value),
                                visiblePostRows = listOf(postRow()),
                                firstAfterIndex = -1,
                                popupStack = emptyList(),
                                containerWidth = 360.dp,
                                enableSharedElements = false,
                                onUrlClick = {},
                                onThreadUrlClick = {},
                                onImageClick = { _, _, _, _ -> },
                                onImageLongPress = { _, _ -> },
                                onImageLoadStart = {},
                                onImageLoadError = { _, _ -> },
                                onImageLoadSuccess = {},
                                onImageRetry = {},
                                onRequestMenu = {},
                                onShowTextMenu = { _, _ -> },
                                onRequestTreePopup = { _, _ -> },
                                onAddPopupForReplyFrom = { _, _ -> },
                                onAddPopupForReplyNumber = { _, _ -> },
                                onAddPopupForId = { _, _ -> },
                                sharedTransitionScope = sharedScope,
                                animatedVisibilityScope = visibilityScope,
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("返信先本文").assertExists()
        composeRule.runOnIdle { sortType.value = ThreadSortType.TREE }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("返信先本文").assertDoesNotExist()
    }
}

/** 指定ソート種別で一件の返信プレビューを持つ画面状態を作る。 */
private fun threadUiState(sortType: ThreadSortType): ThreadUiState {
    val posts = threadPosts()
    return ThreadUiState(
        posts = posts,
        sortType = sortType,
        replyPreviewByPostNumber = mapOf(
            2 to ThreadReplyPreviewTransformer.derive(
                posts = posts,
                visibleReplySourceMap = emptyMap(),
                ngPostNumbers = emptySet(),
                defaultName = "",
            ).getValue(2),
        ),
        imageLoadFailureByUrl = emptyMap<String, ImageLoadFailureType>(),
    )
}

/** レス2を固定keyで描画する行を作る。 */
private fun postRow() = ThreadListItem.PostRow(
    displayPost = DisplayPost(
        num = 2,
        post = threadPosts()[1],
        dimmed = false,
        isAfter = false,
        depth = 0,
        rootNumber = 1,
    ),
    groupIndex = 0,
    role = PostDisplayRole.NORMAL,
    occurrenceIndex = 0,
    stableKey = "post-2",
)

/** 返信先を持つ投稿番号1・2のテストfixtureを作る。 */
private fun threadPosts() = listOf(
    ThreadPostUiModel(
        header = ThreadPostUiModel.Header(name = "", email = "", date = "", id = "id1"),
        body = ThreadPostUiModel.Body(content = "返信先本文"),
    ),
    ThreadPostUiModel(
        header = ThreadPostUiModel.Header(name = "投稿者", email = "", date = "", id = "id2"),
        body = ThreadPostUiModel.Body(content = ">>1"),
    ),
)
