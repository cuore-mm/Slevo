package com.websarva.wings.android.slevo.ui.bbsroute

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolder
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkSheetHost
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.showBoardScreenForTabSelection
import com.websarva.wings.android.slevo.ui.navigation.showThreadScreenForTabSelection
import com.websarva.wings.android.slevo.ui.tabs.TabsBottomSheet
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.tabs.dialog.UrlOpenDialog
import com.websarva.wings.android.slevo.ui.util.ResolvedUrl
import com.websarva.wings.android.slevo.ui.util.rememberBottomBarActionVisibility
import com.websarva.wings.android.slevo.ui.util.resolveUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.websarva.wings.android.slevo.R

/**
 * 板/スレ共通のタブUIと画面内シートを提供する。
 *
 * URL入力ダイアログは検証失敗時にエラー表示し、閉じずに再入力させる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <TabInfo : Any, Key : Any, UiState : BaseUiState<UiState>> BbsRouteScaffold(
    route: AppRoute,
    tabSessionStore: TabSessionStore,
    navController: NavHostController,
    presentationState: TabPresentationState<TabInfo, Key>,
    onEmptyTabs: () -> Unit,
    getUiState: (TabInfo) -> StateFlow<UiState>,
    getBookmarkSheetHolder: (TabInfo) -> BookmarkBottomSheetStateHolder? = { null },
    getKey: (TabInfo) -> Key,
    getScrollIndex: (TabInfo) -> Int,
    getScrollOffset: (TabInfo) -> Int,
    updateScrollPosition: (tab: TabInfo, index: Int, offset: Int) -> Unit,
    onTabSelected: (TabInfo) -> Unit,
    animateToPageFlow: Flow<Int>? = null,
    titleCard: @Composable (
        tabInfo: TabInfo,
        uiState: UiState,
        actionProgress: Float,
        modifier: Modifier,
    ) -> Unit,
    bottomBar: @Composable (
        tabInfo: TabInfo,
        uiState: UiState,
        actionProgress: Float,
        openTabListSheet: () -> Unit,
        controllerModifier: Modifier,
        titleContent: @Composable (Modifier) -> Unit,
    ) -> Unit,
    content: @Composable (
        tabInfo: TabInfo,
        uiState: UiState,
        listState: LazyListState,
        modifier: Modifier,
        navController: NavHostController,
        openTabListSheet: () -> Unit,
        openUrlDialog: () -> Unit,
    ) -> Unit,
    bottomBarScrollBehavior: (@Composable (LazyListState) -> BottomAppBarScrollBehavior)? = null,
    bottomBarActionVisibilityEnabled: Boolean = true,
    optionalSheetContent: @Composable (tabInfo: TabInfo, uiState: UiState) -> Unit = { _, _ -> }
) {
    val displayDecision = remember(presentationState) {
        deriveTabDisplayDecision(presentationState, getKey)
    }
    LaunchedEffect(displayDecision) {
        if (displayDecision is TabDisplayDecision.Empty) {
            onEmptyTabs()
        }
    }

    var cachedPresentationState by remember {
        mutableStateOf<TabPresentationState<TabInfo, Key>?>(null)
    }
    if (presentationState.selection !is TabSelectionResolution.Loading && presentationState.tabs.isNotEmpty()) {
        cachedPresentationState = presentationState
    }
    val renderState = if (
        presentationState.selection is TabSelectionResolution.Loading &&
        cachedPresentationState != null
    ) {
        cachedPresentationState!!
    } else {
        presentationState
    }
    val tabs = renderState.tabs
    val selectedPage = (displayDecision as? TabDisplayDecision.Selected)?.index ?: -1
    val selectedKey = (presentationState.selection as? TabSelectionResolution.Selected)?.key
    var lastSynchronizedSelectedKey by remember { mutableStateOf(selectedKey) }

    if (tabs.isNotEmpty()) {
        // --- Pager state ---
        val pagerState =
            rememberPagerState(
                initialPage = selectedPage.takeIf { it in tabs.indices } ?: 0,
                pageCount = { tabs.size },
            )
        val actionProgressStates = remember { mutableMapOf<Key, MutableState<Float>>() }

        SideEffect {
            val currentKeys = tabs.map(getKey).toSet()
            actionProgressStates.keys.retainAll(currentKeys)
        }

        // --- Selection synchronization ---
        LaunchedEffect(displayDecision, tabs.size) {
            if (displayDecision is TabDisplayDecision.Selected &&
                selectedPage in tabs.indices &&
                pagerState.currentPage != selectedPage
            ) {
                pagerState.scrollToPage(selectedPage)
            }
        }

        LaunchedEffect(pagerState, tabs, displayDecision, selectedKey) {
            snapshotFlow { pagerState.settledPage }
                .distinctUntilChanged()
                .collectLatest { page ->
                    // PendingMissing中は一覧の再bindによる選択callbackを抑止する。
                    if (displayDecision !is TabDisplayDecision.Selected) return@collectLatest
                    val settledTab = tabs.getOrNull(page) ?: return@collectLatest
                    val selectedTab = tabs.getOrNull(selectedPage) ?: return@collectLatest
                    // selected key起因のprogrammatic scroll中はユーザー選択として通知しない。
                    if (selectedKey != lastSynchronizedSelectedKey) {
                        if (page == selectedPage) lastSynchronizedSelectedKey = selectedKey
                        return@collectLatest
                    }
                    if (getKey(settledTab) != getKey(selectedTab)) {
                        onTabSelected(settledTab)
                    }
                }
        }

        LaunchedEffect(animateToPageFlow, pagerState) {
            animateToPageFlow?.let { flow ->
                flow.collectLatest { target ->
                    if (pagerState.pageCount <= 0) return@collectLatest
                    val bounded = target.coerceIn(0, pagerState.pageCount - 1)
                    if (bounded != pagerState.currentPage) {
                        pagerState.animateScrollToPage(bounded)
                    }
                }
            }
        }

        // --- Shared overlays and controller state ---
        val bookmarkSheetState = rememberModalBottomSheetState()
        val tabListSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var showTabListSheet by rememberSaveable { mutableStateOf(false) }
        var showUrlDialog by rememberSaveable { mutableStateOf(false) }
        var urlError by rememberSaveable { mutableStateOf<String?>(null) }
        var isUrlValidating by rememberSaveable { mutableStateOf(false) }
        val invalidUrlMessage = stringResource(R.string.invalid_url)
        val coroutineScope = rememberCoroutineScope()

        // PendingMissingではsettled pageを優先し、selection keyを直接表示に使わない。
        val settledPage = pagerState.settledPage
        val settledTab = tabs.getOrNull(settledPage)
        if (settledTab == null) {
            // タブ削除・並べ替え中の範囲外pageでは、別タブを暗黙に表示しない。
            Box(modifier = Modifier.fillMaxSize())
            return
        }
        val settledUiState by getUiState(settledTab).collectAsState()
        val settledTabKey = getKey(settledTab)
        val settledProgress = actionProgressStates.getOrPut(settledTabKey) { mutableStateOf(1f) }
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val controllerModifier = Modifier.scrollable(
            state = pagerState,
            orientation = Orientation.Horizontal,
            enabled = settledUiState.isTabSwipeEnabled,
            reverseDirection = isRtl,
            flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
        )

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    bottomBar(
                        settledTab,
                        settledUiState,
                        settledProgress.value,
                        { showTabListSheet = true },
                        controllerModifier,
                    ) { modifier ->
                        PagerTitleCards(
                            modifier = modifier,
                            pagerState = pagerState,
                            tabs = tabs,
                            getUiState = getUiState,
                            getKey = getKey,
                            getActionProgress = { tab ->
                                actionProgressStates.getOrPut(getKey(tab)) { mutableStateOf(1f) }.value
                            },
                            titleCard = titleCard,
                        )
                    }
                },
            ) { innerPadding ->
                HorizontalPager(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    state = pagerState,
                    key = { page -> getKey(tabs[page]) },
                    userScrollEnabled = false,
                ) { page ->
                    val tab = tabs[page]
                    val uiState by getUiState(tab).collectAsState()
                    val tabKey = getKey(tab)
                    val listState = remember(tabKey) {
                        LazyListState(
                            firstVisibleItemIndex = getScrollIndex(tab),
                            firstVisibleItemScrollOffset = getScrollOffset(tab),
                        )
                    }
                    val isActive = pagerState.settledPage == page

                    ObserveScrollPositionPersistence(
                        tabKey = tabKey,
                        listState = listState,
                        isActive = isActive,
                        onSave = { index, offset -> updateScrollPosition(tab, index, offset) },
                    )

                    val bottomBehavior = bottomBarScrollBehavior?.invoke(listState)
                    val actionProgressState = actionProgressStates.getOrPut(tabKey) { mutableStateOf(1f) }
                    val actionVisibility = rememberBottomBarActionVisibility(
                        progress = actionProgressState,
                        scrollEnabled = bottomBarActionVisibilityEnabled,
                    )
                    val contentModifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(actionVisibility.nestedScrollConnection)
                        .let { modifier ->
                            bottomBehavior?.let { modifier.nestedScroll(it.nestedScrollConnection) } ?: modifier
                        }

                    content(
                        tab,
                        uiState,
                        listState,
                        contentModifier,
                        navController,
                        { showTabListSheet = true },
                        {
                            urlError = null
                            showUrlDialog = true
                        },
                    )
                }
            }

            BookmarkSheetHost(
                sheetState = bookmarkSheetState,
                holder = getBookmarkSheetHolder(settledTab),
                uiState = settledUiState.bookmarkSheetState,
            )
            // 現在settle済みタブのoverlayをScaffoldの後ろに描画し、固定barを覆う。
            optionalSheetContent(settledTab, settledUiState)

            if (showTabListSheet) {
                val initialPage = when (route) {
                    is AppRoute.Thread -> 1
                    else -> 0
                }
                TabsBottomSheet(
                    sheetState = tabListSheetState,
                    tabSessionStore = tabSessionStore,
                    navController = navController,
                    onDismissRequest = { showTabListSheet = false },
                    initialPage = initialPage,
                    currentScreenRoute = route,
                )
            }

            if (showUrlDialog) {
                UrlOpenDialog(
                    onDismissRequest = {
                        showUrlDialog = false
                        urlError = null
                    },
                    isError = urlError != null,
                    errorMessage = urlError,
                    isValidating = isUrlValidating,
                    onValueChange = {
                        if (urlError != null) urlError = null
                    },
                    onOpen = { url ->
                        isUrlValidating = true
                        val resolved = resolveUrl(url)
                        if (resolved is ResolvedUrl.ItestBoard) {
                            urlError = null
                            coroutineScope.launch {
                                try {
                                    val host = tabSessionStore.resolveBoardHost(
                                        boardKey = resolved.boardKey,
                                        sourceUrl = resolved.rawUrl,
                                    )
                                    if (host != null) {
                                        val boardUrl = "https://$host/${resolved.boardKey}/"
                                        val normalizedRoute = tabSessionStore.normalizeBoardRouteForNavigation(
                                            AppRoute.Board(boardName = boardUrl, boardUrl = boardUrl),
                                        )
                                        tabSessionStore.registerAndSelectBoardRoute(normalizedRoute)
                                        navController.showBoardScreenForTabSelection(
                                            currentScreenRoute = route,
                                            route = normalizedRoute,
                                        )
                                        urlError = null
                                        showUrlDialog = false
                                    } else {
                                        urlError = invalidUrlMessage
                                    }
                                } finally {
                                    isUrlValidating = false
                                }
                            }
                            return@UrlOpenDialog
                        }
                        if (resolved is ResolvedUrl.Thread) {
                            coroutineScope.launch {
                                val boardUrl = "https://${resolved.host}/${resolved.boardKey}/"
                                val normalizedRoute = tabSessionStore.normalizeThreadRouteForNavigation(
                                    AppRoute.Thread(
                                        threadKey = resolved.threadKey,
                                        boardUrl = boardUrl,
                                        boardName = resolved.boardKey,
                                        threadTitle = null,
                                    ),
                                )
                                val index = tabSessionStore.registerAndSelectThreadRoute(normalizedRoute)
                                if (index < 0) {
                                    urlError = invalidUrlMessage
                                    isUrlValidating = false
                                    return@launch
                                }
                                navController.showThreadScreenForTabSelection(
                                    currentScreenRoute = route,
                                    route = normalizedRoute,
                                )
                                urlError = null
                                showUrlDialog = false
                                isUrlValidating = false
                            }
                            return@UrlOpenDialog
                        }
                        if (resolved is ResolvedUrl.Board) {
                            coroutineScope.launch {
                                val boardUrl = "https://${resolved.host}/${resolved.boardKey}/"
                                val normalizedRoute = tabSessionStore.normalizeBoardRouteForNavigation(
                                    AppRoute.Board(boardName = boardUrl, boardUrl = boardUrl),
                                )
                                tabSessionStore.registerAndSelectBoardRoute(normalizedRoute)
                                navController.showBoardScreenForTabSelection(
                                    currentScreenRoute = route,
                                    route = normalizedRoute,
                                )
                                urlError = null
                                showUrlDialog = false
                                isUrlValidating = false
                            }
                            return@UrlOpenDialog
                        }
                        urlError = invalidUrlMessage
                        isUrlValidating = false
                    },
                )
            }
        }
    } else if (displayDecision is TabDisplayDecision.Loading) {
        // 初回 canonical snapshot 前だけローディング表示を出す。
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        // Empty は onEmptyTabs の navigation に委譲し、tab content は構成しない。
        Box(modifier = Modifier.fillMaxSize())
    }
}

/**
 * 本文 Pager の連続位置から、タイトルカードの表示列を構成する。
 *
 * 表示対象は現在ページと前後ページに限定し、同じ PagerState のページ距離で移動させる。
 */
@Composable
private fun <TabInfo : Any, Key : Any, UiState : BaseUiState<UiState>> PagerTitleCards(
    modifier: Modifier,
    pagerState: androidx.compose.foundation.pager.PagerState,
    tabs: List<TabInfo>,
    getUiState: (TabInfo) -> StateFlow<UiState>,
    getKey: (TabInfo) -> Key,
    getActionProgress: (TabInfo) -> Float,
    titleCard: @Composable (TabInfo, UiState, Float, Modifier) -> Unit,
) {
    // --- Visible page window ---
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        val pageDistance = pagerState.layoutInfo.pageSize.toFloat()
        val visiblePages = pagerTitlePageRange(
            currentPage = pagerState.currentPage,
            pageCount = tabs.size,
        )

        // --- Page-specific title cards ---
        for (page in visiblePages) {
            val tab = tabs[page]
            val uiState by getUiState(tab).collectAsState()
            val tabKey = getKey(tab)
            val actionProgress = getActionProgress(tab)
            key(tabKey) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Pagerと同じ一ページ分の距離でカードを連続移動させる。
                            translationX = pagerState.getOffsetDistanceInPages(page) * pageDistance *
                                if (isRtl) -1f else 1f
                        },
                ) {
                    titleCard(
                        tab,
                        uiState,
                        actionProgress,
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * 現在ページ前後のタイトルカードを描画する範囲へ制限する。
 *
 * page削除・reorder直後にPagerが一時的な範囲外indexを返した場合は空範囲を返し、先頭ページへ暗黙に戻さない。
 */
internal fun pagerTitlePageRange(currentPage: Int, pageCount: Int): IntRange {
    if (pageCount <= 0 || currentPage !in 0 until pageCount) return 0 until 0

    return (currentPage - 1).coerceAtLeast(0)..(currentPage + 1).coerceAtMost(pageCount - 1)
}
