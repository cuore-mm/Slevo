package com.websarva.wings.android.slevo.ui.bbsroute

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.websarva.wings.android.slevo.R

import kotlin.math.abs

/**
 * 板/スレ共通のタブUIと画面内シートを提供する。
 *
 * URL入力ダイアログは検証失敗時にエラー表示し、閉じずに再入力させる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <TabInfo : Any, UiState : BaseUiState<UiState>> BbsRouteScaffold(
    route: AppRoute,
    tabSessionStore: TabSessionStore,
    navController: NavHostController,
    isTabsLoaded: Boolean,
    onEmptyTabs: () -> Unit,
    openTabs: List<TabInfo>,
    selectedTabKey: Any?,
    getUiState: (TabInfo) -> StateFlow<UiState>,
    getBookmarkSheetHolder: (TabInfo) -> BookmarkBottomSheetStateHolder? = { null },
    getKey: (TabInfo) -> Any,
    getScrollIndex: (TabInfo) -> Int,
    getScrollOffset: (TabInfo) -> Int,
    updateScrollPosition: (tab: TabInfo, index: Int, offset: Int) -> Unit,
    onTabSelected: (TabInfo) -> Unit,
    animateToPageFlow: Flow<Int>? = null,
    bottomBar: @Composable (
        tabInfo: TabInfo,
        uiState: UiState,
        actionProgress: Float,
        openTabListSheet: () -> Unit,
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
    // このComposableはタブベースの画面レイアウトを提供します。
    // - HorizontalPagerで複数タブを左右にスワイプできる
    // - 各タブごとのUiState購読とリストのスクロール位置を保持/復元する
    // - 共通のボトムシートやダイアログを表示する

    LaunchedEffect(isTabsLoaded, openTabs) {
        if (isTabsLoaded && openTabs.isEmpty()) {
            onEmptyTabs()
        }
    }

    var cachedTabs by remember { mutableStateOf(openTabs) }
    // openTabsが空の場合に前回のタブ一覧をキャッシュしておくための処理
    if (openTabs.isNotEmpty()) {
        cachedTabs = openTabs
    }
    val tabs = if (openTabs.isNotEmpty()) {
        openTabs
    } else if (!isTabsLoaded) {
        cachedTabs
    } else {
        emptyList()
    }
    val selectedPage = remember(selectedTabKey, tabs) {
        deriveSelectedPageIndex(tabs = tabs, selectedKey = selectedTabKey, getKey = getKey)
    }
    val currentTabInfo = tabs.getOrNull(selectedPage)

    if (tabs.isNotEmpty()) {
        // Pagerの状態。ページ数はタブ数に応じて動的に提供される。
        val pagerState =
            rememberPagerState(initialPage = selectedPage, pageCount = { tabs.size })

        // selected key とタブ一覧から導出したページにのみ同期する。
        LaunchedEffect(selectedPage, tabs.size) {
            if (selectedPage in tabs.indices && pagerState.currentPage != selectedPage) {
                pagerState.scrollToPage(selectedPage)
            }
        }

        LaunchedEffect(pagerState.currentPage, tabs) {
            val page = pagerState.currentPage
            val currentTab = tabs.getOrNull(page) ?: return@LaunchedEffect
            if (getKey(currentTab) != selectedTabKey) {
                onTabSelected(currentTab)
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

        // 共通で使うボトムシートの状態
        val bookmarkSheetState = rememberModalBottomSheetState()
        val tabListSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        // --- Dialog state ---
        var showTabListSheet by rememberSaveable { mutableStateOf(false) }
        var showUrlDialog by rememberSaveable { mutableStateOf(false) }
        var urlError by rememberSaveable { mutableStateOf<String?>(null) }
        var isUrlValidating by rememberSaveable { mutableStateOf(false) }
        val invalidUrlMessage = stringResource(R.string.invalid_url)
        val coroutineScope = rememberCoroutineScope()

        val currentUiState = currentTabInfo?.let { tabInfo ->
            getUiState(tabInfo).collectAsState().value
        }
        val pagerUserScrollEnabled = currentUiState?.isTabSwipeEnabled ?: true

        HorizontalPager(
            state = pagerState,
            key = { page -> getKey(tabs[page]) },
            userScrollEnabled = pagerUserScrollEnabled
        ) { page ->
            val tab = tabs[page]
            val uiState by getUiState(tab).collectAsState()
            val bookmarkSheetUiState = uiState.bookmarkSheetState
            val bookmarkSheetHolder = getBookmarkSheetHolder(tab)


            val tabKey = getKey(tab)

            // 各タブごとにLazyListStateを復元する。キーに基づいてrememberするため
            // タブが切り替わっても正しいスクロール位置が再現される。
            val listState = remember(tabKey) {
                LazyListState(
                    firstVisibleItemIndex = getScrollIndex(tab),
                    firstVisibleItemScrollOffset = getScrollOffset(tab)
                )
            }

            val isActive = pagerState.currentPage == page

            ObserveScrollPositionPersistence(
                tabKey = tabKey,
                listState = listState,
                isActive = isActive,
                onSave = { index, offset ->
                    updateScrollPosition(tab, index, offset)
                },
            )

            val bottomBehavior = bottomBarScrollBehavior?.invoke(listState)
            val actionVisibility = rememberBottomBarActionVisibility(
                scrollEnabled = bottomBarActionVisibilityEnabled,
            )

            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier
                        .nestedScroll(actionVisibility.nestedScrollConnection)
                        .let { modifier ->
                            bottomBehavior?.let { modifier.nestedScroll(it.nestedScrollConnection) }
                                ?: modifier
                        },
                    bottomBar = {
                        bottomBar(
                            tab,
                            uiState,
                            actionVisibility.progress.value
                        ) {
                            showTabListSheet = true
                        }
                    }
                ) { innerPadding ->
                    val contentModifier = Modifier
                        .padding(innerPadding)
                        .consumeTabSwipeByDragDirection()
                    // 各画面の実際のコンテンツを呼び出す
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

                    // 共通のボトムシートとダイアログ
                    BookmarkSheetHost(
                        sheetState = bookmarkSheetState,
                        holder = bookmarkSheetHolder,
                        uiState = bookmarkSheetUiState,
                    )
                }
                // 各画面固有のシートやダイアログをScaffoldの外側に重ねることでボトムバーも覆う
                optionalSheetContent(tab, uiState)
            }
        }

        if (showTabListSheet) {
            // ルートに応じてタブ選択シートの初期ページを設定
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
                    if (urlError != null) {
                        urlError = null
                    }
                },
                onOpen = { url ->
                    isUrlValidating = true
                    val resolved = resolveUrl(url)
                    // --- itest board handling ---
                    if (resolved is ResolvedUrl.ItestBoard) {
                        // itest URLはホスト解決が必要なため非同期で処理する。
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
                                        AppRoute.Board(
                                            boardName = boardUrl,
                                            boardUrl = boardUrl,
                                        )
                                    )
                                    tabSessionStore.registerAndSelectBoardRoute(normalizedRoute)
                                    navController.showBoardScreenForTabSelection(
                                        currentScreenRoute = route,
                                        route = normalizedRoute,
                                    )
                                    urlError = null
                                    showUrlDialog = false
                                } else {
                                    // URL解析に失敗したため、エラーを表示して閉じない。
                                    urlError = invalidUrlMessage
                                }
                            } finally {
                                isUrlValidating = false
                            }
                        }
                        return@UrlOpenDialog
                    }
                    // --- Thread URL handling ---
                    if (resolved is ResolvedUrl.Thread) {
                        coroutineScope.launch {
                            val boardUrl = "https://${resolved.host}/${resolved.boardKey}/"
                            val normalizedRoute = tabSessionStore.normalizeThreadRouteForNavigation(
                                AppRoute.Thread(
                                    threadKey = resolved.threadKey,
                                    boardUrl = boardUrl,
                                    boardName = resolved.boardKey,
                                    threadTitle = null
                                )
                            )
                            tabSessionStore.registerAndSelectThreadRoute(normalizedRoute)
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
                    // --- Board URL handling ---
                    if (resolved is ResolvedUrl.Board) {
                        coroutineScope.launch {
                            val boardUrl = "https://${resolved.host}/${resolved.boardKey}/"
                            val normalizedRoute = tabSessionStore.normalizeBoardRouteForNavigation(
                                AppRoute.Board(
                                    boardName = boardUrl,
                                    boardUrl = boardUrl,
                                )
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
                    // --- Invalid URL ---
                    // URL解析に失敗したため、エラーを表示して閉じない。
                    urlError = invalidUrlMessage
                    isUrlValidating = false
                }
            )
        }
    } else {
        // 表示可能なタブがない場合はローディング表示を出す
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

/**
 * selected key とタブ一覧から現在表示すべきページ index を導出する。
 */
internal fun <TabInfo : Any> deriveSelectedPageIndex(
    tabs: List<TabInfo>,
    selectedKey: Any?,
    getKey: (TabInfo) -> Any,
): Int {
    if (tabs.isEmpty()) return -1
    if (selectedKey == null) return 0
    return tabs.indexOfFirst { getKey(it) == selectedKey }
        .takeIf { it >= 0 }
        ?: 0
}

/**
 * 本文領域のドラッグ開始方向を分類し、タブ切り替えの誤伝播を抑止する。
 *
 * 横優勢はジェスチャー用に消費し、縦優勢はLazyColumn側へ委譲する。
 */
private fun Modifier.consumeTabSwipeByDragDirection(): Modifier {
    return pointerInput(Unit) {
        awaitEachGesture {
            // --- Setup ---
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Main,
            )
            val pointerId = down.id
            var dragLock: DragLock? = null
            val touchSlop = viewConfiguration.touchSlop
            var totalOffset = Offset.Zero
            var pendingHorizontalConsume = false

            // --- Touch slop detection (LazyColumn感に合わせた軸優先判定) ---
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
                if (!change.pressed) {
                    // Guard: ポインタが離れたら終了する。
                    return@awaitEachGesture
                }
                val delta = change.position - change.previousPosition
                if (delta == Offset.Zero) {
                    continue
                }
                totalOffset += delta

                val absX = abs(totalOffset.x)
                val absY = abs(totalOffset.y)
                if (absX >= touchSlop || absY >= touchSlop) {
                    // 縦横が同時到達した場合は縦を優先する。
                    dragLock = if (absY >= absX) DragLock.Vertical else DragLock.Horizontal
                    pendingHorizontalConsume = dragLock == DragLock.Horizontal
                }

                if (dragLock != null) {
                    break
                }
            }

            if (dragLock == null) {
                // Guard: 方向が確定していない場合は処理を終了する。
                return@awaitEachGesture
            }

            // --- Drag handling ---
            if (dragLock == DragLock.Horizontal) {
                // 横開始: 子要素のジェスチャー処理を優先し、MainでPagerだけを遮断する。
                if (pendingHorizontalConsume) {
                    // Guard: slop超過の初回移動をMainで消費してPagerの掴みを防ぐ。
                    val event = awaitPointerEvent(pass = PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == pointerId }
                    change?.consume()
                    pendingHorizontalConsume = false
                }
                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
                    if (!change.pressed) {
                        break
                    }
                    change.consume()
                }
            } else {
                // 縦開始: LazyColumn側へ委譲し、横成分が出た場合のみPagerを遮断する。
                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
                    if (!change.pressed) {
                        break
                    }
                    val delta = change.position - change.previousPosition
                    if (abs(delta.x) > abs(delta.y)) {
                        // Guard: 縦中の横ジッターがPagerへ伝播するのを防ぐ。
                        change.consume()
                    }
                }
            }
        }
    }
}

/**
 * ドラッグ開始方向の固定分類を表す。
 */
private enum class DragLock {
    Horizontal,
    Vertical,
}
