package com.websarva.wings.android.slevo.ui.thread.screen.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material3.HorizontalDivider
import com.websarva.wings.android.slevo.data.model.DEFAULT_THREAD_LINE_HEIGHT
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.ui.thread.components.NewArrivalBar
import com.websarva.wings.android.slevo.ui.thread.res.PostDialogTarget
import com.websarva.wings.android.slevo.ui.thread.res.PostItem
import com.websarva.wings.android.slevo.ui.thread.state.DisplayPost
import com.websarva.wings.android.slevo.ui.thread.state.PopupInfo
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import com.websarva.wings.android.slevo.ui.thread.state.ThreadUiState
import com.websarva.wings.android.slevo.ui.thread.viewmodel.buildThreadListItemKey
import com.websarva.wings.android.slevo.ui.common.transition.ImageSharedTransitionKeyFactory
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.thread.screen.resolvePopupBaseOffset
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import kotlin.math.min

/**
 * スレッド投稿一覧の `LazyColumn` コンテンツを構築する。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun LazyListScope.threadPostListContent(
    uiState: ThreadUiState,
    visiblePosts: List<DisplayPost>,
    firstAfterIndex: Int,
    popupStack: List<PopupInfo>,
    enableSharedElements: Boolean,
    onUrlClick: (String) -> Unit,
    onThreadUrlClick: (AppRoute.Thread) -> Unit,
    onImageClick: (String, List<String>, Int, String) -> Unit,
    onImageLongPress: (String, List<String>) -> Unit,
    onImageLoadError: (String, ImageLoadFailureType) -> Unit,
    onImageLoadSuccess: (String) -> Unit,
    onImageRetry: (String) -> Unit,
    onRequestMenu: (PostDialogTarget) -> Unit,
    onShowTextMenu: (String, NgType) -> Unit,
    onRequestTreePopup: (postNumber: Int, baseOffset: IntOffset) -> Unit,
    onAddPopupForReplyFrom: (replyNumbers: List<Int>, baseOffset: IntOffset) -> Unit,
    onAddPopupForReplyNumber: (postNumber: Int, baseOffset: IntOffset) -> Unit,
    onAddPopupForId: (id: String, baseOffset: IntOffset) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    // --- Header divider ---
    if (visiblePosts.isNotEmpty()) {
        val firstIndent = if (uiState.sortType == ThreadSortType.TREE) {
            visiblePosts.first().depth
        } else {
            0
        }
        item(key = "thread_header_divider") {
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp * firstIndent))
        }
    }

    // --- Post rows ---
    itemsIndexed(
        items = visiblePosts,
        key = { idx, display -> buildThreadListItemKey(idx, display) },
    ) { idx, display ->
        val postNum = display.num
        val post = display.post
        val index = postNum - 1
        val indent = if (uiState.sortType == ThreadSortType.TREE) {
            display.depth
        } else {
            0
        }
        val nextIndent = if (idx + 1 < visiblePosts.size && uiState.sortType == ThreadSortType.TREE) {
            visiblePosts[idx + 1].depth
        } else {
            0
        }
        val itemOffset = remember { mutableStateOf(IntOffset.Zero) }
        val transitionNamespace = remember(postNum) {
            ImageSharedTransitionKeyFactory.threadPostNamespace(postNum)
        }

        Column {
            if (firstAfterIndex != -1 && idx == firstAfterIndex) {
                NewArrivalBar()
            }
            PostItem(
                modifier = Modifier.onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    itemOffset.value = IntOffset(pos.x.toInt(), pos.y.toInt())
                },
                post = post,
                postNum = postNum,
                idIndex = uiState.idIndexList.getOrElse(index) { 1 },
                idTotal = if (post.header.id.isBlank()) 1 else uiState.idCountMap[post.header.id] ?: 1,
                headerTextScale = if (uiState.isIndividualTextScale) {
                    uiState.headerTextScale
                } else {
                    uiState.textScale * 0.85f
                },
                bodyTextScale = if (uiState.isIndividualTextScale) {
                    uiState.bodyTextScale
                } else {
                    uiState.textScale
                },
                lineHeight = if (uiState.isIndividualTextScale) {
                    uiState.lineHeight
                } else {
                    DEFAULT_THREAD_LINE_HEIGHT
                },
                indentLevel = indent,
                replyFromNumbers = uiState.replySourceMap[postNum] ?: emptyList(),
                isMyPost = postNum in uiState.myPostNumbers,
                dimmed = display.dimmed,
                searchQuery = uiState.searchQuery,
                onUrlClick = onUrlClick,
                onThreadUrlClick = onThreadUrlClick,
                transitionNamespace = transitionNamespace,
                onImageClick = onImageClick,
                onImageLongPress = onImageLongPress,
                imageLoadFailureByUrl = uiState.imageLoadFailureByUrl,
                onImageLoadError = onImageLoadError,
                onImageLoadSuccess = onImageLoadSuccess,
                onImageRetry = onImageRetry,
                enableSharedElement = enableSharedElements,
                onRequestMenu = onRequestMenu,
                onShowTextMenu = onShowTextMenu,
                onContentClick = {
                    val offset = resolvePopupBaseOffset(itemOffset.value, popupStack)
                    onRequestTreePopup(postNum, offset)
                },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onReplyFromClick = { numbers ->
                    val offset = resolvePopupBaseOffset(itemOffset.value, popupStack)
                    onAddPopupForReplyFrom(numbers, offset)
                },
                onReplyClick = { num ->
                    val offset = resolvePopupBaseOffset(itemOffset.value, popupStack)
                    onAddPopupForReplyNumber(num, offset)
                },
                onIdClick = { id ->
                    val offset = resolvePopupBaseOffset(itemOffset.value, popupStack)
                    onAddPopupForId(id, offset)
                },
            )
            HorizontalDivider(
                modifier = Modifier.padding(
                    start = 16.dp * min(indent, nextIndent),
                ),
            )
        }
    }
}
