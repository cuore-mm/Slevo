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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo

/**
 * 検索表示エリア内で切り替えるコンテンツ種別を表す。
 *
 * 検索結果が存在する場合はリスト、0 件の場合は空状態メッセージを表示する。
 */
private enum class SearchResultContentState {
    HasResults,
    Empty,
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
    pendingCloseBoardTab: BoardTabInfo?,
    pendingCloseThreadTab: ThreadTabInfo?,
    onCloseRequestConsumed: () -> Unit,
    onCloseBoardTab: (BoardTabInfo) -> Unit,
    onCloseThreadTab: (ThreadTabInfo) -> Unit,
    onBoardTabLongPressed: (BoardTabInfo, IntRect) -> Unit,
    onThreadTabLongPressed: (ThreadTabInfo, IntRect) -> Unit,
    onClearNewResCount: (ThreadId) -> Unit,
    isInLongPressSelectionMode: Boolean = false,
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false,
    ) { page ->
        when (page) {
            0 -> AnimatedListContent(
                isShowingSearchResults = isShowingSearchResults,
                normalContent = {
                    OpenBoardsList(
                        openTabs = openBoardTabs,
                        onCloseClick = onCloseBoardTab,
                        navController = navController,
                        closeDrawer = closeDrawer,
                        contentPadding = listContentPadding,
                        listState = boardNormalListState,
                        selectedBoardTab = selectedBoardTab,
                        pendingCloseBoardTab = pendingCloseBoardTab,
                        onCloseRequestConsumed = onCloseRequestConsumed,
                        onBoardTabLongPressed = onBoardTabLongPressed,
                        tabSessionStore = tabSessionStore,
                        isInLongPressSelectionMode = isInLongPressSelectionMode,
                    )
                },
                searchContent = {
                    AnimatedSearchResultContent(
                        contentState = if (filteredBoardTabs.isEmpty()) {
                            SearchResultContentState.Empty
                        } else {
                            SearchResultContentState.HasResults
                        },
                        emptyContent = {
                            SearchResultEmptyState(contentPadding = listContentPadding)
                        },
                        resultContent = {
                            OpenBoardsList(
                                openTabs = filteredBoardTabs,
                                onCloseClick = onCloseBoardTab,
                                navController = navController,
                                closeDrawer = closeDrawer,
                                contentPadding = listContentPadding,
                                listState = boardSearchListState,
                                selectedBoardTab = selectedBoardTab,
                                pendingCloseBoardTab = pendingCloseBoardTab,
                                onCloseRequestConsumed = onCloseRequestConsumed,
                                onBoardTabLongPressed = onBoardTabLongPressed,
                                tabSessionStore = tabSessionStore,
                                isInLongPressSelectionMode = isInLongPressSelectionMode,
                            )
                        },
                    )
                },
            )

            else -> AnimatedListContent(
                isShowingSearchResults = isShowingSearchResults,
                normalContent = {
                    OpenThreadsList(
                        openTabs = openThreadTabs,
                        onCloseClick = onCloseThreadTab,
                        navController = navController,
                        closeDrawer = closeDrawer,
                        contentPadding = listContentPadding,
                        listState = threadNormalListState,
                        newResCounts = newResCounts,
                        selectedThreadTab = selectedThreadTab,
                        pendingCloseThreadTab = pendingCloseThreadTab,
                        onCloseRequestConsumed = onCloseRequestConsumed,
                        onThreadTabLongPressed = onThreadTabLongPressed,
                        onClearNewResCount = onClearNewResCount,
                        tabSessionStore = tabSessionStore,
                        isInLongPressSelectionMode = isInLongPressSelectionMode,
                    )
                },
                searchContent = {
                    AnimatedSearchResultContent(
                        contentState = if (filteredThreadTabs.isEmpty()) {
                            SearchResultContentState.Empty
                        } else {
                            SearchResultContentState.HasResults
                        },
                        emptyContent = {
                            SearchResultEmptyState(contentPadding = listContentPadding)
                        },
                        resultContent = {
                            OpenThreadsList(
                                openTabs = filteredThreadTabs,
                                onCloseClick = onCloseThreadTab,
                                navController = navController,
                                closeDrawer = closeDrawer,
                                contentPadding = listContentPadding,
                                listState = threadSearchListState,
                                newResCounts = newResCounts,
                                selectedThreadTab = selectedThreadTab,
                                pendingCloseThreadTab = pendingCloseThreadTab,
                                onCloseRequestConsumed = onCloseRequestConsumed,
                                onThreadTabLongPressed = onThreadTabLongPressed,
                                onClearNewResCount = onClearNewResCount,
                                tabSessionStore = tabSessionStore,
                                isInLongPressSelectionMode = isInLongPressSelectionMode,
                            )
                        },
                    )
                },
            )
        }
    }
}

/**
 * 検索表示エリア内で、検索結果リストと空状態メッセージをフェードで切り替える。
 *
 * 通常リストと検索結果リストの切り替えとは別に、検索中の「結果あり / なし」だけを
 * 局所的にアニメーションさせる。
 */
@Composable
private fun AnimatedSearchResultContent(
    contentState: SearchResultContentState,
    emptyContent: @Composable () -> Unit,
    resultContent: @Composable () -> Unit,
) {
    AnimatedContent(
        targetState = contentState,
        transitionSpec = {
            fadeIn(animationSpec = tween(durationMillis = 150)) togetherWith
                fadeOut(animationSpec = tween(durationMillis = 100))
        },
        label = "SearchResultEmptyTransition",
    ) { state ->
        when (state) {
            SearchResultContentState.HasResults -> resultContent()
            SearchResultContentState.Empty -> emptyContent()
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
 * 通常リストと検索結果リストの表示をフェードで切り替える。
 *
 * 検索結果の有無に応じて表示コンテンツだけを切り替え、各 `LazyListState` 自体は
 * 呼び出し元で分離して保持されたものをそのまま利用する。
 */
@Composable
private fun AnimatedListContent(
    isShowingSearchResults: Boolean,
    normalContent: @Composable () -> Unit,
    searchContent: @Composable () -> Unit,
) {
    AnimatedContent(
        targetState = isShowingSearchResults,
        transitionSpec = {
            fadeIn(animationSpec = tween(durationMillis = 180)) togetherWith
                fadeOut(animationSpec = tween(durationMillis = 120))
        },
        label = "TabListSearchTransition",
    ) { showingSearchResults ->
        if (showingSearchResults) {
            searchContent()
        } else {
            normalContent()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchResultEmptyStatePreview() {
    SearchResultEmptyState(
        contentPadding = PaddingValues(vertical = 40.dp, horizontal = 16.dp),
    )
}
