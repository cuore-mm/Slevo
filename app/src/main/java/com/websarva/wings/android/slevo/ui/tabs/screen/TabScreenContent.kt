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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.TabPage
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.data.util.ThreadInfoDerivedCalculator
import com.websarva.wings.android.slevo.ui.board.screen.BoardInfoBottomSheet
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.showBoardScreenForTabSelection
import com.websarva.wings.android.slevo.ui.navigation.showThreadScreenForTabSelection
import com.websarva.wings.android.slevo.ui.tabs.TabListUiState
import com.websarva.wings.android.slevo.ui.tabs.TabListViewModel
import com.websarva.wings.android.slevo.ui.tabs.UrlOpenResult
import com.websarva.wings.android.slevo.ui.tabs.component.AnchoredTabActionMenu
import com.websarva.wings.android.slevo.ui.tabs.component.TabHeaderTrailingContent
import com.websarva.wings.android.slevo.ui.tabs.component.TabListBottomControls
import com.websarva.wings.android.slevo.ui.tabs.component.TabListLayoutDefaults
import com.websarva.wings.android.slevo.ui.tabs.component.TabListCard
import com.websarva.wings.android.slevo.ui.tabs.component.TabListTopSearchArea
import com.websarva.wings.android.slevo.ui.tabs.component.extractServiceName
import com.websarva.wings.android.slevo.ui.tabs.applyReorderDraft
import com.websarva.wings.android.slevo.ui.tabs.dialog.UrlOpenDialog
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.filterBoardTabsByQuery
import com.websarva.wings.android.slevo.ui.tabs.model.filterThreadTabsByQuery
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.theme.bookmarkColor
import com.websarva.wings.android.slevo.ui.thread.sheet.ThreadInfoBottomSheet
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    initialPage: Int = TabPage.BOARD.index,
    onPageChanged: (Int) -> Unit = {},
    currentScreenRoute: AppRoute? = null,
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
    // --- Long-press preview state ---
    var longPressPreviewOffset by remember { mutableStateOf(Offset.Zero) }

    // --- Haze state ---
    val hazeState = rememberHazeState()

    // --- Pager state ---
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { TabPage.count })
    val boardNormalListState = rememberLazyListState()
    val boardSearchListState = rememberLazyListState()
    val threadNormalListState = rememberLazyListState()
    val threadSearchListState = rememberLazyListState()

    // --- Search state delegation ---
    val isSearchMode = listUiState.isSearchMode
    val searchQuery = listUiState.searchQuery
    val isShowingSearchResults = searchQuery.isNotBlank()
    val filteredBoardTabs = filterBoardTabsByQuery(openBoardTabs, searchQuery)
    val filteredThreadTabs = filterThreadTabsByQuery(openThreadTabs, searchQuery)
    val displayedBoardTabs = applyReorderDraft(openBoardTabs, listUiState.boardReorderDraft, BoardTabInfo::boardUrl)
    val displayedThreadTabs = applyReorderDraft(openThreadTabs, listUiState.threadReorderDraft) { it.id.value }
    val isSelectionMode = listUiState.isInSelectionMode
    val selectionPage = listUiState.selectionModePage
    val allSelectedPinned = when (selectionPage) {
        TabPage.BOARD -> openBoardTabs
            .filter { it.boardUrl in listUiState.selectedBoardTabKeys }
            .let { tabs -> tabs.isNotEmpty() && tabs.all { it.isPinned } }
        TabPage.THREAD -> openThreadTabs
            .filter { it.id in listUiState.selectedThreadTabIds }
            .let { tabs -> tabs.isNotEmpty() && tabs.all { it.isPinned } }
        null -> false
    }

    // --- Removal state cleanup ---
    LaunchedEffect(openBoardTabs, listUiState.removingBoardTabKeys) {
        val activeKeys = openBoardTabs.map(BoardTabInfo::boardUrl).toSet()
        tabListViewModel.clearBoardRemovalKeys(listUiState.removingBoardTabKeys - activeKeys)
    }
    LaunchedEffect(openThreadTabs, listUiState.removingThreadTabKeys) {
        val activeKeys = openThreadTabs.map { it.id.value }.toSet()
        tabListViewModel.clearThreadRemovalKeys(listUiState.removingThreadTabKeys - activeKeys)
    }
    LaunchedEffect(openBoardTabs, openThreadTabs) {
        tabListViewModel.pruneSelection(
            boardUrls = openBoardTabs.map(BoardTabInfo::boardUrl).toSet(),
            threadIds = openThreadTabs.map(ThreadTabInfo::id).toSet(),
        )
    }
    // --- Preview offset cleanup ---
    LaunchedEffect(listUiState.isInLongPressSelectionMode) {
        if (!listUiState.isInLongPressSelectionMode) {
            longPressPreviewOffset = Offset.Zero
        }
    }

    /**
     * 検索モードを開始する。
     *
     * 検索クエリが空の間は通常リストを表示し続けるため、ここでは表示リストや
     * スクロール状態の切り替えを行わない。
     */
    fun enterSearchMode() {
        tabListViewModel.enterSearchMode()
    }

    /**
     * 検索モードを終了する。
     *
     * 通常リストと検索結果リストは別 state のため、検索解除時の復元スクロールは不要である。
     */
    fun exitSearchMode() {
        tabListViewModel.closeSearchMode()
    }

    /**
     * ViewModel の先頭表示要求を監視し、対象クエリの検索結果リストが描画対象になった後に実行して消費する。
     */
    LaunchedEffect(
        listUiState.pendingScrollToTopRequest,
        listUiState.searchQuery,
        filteredBoardTabs.size,
        filteredThreadTabs.size,
    ) {
        val request = listUiState.pendingScrollToTopRequest ?: return@LaunchedEffect

        // 古いクエリに対する要求は実行しない。
        if (request.query != listUiState.searchQuery) {
            return@LaunchedEffect
        }

        // 検索結果リストが表示対象になるまでは待機する。
        if (!isShowingSearchResults) {
            return@LaunchedEffect
        }

        when (TabPage.fromIndex(request.page)) {
            TabPage.BOARD -> {
                if (filteredBoardTabs.isNotEmpty()) {
                    boardSearchListState.requestScrollToItem(0)
                }
            }

            TabPage.THREAD -> {
                if (filteredThreadTabs.isNotEmpty()) {
                    threadSearchListState.requestScrollToItem(0)
                }
            }

            null -> Unit
        }

        tabListViewModel.consumePendingScrollToTopRequest()
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            tabListViewModel.onPageChanged(page)
            onPageChanged(page)
        }
    }

    // --- Back handler for search mode ---
    if (isSearchMode) {
        BackHandler {
            exitSearchMode()
        }
    } else if (isSelectionMode) {
        BackHandler {
            tabListViewModel.exitSelectionMode()
        }
    }

    // --- Scaffold ---
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        // TabListBottomControls と上部検索領域に合わせたリスト余白。
        val topSearchHeight = TabListLayoutDefaults.topSearchHeight
        val bottomControlsHeight = TabListLayoutDefaults.listBottomPadding
        val listPadding = PaddingValues(
            top = topSearchHeight + TabListLayoutDefaults.listTopSpacing,
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
                        navController = navController,
                         closeDrawer = closeDrawer,
                         listContentPadding = listPadding,
                         isShowingSearchResults = isShowingSearchResults,
                         isSearchMode = isSearchMode,
                         boardNormalListState = boardNormalListState,
                        boardSearchListState = boardSearchListState,
                        threadNormalListState = threadNormalListState,
                        threadSearchListState = threadSearchListState,
                         openBoardTabs = displayedBoardTabs,
                        filteredBoardTabs = filteredBoardTabs,
                         openThreadTabs = displayedThreadTabs,
                        filteredThreadTabs = filteredThreadTabs,
                        newResCounts = newResCounts,
                         selectedBoardTab = listUiState.selectedBoardTab,
                         selectedThreadTab = listUiState.selectedThreadTab,
                         isSelectionMode = isSelectionMode,
                         selectedBoardTabKeys = listUiState.selectedBoardTabKeys,
                         selectedThreadTabIds = listUiState.selectedThreadTabIds,
                        onCloseBoardTab = { tabListViewModel.startBoardTabRemoval(it) },
                         onCloseThreadTab = { tabListViewModel.startThreadTabRemoval(it) },
                         onBoardTabSelectionToggle = { tabListViewModel.toggleBoardTabSelection(it) },
                         onThreadTabSelectionToggle = { tabListViewModel.toggleThreadTabSelection(it) },
                        onSwipeDeleteBoardTab = { tabSessionStore.closeBoardTab(it) },
                        onSwipeDeleteThreadTab = createThreadTabCloseHandler(tabSessionStore),
                          onBoardTabLongPressed = { tab, bounds ->
                              longPressPreviewOffset = Offset.Zero
                              tabListViewModel.onBoardTabLongPressed(tab, bounds)
                          },
                          onBoardTabLongPressMoved = { offset ->
                              longPressPreviewOffset = offset
                          },
                          onBoardTabLongPressReleased = {
                              longPressPreviewOffset = Offset.Zero
                              tabListViewModel.openSelectedTabMenu()
                          },
                          onThreadTabLongPressed = { tab, bounds ->
                              longPressPreviewOffset = Offset.Zero
                              tabListViewModel.onThreadTabLongPressed(tab, bounds)
                          },
                          onThreadTabLongPressMoved = { offset ->
                              longPressPreviewOffset = offset
                          },
                          onThreadTabLongPressReleased = {
                              longPressPreviewOffset = Offset.Zero
                              tabListViewModel.openSelectedTabMenu()
                          },
                          onBoardTabReorderStarted = {
                              longPressPreviewOffset = Offset.Zero
                              tabListViewModel.startBoardReorder()
                          },
                         onBoardTabReorderMoved = { from, to -> tabListViewModel.moveBoardReorder(from, to) },
                         onBoardTabReorderFinished = { tabListViewModel.finishBoardReorder() },
                          onBoardTabReorderCancelled = {
                              longPressPreviewOffset = Offset.Zero
                              tabListViewModel.cancelReorder()
                          },
                         onBoardTabReorderAccessibilityMove = { tab, offset ->
                             tabListViewModel.moveBoardTabByOffset(tab.boardUrl, offset)
                         },
                          onThreadTabReorderStarted = {
                              longPressPreviewOffset = Offset.Zero
                              tabListViewModel.startThreadReorder()
                          },
                         onThreadTabReorderMoved = { from, to -> tabListViewModel.moveThreadReorder(from, to) },
                         onThreadTabReorderFinished = { tabListViewModel.finishThreadReorder() },
                          onThreadTabReorderCancelled = {
                              longPressPreviewOffset = Offset.Zero
                              tabListViewModel.cancelReorder()
                          },
                         onThreadTabReorderAccessibilityMove = { tab, offset ->
                             tabListViewModel.moveThreadTabByOffset(tab.id.value, offset)
                         },
                        onClearNewResCount = { tabSessionStore.clearNewResCount(it) },
                        removingBoardTabKeys = listUiState.removingBoardTabKeys,
                        removingThreadTabKeys = listUiState.removingThreadTabKeys,
                        tabSessionStore = tabSessionStore,
                         isInLongPressSelectionMode = listUiState.isTabGestureLocked || isSelectionMode,
                        currentScreenRoute = currentScreenRoute,
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
                isSelectionMode = isSelectionMode,
                selectedTabCount = listUiState.selectedTabCount,
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
                isSelectionMode = isSelectionMode,
                selectedTabCount = listUiState.selectedTabCount,
                searchInputValue = listUiState.searchInputValue,
                searchFocusRequestId = listUiState.pendingSearchFocusRequestId,
                onSearchClick = { enterSearchMode() },
                 onMoreClick = { bounds -> tabListViewModel.showBulkCloseMenu(bounds) },
                onSearchInputChange = { inputValue: TextFieldValue ->
                    tabListViewModel.updateSearchInput(inputValue, pagerState.currentPage)
                },
                onSearchFocusRequestConsumed = { tabListViewModel.consumePendingSearchFocusRequest() },
                 onCloseSearch = { exitSearchMode() },
                 onBackFromSelection = { tabListViewModel.exitSelectionMode() },
             )

            // --- Bulk close menu ---
            AnchoredTabActionMenu(
                expanded = listUiState.isBulkCloseMenuVisible,
                anchorBoundsInWindow = listUiState.bulkCloseMenuBounds,
                hazeState = hazeState,
                onDismissRequest = { tabListViewModel.dismissBulkCloseMenu() },
                 onCloseAllClick = {
                    // Pager state is the source of truth for the page acted on by the menu.
                    TabPage.fromIndex(pagerState.currentPage)?.let {
                        tabListViewModel.closeAllUnpinnedTabs(it)
                     }
                 },
                 isSelectionMode = isSelectionMode,
                 selectedTabCount = listUiState.selectedTabCount,
                 allSelectedPinned = allSelectedPinned,
                 onSelectClick = {
                     TabPage.fromIndex(pagerState.currentPage)?.let { page ->
                         tabListViewModel.startSelectionMode(page)
                     }
                 },
                 onCloseSelectedClick = {
                     selectionPage?.let(tabListViewModel::closeSelectedTabs)
                 },
                 onPinSelectedClick = {
                     selectionPage?.let { tabListViewModel.setSelectedTabsPinned(it) }
                 },
             )

            // --- Long-press overlay layer ---
            TabLongPressOverlayLayer(
                uiState = listUiState,
                newResCounts = newResCounts,
                hazeState = hazeState,
                onCancelSelection = {
                    longPressPreviewOffset = Offset.Zero
                    tabListViewModel.cancelTabSelection()
                },
                onDetailClick = { tabListViewModel.openSelectedTabDetail() },
                 onPinClick = { tabListViewModel.toggleSelectedTabPin() },
                 onCloseClick = { tabListViewModel.requestCloseSelectedTab() },
                 onSelectClick = {
                     val selectedBoardTab = tabListViewModel.uiState.value.selectedBoardTab
                     val selectedThreadTab = tabListViewModel.uiState.value.selectedThreadTab
                     when {
                         selectedBoardTab != null -> tabListViewModel.startSelectionMode(
                             page = TabPage.BOARD,
                             initialBoardUrl = selectedBoardTab.boardUrl,
                         )
                         selectedThreadTab != null -> tabListViewModel.startSelectionMode(
                             page = TabPage.THREAD,
                             initialThreadId = selectedThreadTab.id,
                         )
                     }
                 },
                isBackHandlerEnabled = !isSearchMode,
                previewOffset = longPressPreviewOffset,
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
                                    tabSessionStore.registerAndSelectBoardRoute(result.route)
                                    navController.showBoardScreenForTabSelection(
                                        currentScreenRoute = currentScreenRoute,
                                        route = result.route,
                                    )
                                    closeDrawer()
                                }

                                is UrlOpenResult.NavigateThread -> {
                                    val index = tabSessionStore.registerAndSelectThreadRoute(result.route)
                                    if (index >= 0) {
                                        navController.showThreadScreenForTabSelection(
                                            currentScreenRoute = currentScreenRoute,
                                            route = result.route,
                                        )
                                        closeDrawer()
                                    }
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
    onSelectClick: () -> Unit,
    isBackHandlerEnabled: Boolean,
    previewOffset: Offset,
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
    val previewOffsetInPixels = IntOffset(
        previewOffset.x.roundToInt(),
        previewOffset.y.roundToInt(),
    )

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
                    .clickable(
                        enabled = uiState.isInLongPressSelectionMode &&
                            uiState.tabActionMenuMode == com.websarva.wings.android.slevo.ui.tabs.TabActionMenuMode.Open
                    ) {
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
                        .offset {
                            IntOffset(
                                localLeft + previewOffsetInPixels.x,
                                localTop + previewOffsetInPixels.y,
                            )
                        }
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
                        .offset {
                            IntOffset(
                                localLeft + previewOffsetInPixels.x,
                                localTop + previewOffsetInPixels.y,
                            )
                        }
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
            interactive = uiState.tabActionMenuMode == com.websarva.wings.android.slevo.ui.tabs.TabActionMenuMode.Open,
            onDismissRequest = onCancelSelection,
            onDetailClick = onDetailClick,
            onPinClick = onPinClick,
            onCloseClick = onCloseClick,
            onSelectClick = onSelectClick,
        )
    }

    // --- Back handler for selection mode ---
    if (
        uiState.isInLongPressSelectionMode &&
        uiState.tabActionMenuMode == com.websarva.wings.android.slevo.ui.tabs.TabActionMenuMode.Open &&
        isBackHandlerEnabled
    ) {
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
