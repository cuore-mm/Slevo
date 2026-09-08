package com.websarva.wings.android.slevo.ui.bbsroute

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolder
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkSheetHost
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.showBoardScreenForTabSelection
import com.websarva.wings.android.slevo.ui.navigation.showThreadScreenForTabSelection
import com.websarva.wings.android.slevo.ui.tabs.TabsBottomSheet
import com.websarva.wings.android.slevo.ui.tabs.dialog.UrlOpenDialog
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.util.ResolvedUrl
import com.websarva.wings.android.slevo.ui.util.rememberBottomBarActionVisibility
import com.websarva.wings.android.slevo.ui.util.resolveUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

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
        isSharedTransitionCandidate: Boolean,
        modifier: Modifier,
        openTabListSheet: () -> Unit,
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
        contentPadding: PaddingValues,
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
        val settledProgress =
            actionProgressStates.getOrPut(settledTabKey) { mutableFloatStateOf(1f) }
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val rubberBandResistancePx = with(LocalDensity.current) {
            PAGER_RUBBER_BAND_RESISTANCE.toPx()
        }
        val pagerOverscrollEffect = remember(pagerState, rubberBandResistancePx, tabs.size) {
            PagerRubberBandOverscrollEffect(
                scope = coroutineScope,
                resistanceLimitPx = rubberBandResistancePx,
            )
        }
        LaunchedEffect(pagerOverscrollEffect, settledUiState.isTabSwipeEnabled) {
            if (!settledUiState.isTabSwipeEnabled) {
                pagerOverscrollEffect.reset()
            }
        }
        val controllerState: ScrollableState = remember(pagerState, tabs.size) {
            if (tabs.size == 1) {
                // PagerState reports no scrollable direction for one tab, so expose both directions
                // to let the overscroll effect receive the boundary gesture.
                PagerRubberBandScrollableState(pagerState)
            } else {
                pagerState
            }
        }
        val controllerModifier = Modifier
            .overscroll(pagerOverscrollEffect)
            .scrollable(
                state = controllerState,
                orientation = Orientation.Horizontal,
                overscrollEffect = pagerOverscrollEffect,
                enabled = settledUiState.isTabSwipeEnabled,
                reverseDirection = !isRtl,
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
                                actionProgressStates.getOrPut(getKey(tab)) { mutableFloatStateOf(1f) }.value
                            },
                            overscrollOffsetPx = { pagerOverscrollEffect.offsetPx },
                            titleCard = titleCard,
                            openTabListSheet = { showTabListSheet = true },
                        )
                    }
                },
                ) { innerPadding ->
                HorizontalPager(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .graphicsLayer {
                            translationX = pagerOverscrollEffect.offsetPx
                        },
                    state = pagerState,
                    key = { page -> getKey(tabs[page]) },
                    pageSpacing = 32.dp,
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
                    val actionProgressState =
                        actionProgressStates.getOrPut(tabKey) { mutableFloatStateOf(1f) }
                    val actionVisibility = rememberBottomBarActionVisibility(
                        progress = actionProgressState,
                        scrollEnabled = bottomBarActionVisibilityEnabled,
                    )
                    val contentModifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(actionVisibility.nestedScrollConnection)
                        .let { modifier ->
                            bottomBehavior?.let { modifier.nestedScroll(it.nestedScrollConnection) }
                                ?: modifier
                        }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(
                                elevation = 4.dp,
                                shape = RectangleShape,
                                clip = false,
                            )
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        content(
                            tab,
                            uiState,
                            listState,
                            contentModifier,
                            innerPadding,
                            navController,
                            { showTabListSheet = true },
                            {
                                urlError = null
                                showUrlDialog = true
                            },
                        )
                    }
                }
            }
            BbsRouteStatusBarProtection()

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
                                        val normalizedRoute =
                                            tabSessionStore.normalizeBoardRouteForNavigation(
                                                AppRoute.Board(
                                                    boardName = boardUrl,
                                                    boardUrl = boardUrl
                                                ),
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
                                val normalizedRoute =
                                    tabSessionStore.normalizeThreadRouteForNavigation(
                                        AppRoute.Thread(
                                            threadKey = resolved.threadKey,
                                            boardUrl = boardUrl,
                                            boardName = resolved.boardKey,
                                            threadTitle = null,
                                        ),
                                    )
                                val index =
                                    tabSessionStore.registerAndSelectThreadRoute(normalizedRoute)
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
                                val normalizedRoute =
                                    tabSessionStore.normalizeBoardRouteForNavigation(
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
 * 板・スレッド画面のステータスバー領域に半透明の視認性保護背景を描画する。
 *
 * 背景とコンテンツのedge-to-edge描画は維持し、ステータスバーのアイコンと時刻だけを
 * テーマ連動のsurface色で読みやすくする。
 */
@Composable
private fun BbsRouteStatusBarProtection() {
    val surfaceColor = MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        surfaceColor.copy(alpha = 0.92f),
                        surfaceColor.copy(alpha = 0.76f),
                    )
                )
            )
    )
}

/**
 * 本文 Pager の連続位置から、タイトルカードの表示列を構成する。
 *
 * 表示対象は現在ページと前後ページに限定し、本文とタイトルのviewportで表示進行率が
 * 一致するページ距離で移動させる。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <TabInfo : Any, Key : Any, UiState : BaseUiState<UiState>> PagerTitleCards(
    modifier: Modifier,
    pagerState: PagerState,
    tabs: List<TabInfo>,
    getUiState: (TabInfo) -> StateFlow<UiState>,
    getKey: (TabInfo) -> Key,
    getActionProgress: (TabInfo) -> Float,
    overscrollOffsetPx: () -> Float = { 0f },
    titleCard: @Composable (TabInfo, UiState, Float, Boolean, Modifier, () -> Unit) -> Unit,
    openTabListSheet: () -> Unit,
) {
    // --- Visible page window ---
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.largeIncreased),
    ) {
        val visiblePages = pagerTitlePageRange(
            currentPage = pagerState.currentPage,
            pageCount = tabs.size,
        )

        // --- Page-specific title cards ---
        for (page in visiblePages) {
            val tab = tabs[page]
            val tabKey = getKey(tab)
            key(tabKey) {
                PagerTitleCardPage(
                    page = page,
                    tab = tab,
                    pagerState = pagerState,
                    isRtl = isRtl,
                    getUiState = getUiState,
                    getActionProgress = getActionProgress,
                    overscrollOffsetPx = overscrollOffsetPx,
                    titleCard = titleCard,
                    openTabListSheet = openTabListSheet,
                )
            }
        }
    }
}

/**
 * stable keyで識別された1タブ分のタイトルカードを描画する。
 *
 * タブ固有のStateFlow購読とカード描画を同じkeyグループ内へ置き、描画windowの位置が
 * 別タブへ移動しても前のタブのUiStateを再利用しない。
 */
@Composable
private fun <TabInfo : Any, UiState : BaseUiState<UiState>> PagerTitleCardPage(
    page: Int,
    tab: TabInfo,
    pagerState: PagerState,
    isRtl: Boolean,
    getUiState: (TabInfo) -> StateFlow<UiState>,
    getActionProgress: (TabInfo) -> Float,
    overscrollOffsetPx: () -> Float,
    titleCard: @Composable (TabInfo, UiState, Float, Boolean, Modifier, () -> Unit) -> Unit,
    openTabListSheet: () -> Unit,
) {
    // --- Tab-specific state ---
    val uiState by getUiState(tab).collectAsState()
    val actionProgress = getActionProgress(tab)

    // --- Card rendering ---
    val canUseSharedTransition = isSharedTransitionCandidate(
        page = page,
        settledPage = pagerState.settledPage,
        isScrollInProgress = pagerState.isScrollInProgress,
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val titlePageDistance = calculateTitlePageDistance(
                    titleViewportWidthPx = size.width,
                    bodyPageSizePx = pagerState.layoutInfo.pageSize,
                    bodyPageSpacingPx = pagerState.layoutInfo.pageSpacing,
                )
                val titleOverscrollOffset = calculateTitleOverscrollOffset(
                    overscrollOffsetPx = overscrollOffsetPx(),
                    titleViewportWidthPx = size.width,
                    bodyPageSizePx = pagerState.layoutInfo.pageSize,
                )
                // 本文とタイトルviewport内の表示進行率を揃えて移動させる。
                translationX =
                    pagerState.getOffsetDistanceInPages(page) * titlePageDistance *
                            (if (isRtl) -1f else 1f) + titleOverscrollOffset
            },
    ) {
        titleCard(
            tab,
            uiState,
            actionProgress,
            canUseSharedTransition,
            Modifier.fillMaxSize(),
            openTabListSheet,
        )
    }
}

/**
 * Pager内のタイトルカードを画面種別切替のShared Transition対象にできるか判定する。
 *
 * settle済みページ以外と横ドラッグ中は、隣接タブがdestinationボタンへ誤照合されないよう
 * 対象外とする。
 */
internal fun isSharedTransitionCandidate(
    page: Int,
    settledPage: Int,
    isScrollInProgress: Boolean,
): Boolean = page == settledPage && !isScrollInProgress

/**
 * 本文Pagerのページ進行をタイトルviewportの移動距離へ変換する。
 *
 * タイトルviewportが固定ボタン分だけ狭くても、本文のページピッチに対する表示進行率が
 * 一致するように移動距離を比例計算する。初期レイアウトで本文幅が未確定の場合は0を返す。
 */
internal fun calculateTitlePageDistance(
    titleViewportWidthPx: Float,
    bodyPageSizePx: Int,
    bodyPageSpacingPx: Int,
): Float {
    if (titleViewportWidthPx <= 0f || bodyPageSizePx <= 0) {
        return 0f
    }

    return titleViewportWidthPx * (bodyPageSizePx + bodyPageSpacingPx) / bodyPageSizePx
}

/**
 * 本文の境界変位をタイトルviewportの幅へ比例変換する。
 *
 * タイトルカードが本文と同じ表示進行率で境界へ追従するように、タイトルviewport幅を本文
 * Pagerのページ幅で割る。初期レイアウトでどちらかの幅が未確定の場合は0を返す。
 */
internal fun calculateTitleOverscrollOffset(
    overscrollOffsetPx: Float,
    titleViewportWidthPx: Float,
    bodyPageSizePx: Int,
): Float {
    if (titleViewportWidthPx <= 0f || bodyPageSizePx <= 0) {
        return 0f
    }

    return overscrollOffsetPx * titleViewportWidthPx / bodyPageSizePx
}

/**
 * 現在ページ前後のタイトルカードを描画する範囲へ制限する。
 *
 * page削除・reorder直後にPagerが一時的な範囲外indexを返した場合は空範囲を返し、先頭ページへ暗黙に戻さない。
 */
internal fun pagerTitlePageRange(currentPage: Int, pageCount: Int): IntRange {
    if (pageCount <= 0 || currentPage !in 0 until pageCount) return IntRange.EMPTY

    return (currentPage - 1).coerceAtLeast(0)..(currentPage + 1).coerceAtMost(pageCount - 1)
}

private val PAGER_RUBBER_BAND_RESISTANCE = 64.dp
