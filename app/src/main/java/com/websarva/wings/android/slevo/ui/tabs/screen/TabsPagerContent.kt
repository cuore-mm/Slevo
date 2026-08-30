package com.websarva.wings.android.slevo.ui.tabs.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.TabPage
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.tabs.component.TabListAnimationDefaults
import com.websarva.wings.android.slevo.ui.tabs.component.TabListLayoutDefaults
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo

/**
 * タブ一覧ページで切り替える表示状態を表す。
 *
 * 通常リスト、検索結果リスト、検索結果なし表示を単一状態として扱い、
 * 同じ `AnimatedContent` で切り替えるために利用する。
 */
private enum class TabListDisplayState {
    Normal,
    SearchResults,
    SearchEmpty,
}

/**
 * タブ一覧のページャーを提供し、板/スレ一覧を切り替えて表示する。
 */
@Composable
fun TabsPagerContent(
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    tabSessionStore: TabSessionStore,
    navController: NavHostController,
    closeDrawer: () -> Unit,
    listContentPadding: PaddingValues = PaddingValues(0.dp),
    isShowingSearchResults: Boolean,
    isSearchMode: Boolean,
    boardNormalListState: LazyListState,
    boardSearchListState: LazyListState,
    threadNormalListState: LazyListState,
    threadSearchListState: LazyListState,
    openBoardTabs: List<BoardTabInfo>,
    filteredBoardTabs: List<BoardTabInfo>,
    openThreadTabs: List<ThreadTabInfo>,
    filteredThreadTabs: List<ThreadTabInfo>,
    newResCounts: Map<String, Int>,
    selectedBoardTab: BoardTabInfo?,
    selectedThreadTab: ThreadTabInfo?,
    isSelectionMode: Boolean = false,
    selectedBoardTabKeys: Set<String> = emptySet(),
    selectedThreadTabIds: Set<ThreadId> = emptySet(),
    removingBoardTabKeys: Set<String>,
    removingThreadTabKeys: Set<String>,
    onCloseBoardTab: (BoardTabInfo) -> Unit,
    onCloseThreadTab: (ThreadTabInfo) -> Unit,
    onBoardTabSelectionToggle: (String) -> Unit = {},
    onThreadTabSelectionToggle: (ThreadId) -> Unit = {},
    onSwipeDeleteBoardTab: (BoardTabInfo) -> Unit,
    onSwipeDeleteThreadTab: (ThreadTabInfo) -> Unit,
    onBoardTabLongPressed: (BoardTabInfo, IntRect) -> Unit,
    onBoardTabLongPressMoved: (Offset) -> Unit,
    onBoardTabLongPressReleased: () -> Unit,
    onThreadTabLongPressed: (ThreadTabInfo, IntRect) -> Unit,
    onThreadTabLongPressMoved: (Offset) -> Unit,
    onThreadTabLongPressReleased: () -> Unit,
    onBoardTabReorderStarted: (BoardTabInfo) -> Unit,
    onBoardTabReorderMoved: (BoardTabInfo, BoardTabInfo) -> Unit,
    onBoardTabReorderFinished: (BoardTabInfo) -> Unit,
    onBoardTabReorderCancelled: (BoardTabInfo) -> Unit,
    onBoardTabReorderAccessibilityMove: (BoardTabInfo, Int) -> Boolean,
    onThreadTabReorderStarted: (ThreadTabInfo) -> Unit,
    onThreadTabReorderMoved: (ThreadTabInfo, ThreadTabInfo) -> Unit,
    onThreadTabReorderFinished: (ThreadTabInfo) -> Unit,
    onThreadTabReorderCancelled: (ThreadTabInfo) -> Unit,
    onThreadTabReorderAccessibilityMove: (ThreadTabInfo, Int) -> Boolean,
    onClearNewResCount: (ThreadId) -> Unit,
    isInLongPressSelectionMode: Boolean = false,
    currentScreenRoute: AppRoute? = null,
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false,
    ) { page ->
        when (TabPage.fromIndex(page)) {
            TabPage.BOARD -> AnimatedListContent(
                displayState = when {
                    !isShowingSearchResults -> TabListDisplayState.Normal
                    filteredBoardTabs.isEmpty() -> TabListDisplayState.SearchEmpty
                    else -> TabListDisplayState.SearchResults
                },
                normalContent = {
                    OpenBoardsList(
                        openTabs = openBoardTabs,
                        onCloseClick = onCloseBoardTab,
                        onSwipeDelete = onSwipeDeleteBoardTab,
                        navController = navController,
                        closeDrawer = closeDrawer,
                        contentPadding = listContentPadding,
                        listState = boardNormalListState,
                        selectedBoardTab = selectedBoardTab,
                        isSelectionMode = isSelectionMode,
                        selectedBoardTabKeys = selectedBoardTabKeys,
                        onBoardTabSelectionToggle = onBoardTabSelectionToggle,
                        removingKeys = removingBoardTabKeys,
                        onBoardTabLongPressed = onBoardTabLongPressed,
                        onBoardTabLongPressMoved = onBoardTabLongPressMoved,
                        onBoardTabLongPressReleased = onBoardTabLongPressReleased,
                        tabSessionStore = tabSessionStore,
                        isInLongPressSelectionMode = isInLongPressSelectionMode,
                        isReorderEnabled = !isSearchMode && !isSelectionMode,
                        onReorderStarted = onBoardTabReorderStarted,
                        onReorderMoved = onBoardTabReorderMoved,
                        onReorderFinished = onBoardTabReorderFinished,
                        onReorderCancelled = onBoardTabReorderCancelled,
                        onReorderAccessibilityMove = onBoardTabReorderAccessibilityMove,
                        currentScreenRoute = currentScreenRoute,
                    )
                },
                searchResultContent = {
                    OpenBoardsList(
                        openTabs = filteredBoardTabs,
                        onCloseClick = onCloseBoardTab,
                        onSwipeDelete = onSwipeDeleteBoardTab,
                        navController = navController,
                        closeDrawer = closeDrawer,
                        contentPadding = listContentPadding,
                        listState = boardSearchListState,
                        selectedBoardTab = selectedBoardTab,
                        isSelectionMode = isSelectionMode,
                        selectedBoardTabKeys = selectedBoardTabKeys,
                        onBoardTabSelectionToggle = onBoardTabSelectionToggle,
                        removingKeys = removingBoardTabKeys,
                        onBoardTabLongPressed = onBoardTabLongPressed,
                        onBoardTabLongPressReleased = onBoardTabLongPressReleased,
                        tabSessionStore = tabSessionStore,
                        isInLongPressSelectionMode = isInLongPressSelectionMode,
                        isReorderEnabled = false,
                    )
                },
                searchEmptyContent = {
                    SearchResultEmptyState(contentPadding = listContentPadding)
                },
            )

            TabPage.THREAD -> AnimatedListContent(
                displayState = when {
                    !isShowingSearchResults -> TabListDisplayState.Normal
                    filteredThreadTabs.isEmpty() -> TabListDisplayState.SearchEmpty
                    else -> TabListDisplayState.SearchResults
                },
                normalContent = {
                    OpenThreadsList(
                        openTabs = openThreadTabs,
                        onCloseClick = onCloseThreadTab,
                        onSwipeDelete = onSwipeDeleteThreadTab,
                        navController = navController,
                        closeDrawer = closeDrawer,
                        contentPadding = listContentPadding,
                        listState = threadNormalListState,
                        newResCounts = newResCounts,
                        selectedThreadTab = selectedThreadTab,
                        isSelectionMode = isSelectionMode,
                        selectedThreadTabIds = selectedThreadTabIds,
                        onThreadTabSelectionToggle = onThreadTabSelectionToggle,
                        removingKeys = removingThreadTabKeys,
                        onThreadTabLongPressed = onThreadTabLongPressed,
                        onThreadTabLongPressMoved = onThreadTabLongPressMoved,
                        onThreadTabLongPressReleased = onThreadTabLongPressReleased,
                        onClearNewResCount = onClearNewResCount,
                        tabSessionStore = tabSessionStore,
                        isInLongPressSelectionMode = isInLongPressSelectionMode,
                        isReorderEnabled = !isSearchMode && !isSelectionMode,
                        onReorderStarted = onThreadTabReorderStarted,
                        onReorderMoved = onThreadTabReorderMoved,
                        onReorderFinished = onThreadTabReorderFinished,
                        onReorderCancelled = onThreadTabReorderCancelled,
                        onReorderAccessibilityMove = onThreadTabReorderAccessibilityMove,
                        currentScreenRoute = currentScreenRoute,
                    )
                },
                searchResultContent = {
                    OpenThreadsList(
                        openTabs = filteredThreadTabs,
                        onCloseClick = onCloseThreadTab,
                        onSwipeDelete = onSwipeDeleteThreadTab,
                        navController = navController,
                        closeDrawer = closeDrawer,
                        contentPadding = listContentPadding,
                        listState = threadSearchListState,
                        newResCounts = newResCounts,
                        selectedThreadTab = selectedThreadTab,
                        isSelectionMode = isSelectionMode,
                        selectedThreadTabIds = selectedThreadTabIds,
                        onThreadTabSelectionToggle = onThreadTabSelectionToggle,
                        removingKeys = removingThreadTabKeys,
                        onThreadTabLongPressed = onThreadTabLongPressed,
                        onThreadTabLongPressReleased = onThreadTabLongPressReleased,
                        onClearNewResCount = onClearNewResCount,
                        tabSessionStore = tabSessionStore,
                        isInLongPressSelectionMode = isInLongPressSelectionMode,
                        isReorderEnabled = false,
                    )
                },
                searchEmptyContent = {
                    SearchResultEmptyState(contentPadding = listContentPadding)
                },
            )

            null -> Unit
        }
    }
}

/**
 * 検索クエリに一致するタブが存在しないときの空状態メッセージを表示する。
 *
 * リスト領域と同じ余白を適用し、トップ検索バーと下部操作領域を避けた中央へ文言を配置する。
 */
@Composable
private fun SearchResultEmptyState(
    contentPadding: PaddingValues,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.search_results_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 通常リスト、検索結果リスト、検索結果なし表示をフェードで切り替える。
 *
 * 単一の表示状態として切り替えることで、検索解除時に検索側のコンテンツが途中で
 * 別状態へ再評価されることを防ぎつつ、各 `LazyListState` 自体は呼び出し元で分離して
 * 保持されたものをそのまま利用する。
 */
@Composable
private fun AnimatedListContent(
    displayState: TabListDisplayState,
    normalContent: @Composable () -> Unit,
    searchResultContent: @Composable () -> Unit,
    searchEmptyContent: @Composable () -> Unit,
) {
    AnimatedContent(
        targetState = displayState,
        transitionSpec = {
            fadeIn(animationSpec = tween(durationMillis = TabListAnimationDefaults.LIST_FADE_IN_MILLIS)) togetherWith
                fadeOut(animationSpec = tween(durationMillis = TabListAnimationDefaults.LIST_FADE_OUT_MILLIS))
        },
        label = "TabListSearchTransition",
    ) { state ->
        when (state) {
            TabListDisplayState.Normal -> normalContent()
            TabListDisplayState.SearchResults -> searchResultContent()
            TabListDisplayState.SearchEmpty -> searchEmptyContent()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchResultEmptyStatePreview() {
    SearchResultEmptyState(
        contentPadding = TabListLayoutDefaults.emptyStatePreviewPadding,
    )
}
