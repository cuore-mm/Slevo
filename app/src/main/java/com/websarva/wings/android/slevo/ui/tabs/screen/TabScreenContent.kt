package com.websarva.wings.android.slevo.ui.tabs.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.data.util.ThreadInfoDerivedCalculator
import com.websarva.wings.android.slevo.ui.board.screen.BoardInfoBottomSheet
import com.websarva.wings.android.slevo.ui.navigation.navigateToBoard
import com.websarva.wings.android.slevo.ui.navigation.navigateToThread
import com.websarva.wings.android.slevo.ui.tabs.component.AnchoredTabActionMenu
import com.websarva.wings.android.slevo.ui.tabs.component.TabHeaderTrailingContent
import com.websarva.wings.android.slevo.ui.tabs.component.TabListBottomControls
import com.websarva.wings.android.slevo.ui.tabs.component.TabListCard
import com.websarva.wings.android.slevo.ui.tabs.component.TabListTopSearchArea
import com.websarva.wings.android.slevo.ui.tabs.component.TabListTopSearchDefaults
import com.websarva.wings.android.slevo.ui.tabs.TabListScrollCommand
import com.websarva.wings.android.slevo.ui.tabs.TabListUiState
import com.websarva.wings.android.slevo.ui.tabs.TabListViewModel
import com.websarva.wings.android.slevo.ui.tabs.TabSearchScrollSnapshot
import com.websarva.wings.android.slevo.ui.tabs.UrlOpenResult
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.tabs.dialog.UrlOpenDialog
import com.websarva.wings.android.slevo.ui.tabs.component.extractServiceName
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.filterBoardTabsByQuery
import com.websarva.wings.android.slevo.ui.tabs.model.filterThreadTabsByQuery
import com.websarva.wings.android.slevo.ui.theme.bookmarkColor
import com.websarva.wings.android.slevo.ui.thread.sheet.ThreadInfoBottomSheet
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

/**
 * タブ一覧とURL入力ダイアログを統合した画面を提供する。
 *
 * URL入力は検証に失敗した場合、ダイアログ内にエラーを表示する。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TabScreenContent(
    modifier: Modifier = Modifier,
    tabSessionStore: TabSessionStore,
    tabListViewModel: TabListViewModel,
    navController: NavHostController,
    closeDrawer: () -> Unit,
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {}
) {
    val openBoardTabs by tabSessionStore.openBoardTabs.collectAsStateWithLifecycle()
    val openThreadTabs by tabSessionStore.openThreadTabs.collectAsStateWithLifecycle()
    val boardLoaded by tabSessionStore.boardLoaded.collectAsStateWithLifecycle()
    val threadLoaded by tabSessionStore.threadLoaded.collectAsStateWithLifecycle()
    val isRefreshing by tabSessionStore.isRefreshing.collectAsStateWithLifecycle()
    val refreshProgress by tabSessionStore.refreshProgress.collectAsStateWithLifecycle()
    val newResCounts by tabSessionStore.newResCounts.collectAsStateWithLifecycle()
    val isLoading = !(boardLoaded && threadLoaded)
    val listUiState by tabListViewModel.uiState.collectAsStateWithLifecycle()
    val invalidUrlMessage = stringResource(R.string.invalid_url)
    val coroutineScope = rememberCoroutineScope()

    // --- Haze state ---
    val hazeState = rememberHazeState()

    // --- Pager state ---
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 2 })
    val boardListState = rememberLazyListState()
    val threadListState = rememberLazyListState()

    // --- Search state delegation ---
    val isSearchMode = listUiState.isSearchMode
    val searchQuery = listUiState.searchQuery
    val filteredBoardTabs = filterBoardTabsByQuery(openBoardTabs, searchQuery)
    val filteredThreadTabs = filterThreadTabsByQuery(openThreadTabs, searchQuery)

    /**
     * 検索モードを開始する。スクロール位置の保存は検索クエリが空から非空へ
     * 切り替わる直前に行うため、ここでは行わない。
     */
    fun enterSearchMode() {
        tabListViewModel.enterSearchMode()
    }

    /**
     * 検索モードを終了する。スクロール位置の復元は
     * 検索クエリが非空から空へ切り替わる LaunchedEffect で行う。
     */
    fun exitSearchMode() {
        tabListViewModel.closeSearchMode()
    }

    /**
     * ViewModel の復元待ちスナップショットとスクロール命令を監視し、
     * 表示リストが更新された後に実行して消費する。
     *
     * 復元は searchQuery が空で完全リストが描画対象になった後に行い、
     * 先頭表示は searchQuery が非空で現在表示中ページのリストだけを先頭へ移動する。
     */
    LaunchedEffect(listUiState.pendingRestoreSnapshot, listUiState.searchQuery) {
        val snapshot = listUiState.pendingRestoreSnapshot ?: return@LaunchedEffect
        // 検索結果リストから完全リストへ戻るのを待つ
        if (listUiState.searchQuery.isNotBlank()) {
            return@LaunchedEffect
        }
        if (openBoardTabs.isNotEmpty()) {
            val targetIndex = snapshot.boardIndex.coerceIn(0, openBoardTabs.lastIndex)
            boardListState.scrollToItem(targetIndex, snapshot.boardOffset.coerceAtLeast(0))
        }
        if (openThreadTabs.isNotEmpty()) {
            val targetIndex = snapshot.threadIndex.coerceIn(0, openThreadTabs.lastIndex)
            threadListState.scrollToItem(targetIndex, snapshot.threadOffset.coerceAtLeast(0))
        }
        tabListViewModel.consumePendingRestoreSnapshot()
    }

    LaunchedEffect(listUiState.scrollCommand, listUiState.searchQuery) {
        val command = listUiState.scrollCommand ?: return@LaunchedEffect
        // 検索クエリ変更後の検索結果リストが反映されるのを待つ
        if (listUiState.searchQuery.isBlank()) {
            return@LaunchedEffect
        }

        when (command) {
            is TabListScrollCommand.ScrollToTop -> {
                when (command.page) {
                    0 -> {
                        if (filteredBoardTabs.isNotEmpty()) {
                            boardListState.scrollToItem(0)
                        }
                    }

                    else -> {
                        if (filteredThreadTabs.isNotEmpty()) {
                            threadListState.scrollToItem(0)
                        }
                    }
                }
                tabListViewModel.consumeScrollCommand()
            }

            else -> {
                // Restore は pendingRestoreSnapshot 側で処理する
            }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            tabListViewModel.onPageChanged()
            onPageChanged(page)
        }
    }

    // --- Back handler for search mode ---
    if (isSearchMode) {
        BackHandler {
            exitSearchMode()
        }
    }

    // --- Scaffold ---
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        // TabListBottomControls の高さ分の bottom padding。
        // hazeTopOverlap(32) + controlHeight(48) + spacing(8) + progressHeight(8) + bottomPadding(16) = 112.dp
        val topSearchHeight = TabListTopSearchDefaults.height
        val bottomControlsHeight = 112.dp
        val listPadding = PaddingValues(
            top = topSearchHeight + 8.dp,
            bottom = bottomControlsHeight,
        )

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // --- Content with haze source ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularWavyProgressIndicator()
                    }
                } else {
                    TabsPagerContent(
                        modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
                        pagerState = pagerState,
                        boardListState = boardListState,
                        threadListState = threadListState,
                        navController = navController,
                        closeDrawer = closeDrawer,
                        listContentPadding = listPadding,
                        openBoardTabs = filteredBoardTabs,
                        openThreadTabs = filteredThreadTabs,
                        newResCounts = newResCounts,
                        selectedBoardTab = listUiState.selectedBoardTab,
                        selectedThreadTab = listUiState.selectedThreadTab,
                        onCloseBoardTab = { tabSessionStore.closeBoardTab(it) },
                        onCloseThreadTab = { tabSessionStore.closeThreadTab(it) },
                        onBoardTabLongPressed = { tab, bounds ->
                            tabListViewModel.onBoardTabLongPressed(tab, bounds)
                        },
                        onThreadTabLongPressed = { tab, bounds ->
                            tabListViewModel.onThreadTabLongPressed(tab, bounds)
                        },
                        onClearNewResCount = { tabSessionStore.clearNewResCount(it) },
                        pendingCloseBoardTab = listUiState.pendingCloseBoardTab,
                        pendingCloseThreadTab = listUiState.pendingCloseThreadTab,
                        onCloseRequestConsumed = { tabListViewModel.consumePendingCloseRequest() },
                        tabSessionStore = tabSessionStore,
                        isInLongPressSelectionMode = listUiState.isInLongPressSelectionMode,
                    )
                }
            }

            // --- Bottom controls ---
            TabListBottomControls(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                pagerState = pagerState,
                hazeState = hazeState,
                isRefreshing = isRefreshing,
                isSearchMode = isSearchMode,
                refreshProgress = refreshProgress,
                onCreateTabClick = {
                    tabListViewModel.setUrlErrorMessage(null)
                    tabListViewModel.setUrlDialogVisible(true)
                },
                onRefreshClick = { tabSessionStore.refreshOpenThreads() },
                onCancelRefreshClick = { tabSessionStore.cancelRefreshOpenThreads() },
            )

            TabListTopSearchArea(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = innerPadding.calculateTopPadding()),
                hazeState = hazeState,
                isSearchMode = isSearchMode,
                searchQuery = searchQuery,
                onSearchClick = { enterSearchMode() },
                onQueryChange = { newQuery ->
                    if (searchQuery.isBlank() && newQuery.isNotBlank()) {
                        tabListViewModel.saveScrollSnapshotBeforeSearch(
                            TabSearchScrollSnapshot(
                                boardIndex = boardListState.firstVisibleItemIndex,
                                boardOffset = boardListState.firstVisibleItemScrollOffset,
                                threadIndex = threadListState.firstVisibleItemIndex,
                                threadOffset = threadListState.firstVisibleItemScrollOffset,
                            )
                        )
                    }
                    if (searchQuery.isNotBlank() && newQuery.isNotBlank() && searchQuery != newQuery) {
                        tabListViewModel.issueScrollToTopCommand(pagerState.currentPage)
                    }
                    tabListViewModel.updateSearchQuery(newQuery)
                },
                onCloseSearch = { exitSearchMode() },
            )

            // --- Long-press overlay layer ---
            TabLongPressOverlayLayer(
                uiState = listUiState,
                newResCounts = newResCounts,
                hazeState = hazeState,
                onCancelSelection = { tabListViewModel.cancelTabSelection() },
                onDetailClick = { tabListViewModel.openSelectedTabDetail() },
                onPinClick = { tabListViewModel.toggleSelectedTabPin() },
                onCloseClick = { tabListViewModel.requestCloseSelectedTab() },
                isBackHandlerEnabled = !isSearchMode,
            )

            // --- Bottom sheets ---
            TabDetailBottomSheets(
                uiState = listUiState,
                onDismissBoardSheet = { tabListViewModel.dismissBoardInfoBottomSheet() },
                onDismissThreadSheet = { tabListViewModel.dismissThreadInfoBottomSheet() },
                navController = navController,
                tabSessionStore = tabSessionStore,
            )

            // --- URL dialog ---
            if (listUiState.showUrlDialog) {
                UrlOpenDialog(
                    onDismissRequest = {
                        tabListViewModel.setUrlDialogVisible(false)
                    },
                    isError = listUiState.urlErrorMessage != null,
                    errorMessage = listUiState.urlErrorMessage,
                    isValidating = listUiState.isUrlValidating,
                    onValueChange = {
                        if (listUiState.urlErrorMessage != null) {
                            tabListViewModel.setUrlErrorMessage(null)
                        }
                    },
                    onOpen = { url ->
                        coroutineScope.launch {
                            val result = tabListViewModel.openUrlInput(url, invalidUrlMessage)
                            when (result) {
                                is UrlOpenResult.NavigateBoard -> {
                                    navController.navigateToBoard(
                                        route = result.route,
                                        tabSessionStore = tabSessionStore,
                                    )
                                    closeDrawer()
                                }

                                is UrlOpenResult.NavigateThread -> {
                                    navController.navigateToThread(
                                        route = result.route,
                                        tabSessionStore = tabSessionStore,
                                    )
                                    closeDrawer()
                                }

                                is UrlOpenResult.Error -> {
                                    tabListViewModel.setUrlErrorMessage(result.message)
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

/**
 * 詳細 BottomSheet を detail state に基づいて表示する。
 *
 * `detailBoardTab` / `detailThreadTab` が存在する場合に対応する BottomSheet を描画し、
 * 長押し選択 state の解除後も表示を維持する。
 */
@Composable
private fun TabDetailBottomSheets(
    uiState: TabListUiState,
    onDismissBoardSheet: () -> Unit,
    onDismissThreadSheet: () -> Unit,
    navController: NavHostController,
    tabSessionStore: TabSessionStore,
) {
    val boardTab = uiState.detailBoardTab
    if (boardTab != null) {
        BoardInfoBottomSheet(
            showBoardInfoSheet = uiState.showBoardInfoBottomSheet,
            onDismissRequest = onDismissBoardSheet,
            boardName = boardTab.boardName,
            serviceName = boardTab.serviceName,
            boardUrl = boardTab.boardUrl,
        )
    }

    val threadTab = uiState.detailThreadTab
    if (threadTab != null) {
        val derived = ThreadInfoDerivedCalculator.calculate(
            threadKey = threadTab.threadKey,
            resCount = threadTab.resCount,
        )
        ThreadInfoBottomSheet(
            showThreadInfoSheet = uiState.showThreadInfoBottomSheet,
            onDismissRequest = onDismissThreadSheet,
            threadInfo = ThreadInfo(
                title = threadTab.title,
                key = threadTab.threadKey,
                url = "${threadTab.boardUrl}test/read.cgi/${
                    threadTab.boardUrl.substringAfterLast("/").removeSuffix("/")
                }/${threadTab.threadKey}/",
                datUrl = "",
                resCount = threadTab.resCount,
                date = derived.date,
                momentum = derived.momentum,
            ),
            boardInfo = BoardInfo(
                boardId = threadTab.boardId,
                name = threadTab.boardName,
                url = threadTab.boardUrl,
            ),
            navController = navController,
            tabSessionStore = tabSessionStore,
            showBoardAction = true,
        )
    }
}

/**
 * 長押し選択中の overlay レイヤーをまとめる Composable。
 *
 * dim overlay、floating card（enter アニメーション付き）、
 * アクションメニュー、BackHandler を一箇所に集約する。
 */
@Composable
private fun TabLongPressOverlayLayer(
    uiState: TabListUiState,
    newResCounts: Map<String, Int>,
    hazeState: HazeState,
    onCancelSelection: () -> Unit,
    onDetailClick: () -> Unit,
    onPinClick: () -> Unit,
    onCloseClick: () -> Unit,
    isBackHandlerEnabled: Boolean,
) {
    // --- Floating card animation state (Compose-local) ---
    val floatingScale = remember { Animatable(1f) }

    LaunchedEffect(uiState.isInLongPressSelectionMode) {
        if (uiState.isInLongPressSelectionMode) {
            floatingScale.snapTo(1f)
            floatingScale.animateTo(
                targetValue = 1.04f,
                animationSpec = tween(durationMillis = 220),
            )
        } else {
            floatingScale.snapTo(1f)
        }
    }

    val boxWindowOffset = remember { mutableStateOf(IntOffset.Zero) }
    val boundsForFloating = uiState.selectedTabBounds
    val hasFloatingBounds = boundsForFloating != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val pos = coordinates.positionInWindow()
                boxWindowOffset.value = IntOffset(pos.x.toInt(), pos.y.toInt())
            }
    ) {
        // --- Long-press dim overlay ---
        val showDimOverlay = uiState.isInLongPressSelectionMode
        val dimAlpha by animateFloatAsState(
            targetValue = if (uiState.isInLongPressSelectionMode) 0.30f else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "dimOverlayAlpha",
        )

        if (showDimOverlay || dimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAlpha))
                    .clickable(enabled = uiState.isInLongPressSelectionMode) {
                        onCancelSelection()
                    }
            )
        }

        // --- Selected tab floating card (overlay layer) ---
        val density = LocalDensity.current
        val boardTabForFloating = if (hasFloatingBounds) uiState.selectedBoardTab else null
        val threadTabForFloating = if (hasFloatingBounds) uiState.selectedThreadTab else null

        boardTabForFloating?.let { tab ->
            boundsForFloating?.let { bounds ->
                val localLeft = bounds.left - boxWindowOffset.value.x
                val localTop = bounds.top - boxWindowOffset.value.y
                val cardWidthPx = bounds.right - bounds.left
                Box(
                    modifier = Modifier
                        .width(with(density) { cardWidthPx.toDp() })
                        .offset { IntOffset(localLeft, localTop) }
                        .graphicsLayer {
                            scaleX = floatingScale.value
                            scaleY = floatingScale.value
                            transformOrigin = TransformOrigin.Center
                        }
                        .clickable { /* 選択タブのタップは選択解除しない */ }
                ) {
                    BoardTabFloatingCard(tab = tab)
                }
            }
        }
        threadTabForFloating?.let { tab ->
            boundsForFloating?.let { bounds ->
                val localLeft = bounds.left - boxWindowOffset.value.x
                val localTop = bounds.top - boxWindowOffset.value.y
                val cardWidthPx = bounds.right - bounds.left
                Box(
                    modifier = Modifier
                        .width(with(density) { cardWidthPx.toDp() })
                        .offset { IntOffset(localLeft, localTop) }
                        .graphicsLayer {
                            scaleX = floatingScale.value
                            scaleY = floatingScale.value
                            transformOrigin = TransformOrigin.Center
                        }
                        .clickable { /* 選択タブのタップは選択解除しない */ }
                ) {
                    ThreadTabFloatingCard(
                        tab = tab,
                        newResCount = newResCounts[tab.id.value] ?: tab.newResCount,
                    )
                }
            }
        }

        // --- Anchored tab action menu ---
        AnchoredTabActionMenu(
            expanded = uiState.isInLongPressSelectionMode,
            anchorBoundsInWindow = uiState.selectedTabBounds,
            hazeState = null,
            isPinned = uiState.selectedBoardTab?.isPinned
                ?: uiState.selectedThreadTab?.isPinned
                ?: false,
            onDismissRequest = onCancelSelection,
            onDetailClick = onDetailClick,
            onPinClick = onPinClick,
            onCloseClick = onCloseClick,
        )
    }

    // --- Back handler for selection mode ---
    if (uiState.isInLongPressSelectionMode && isBackHandlerEnabled) {
        BackHandler { onCancelSelection() }
    }
}

/**
 * 選択中の板タブを overlay 上に再描画するための Composable。
 * floating card 側で選択中の視覚状態（拡大・影）を表現する。
 */
@Composable
private fun BoardTabFloatingCard(tab: BoardTabInfo) {
    val color =
        tab.bookmarkColorName?.let { bookmarkColor(it) }
    val serviceName = tab.serviceName.ifBlank { extractServiceName(tab.boardUrl) }

    TabListCard(
        bookmarkColor = color,
        onClick = {},
        isPinned = tab.isPinned,
        headerTitle = serviceName,
        bodyTitle = tab.boardName,
        bodyMaxLines = 1,
        onCloseClick = {},
    )
}

/**
 * 選択中のスレッドタブを overlay 上に再描画するための Composable。
 * floating card 側で選択中の視覚状態（拡大・影）を表現する。
 */
@Composable
private fun ThreadTabFloatingCard(tab: ThreadTabInfo, newResCount: Int) {
    val color =
        tab.bookmarkColorName?.let { bookmarkColor(it) }

    TabListCard(
        bookmarkColor = color,
        onClick = {},
        isPinned = tab.isPinned,
        headerTitle = tab.boardName,
        headerTrailingContent = TabHeaderTrailingContent.ThreadResCount(
            resCount = tab.resCount,
            newResCount = newResCount,
        ),
        bodyTitle = tab.title,
        bodyMaxLines = 2,
        onCloseClick = {},
    )
}
