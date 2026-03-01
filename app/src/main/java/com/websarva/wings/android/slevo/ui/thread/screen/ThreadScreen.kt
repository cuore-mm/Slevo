package com.websarva.wings.android.slevo.ui.thread.screen

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.GestureSettings
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.ui.common.GestureHintOverlay
import com.websarva.wings.android.slevo.ui.common.SlevoLazyColumnScrollbar
import com.websarva.wings.android.slevo.ui.common.interaction.ObserveGestureHintInvalidResetEffect
import com.websarva.wings.android.slevo.ui.common.interaction.executeGestureScrollAction
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.buildImageViewerRoute
import com.websarva.wings.android.slevo.ui.navigation.navigateToThread
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.thread.components.MomentumBar
import com.websarva.wings.android.slevo.ui.thread.res.PostDialogTarget
import com.websarva.wings.android.slevo.ui.thread.screen.components.threadPostListContent
import com.websarva.wings.android.slevo.ui.thread.screen.effects.ObserveAutoScrollEffect
import com.websarva.wings.android.slevo.ui.thread.screen.effects.ObserveLastReadEffect
import com.websarva.wings.android.slevo.ui.thread.screen.effects.ObservePopupVisibilityEffect
import com.websarva.wings.android.slevo.ui.thread.screen.effects.rememberBottomRefreshHandle
import com.websarva.wings.android.slevo.ui.thread.state.ThreadLoadingSource
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.thread.state.ThreadUiState
import com.websarva.wings.android.slevo.ui.util.GestureHint
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import com.websarva.wings.android.slevo.ui.util.detectDirectionalGesture
import kotlinx.coroutines.launch

/**
 * スレッド画面を構成し、投稿一覧と各種オーバーレイを表示する。
 *
 * 投稿メニューやダイアログは上位のホストへ委譲する。
 * 画像サムネイルの読み込み状態もイベントとして上位へ渡す。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ThreadScreen(
    modifier: Modifier = Modifier,
    uiState: ThreadUiState,
    listState: LazyListState = rememberLazyListState(),
    navController: NavHostController,
    tabsViewModel: TabsViewModel? = null,
    showBottomBar: (() -> Unit)? = null,
    onAutoScrollBottom: () -> Unit = {},
    onBottomRefresh: () -> Unit = {},
    onLastRead: (Int) -> Unit = {},
    gestureSettings: GestureSettings = GestureSettings.DEFAULT,
    onGestureAction: (GestureAction) -> Unit = {},
    onPopupVisibilityChange: (Boolean) -> Unit = {},
    onRequestPostMenu: (PostDialogTarget) -> Unit = {},
    onRequestTextMenu: (text: String, type: NgType) -> Unit = { _, _ -> },
    onRequestTreePopup: (postNumber: Int, baseOffset: IntOffset) -> Unit = { _, _ -> },
    onAddPopupForReplyFrom: (replyNumbers: List<Int>, baseOffset: IntOffset) -> Unit = { _, _ -> },
    onAddPopupForReplyNumber: (postNumber: Int, baseOffset: IntOffset) -> Unit = { _, _ -> },
    onAddPopupForId: (id: String, baseOffset: IntOffset) -> Unit = { _, _ -> },
    onImageLongPress: (String, List<String>) -> Unit = { _, _ -> },
    onImageLoadStart: (String) -> Unit = {},
    onImageLoadError: (String, ImageLoadFailureType) -> Unit = { _, _ -> },
    onImageLoadSuccess: (String) -> Unit = {},
    onImageRetry: (String) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    // 投稿一覧（nullの場合は空リスト）
    val posts = uiState.posts ?: emptyList()
    // 表示対象の投稿（フィルタ済み）
    val visiblePosts = uiState.visiblePosts
    // 表示用の投稿データ（ReplyInfo型）
    val displayPosts = visiblePosts.map { it.post }
    // 各投稿の返信数
    val replyCounts = uiState.replyCounts
    // 新着バーを表示するインデックス
    val firstAfterIndex = uiState.firstAfterIndex
    // ポップアップ表示用のスタック
    val popupStack = uiState.popupStack
    // ポップアップ表示中はリスト側の共有トランジションを無効化する。
    val enableListSharedElements = popupStack.isEmpty()
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val haptic = LocalHapticFeedback.current

    // --- ナビゲーション ---
    val onUrlClick: (String) -> Unit = { url -> uriHandler.openUri(url) }
    val onThreadUrlClick: (AppRoute.Thread) -> Unit = { route ->
        navController.navigateToThread(
            route = route,
            tabsViewModel = tabsViewModel,
        )
    }
    val onImageClick: (String, List<String>, Int, String) -> Unit =
        { _, imageUrls, tappedIndex, transitionNamespace ->
            val route = buildImageViewerRoute(
                imageUrls = imageUrls,
                tappedIndex = tappedIndex,
                transitionNamespace = transitionNamespace,
            )
            route?.let(navController::navigate)
        }
    val onRequestMenu: (PostDialogTarget) -> Unit = onRequestPostMenu
    val onShowTextMenu: (String, NgType) -> Unit = onRequestTextMenu

    ObservePopupVisibilityEffect(
        popupCount = popupStack.size,
        onPopupVisibilityChange = onPopupVisibilityChange,
    )

    ObserveLastReadEffect(
        listState = listState,
        visiblePosts = visiblePosts,
        sortType = uiState.sortType,
        totalPostCount = posts.size,
        onLastRead = onLastRead,
    )

    ObserveAutoScrollEffect(
        listState = listState,
        isAutoScroll = uiState.isAutoScroll,
        fallbackItemCount = visiblePosts.size,
        onAutoScrollBottom = onAutoScrollBottom,
    )

    val bottomRefreshHandle = rememberBottomRefreshHandle(
        listState = listState,
        postCount = posts.size,
        hapticFeedback = haptic,
        onBottomRefresh = onBottomRefresh,
        onLastRead = onLastRead,
    )

    val showScrollbar by remember(listState) {
        derivedStateOf { listState.canScrollForward || listState.canScrollBackward }
    }

    var gestureHint by remember { mutableStateOf<GestureHint>(GestureHint.Hidden) }
    ObserveGestureHintInvalidResetEffect(
        isInvalid = gestureHint is GestureHint.Invalid,
        onReset = { gestureHint = GestureHint.Hidden },
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .detectDirectionalGesture(
                enabled = gestureSettings.isEnabled,
                onGestureProgress = { direction ->
                    gestureHint = direction?.let {
                        GestureHint.Direction(it, gestureSettings.assignments[it])
                    } ?: GestureHint.Hidden
                },
                onGestureInvalid = {
                    gestureHint = GestureHint.Invalid
                },
            ) { direction ->
                val action = gestureSettings.assignments[direction]
                if (action == null) {
                    gestureHint = GestureHint.Direction(direction, null)
                    return@detectDirectionalGesture
                }
                gestureHint = GestureHint.Hidden
                if (action == GestureAction.ToTop || action == GestureAction.ToBottom) {
                    coroutineScope.launch {
                        executeGestureScrollAction(
                            action = action,
                            listState = listState,
                            fallbackItemCount = visiblePosts.size,
                            showBottomBar = showBottomBar,
                        )
                    }
                } else {
                    onGestureAction(action)
                }
            }
    ) {
        val lazyColumnContent: LazyListScope.() -> Unit = {
        threadPostListContent(
            uiState = uiState,
            visiblePosts = visiblePosts,
            firstAfterIndex = firstAfterIndex,
            popupStack = popupStack,
            enableSharedElements = enableListSharedElements,
            onUrlClick = onUrlClick,
            onThreadUrlClick = onThreadUrlClick,
            onImageClick = onImageClick,
            onImageLongPress = onImageLongPress,
            onImageLoadStart = onImageLoadStart,
            onImageLoadError = onImageLoadError,
            onImageLoadSuccess = onImageLoadSuccess,
            onImageRetry = onImageRetry,
            onRequestMenu = onRequestMenu,
                onShowTextMenu = onShowTextMenu,
                onRequestTreePopup = onRequestTreePopup,
                onAddPopupForReplyFrom = onAddPopupForReplyFrom,
                onAddPopupForReplyNumber = onAddPopupForReplyNumber,
                onAddPopupForId = onAddPopupForId,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }

        if (uiState.showMinimapScrollbar) {
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .nestedScroll(bottomRefreshHandle.nestedScrollConnection),
                    state = listState,
                    content = lazyColumnContent
                )
                // 中央の区切り線
                VerticalDivider()

                // 右側: 固定の勢いバー
                MomentumBar(
                    modifier = Modifier
                        .width(24.dp)
                        .fillMaxHeight(),
                    posts = displayPosts,
                    replyCounts = replyCounts,
                    lazyListState = listState,
                    firstAfterIndex = firstAfterIndex,
                    myPostNumbers = uiState.myPostNumbers
                )
            }
        } else {
            SlevoLazyColumnScrollbar(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                enabled = showScrollbar,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(bottomRefreshHandle.nestedScrollConnection),
                    state = listState,
                    content = lazyColumnContent
                )
            }
        }

        ThreadBottomRefreshIndicator(
            isRefreshing = uiState.isLoading &&
                uiState.loadingSource == ThreadLoadingSource.BOTTOM_PULL,
            overscroll = bottomRefreshHandle.overscroll,
            refreshThresholdPx = bottomRefreshHandle.refreshThresholdPx,
        )
        if (gestureSettings.showActionHints) {
            GestureHintOverlay(state = gestureHint)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Preview(showBackground = true)
@Composable
fun ThreadScreenPreview() {
    val previewPosts = listOf(
        ThreadPostUiModel(
            header = ThreadPostUiModel.Header(
                name = "名無しさん",
                email = "sage",
                date = "2025/07/09(水) 19:40:25.769",
                id = "test1",
                beLoginId = "12345",
                beRank = "DIA(20000)",
                beIconUrl = "http://img.2ch.net/ico/hikky2.gif",
            ),
            body = ThreadPostUiModel.Body(
                content = "これはテスト投稿です。"
            ),
        ),
        ThreadPostUiModel(
            header = ThreadPostUiModel.Header(
                name = "名無しさん",
                email = "sage",
                date = "2025/07/09(水) 19:41:00.123",
                id = "test2",
            ),
            body = ThreadPostUiModel.Body(
                content = "別のテスト投稿です。"
            ),
        ),
    )
    val uiState = ThreadUiState(
        posts = previewPosts,
        boardInfo = BoardInfo(
            0L,
            "board",
            "https://example.com/"
        ),
        idCountMap = previewPosts.groupingBy { it.header.id }.eachCount(),
        idIndexList = previewPosts.mapIndexed { i, _ -> i + 1 },
        replySourceMap = emptyMap()
    )
    SharedTransitionLayout {
        AnimatedVisibility(visible = true) {
            ThreadScreen(
                uiState = uiState,
                navController = NavHostController(LocalContext.current),
                onAutoScrollBottom = {},
                onBottomRefresh = {},
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this
            )
        }
    }
}
