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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.ui.board.viewmodel.BoardViewModel
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkSheetHost
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.navigateToBoard
import com.websarva.wings.android.slevo.ui.navigation.navigateToThread
import com.websarva.wings.android.slevo.ui.tabs.TabsBottomSheet
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.tabs.UrlOpenDialog
import com.websarva.wings.android.slevo.ui.thread.viewmodel.ThreadViewModel
import com.websarva.wings.android.slevo.ui.util.ResolvedUrl
import com.websarva.wings.android.slevo.ui.util.resolveUrl
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.websarva.wings.android.slevo.R
import timber.log.Timber
import kotlin.math.abs

/**
 * 板/スレ共通のタブUIと画面内シートを提供する。
 *
 * URL入力ダイアログは検証失敗時にエラー表示し、閉じずに再入力させる。
 */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun <TabInfo : Any, UiState : BaseUiState<UiState>, ViewModel : BaseViewModel<UiState, *>> BbsRouteScaffold(
    route: AppRoute,
    tabsViewModel: TabsViewModel,
    navController: NavHostController,
    isTabsLoaded: Boolean,
    onEmptyTabs: () -> Unit,
    openTabs: List<TabInfo>,
    currentRoutePredicate: (TabInfo) -> Boolean,
    getViewModel: (TabInfo) -> ViewModel,
    getKey: (TabInfo) -> Any,
    getScrollIndex: (TabInfo) -> Int,
    getScrollOffset: (TabInfo) -> Int,
    initializeViewModel: (viewModel: ViewModel, tabInfo: TabInfo) -> Unit,
    updateScrollPosition: (viewModel: ViewModel, tab: TabInfo, index: Int, offset: Int) -> Unit,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    animateToPageFlow: Flow<Int>? = null,
    bottomBar: @Composable (
        viewModel: ViewModel,
        uiState: UiState,
        scrollBehavior: BottomAppBarScrollBehavior?,
        openTabListSheet: () -> Unit,
    ) -> Unit,
    content: @Composable (
        viewModel: ViewModel,
        uiState: UiState,
        listState: LazyListState,
        modifier: Modifier,
        navController: NavHostController,
        showBottomBar: (() -> Unit)?,
        openTabListSheet: () -> Unit,
        openUrlDialog: () -> Unit,
    ) -> Unit,
    bottomBarScrollBehavior: (@Composable (LazyListState) -> BottomAppBarScrollBehavior)? = null,
    optionalSheetContent: @Composable (viewModel: ViewModel, uiState: UiState) -> Unit = { _, _ -> }
) {
    // このComposableはタブベースの画面レイアウトを提供します。
    // - HorizontalPagerで複数タブを左右にスワイプできる
    // - 各タブごとにViewModelとリストのスクロール位置を保持/復元する
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
    Timber.d("tabs: $tabs")
    val currentTabInfo = tabs.find(currentRoutePredicate)

    if (tabs.isNotEmpty()) {
        // 初期ページの決定。routeやタブ数が変わったら再計算される。
        val initialPage = remember(route, tabs.size, currentTabInfo, currentPage) {
            when {
                currentPage in tabs.indices -> currentPage
                currentPage >= 0 -> currentPage.coerceIn(0, tabs.size - 1)
                currentTabInfo != null -> tabs.indexOf(currentTabInfo).takeIf { it >= 0 }
                    ?: 0

                else -> 0
            }
        }

        // Pagerの状態。ページ数はタブ数に応じて動的に提供される。
        val pagerState =
            rememberPagerState(initialPage = initialPage, pageCount = { tabs.size })

        // initialPage が現在のページと異なる場合は強制的に遷移する。
        LaunchedEffect(initialPage) {
            if (pagerState.currentPage != initialPage) {
                pagerState.scrollToPage(initialPage)
            }
        }

        LaunchedEffect(pagerState.currentPage) {
            onPageChange(pagerState.currentPage)
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
        val invalidUrlMessage = stringResource(R.string.invalid_url)
        val coroutineScope = rememberCoroutineScope()
        val tabsUiState by tabsViewModel.uiState.collectAsState()

        val currentUiState = currentTabInfo?.let { tabInfo ->
            val currentViewModel = getViewModel(tabInfo)
            currentViewModel.uiState.collectAsState().value
        }
        val pagerUserScrollEnabled = currentUiState?.isTabSwipeEnabled ?: true

        HorizontalPager(
            state = pagerState,
            key = { page -> getKey(tabs[page]) },
            userScrollEnabled = pagerUserScrollEnabled
        ) { page ->
            val tab = tabs[page]
            val viewModel = getViewModel(tab)
            val uiState by viewModel.uiState.collectAsState()
            val bookmarkSheetUiState = uiState.bookmarkSheetState
            val bookmarkSheetHolder = when (viewModel) {
                is BoardViewModel -> viewModel.bookmarkSheetHolder
                is ThreadViewModel -> viewModel.bookmarkSheetHolder
                else -> null
            }


            // 各タブごとにLazyListStateを復元する。キーに基づいてrememberするため
            // タブが切り替わっても正しいスクロール位置が再現される。
            val listState = remember(getKey(tab)) {
                LazyListState(
                    firstVisibleItemIndex = getScrollIndex(tab),
                    firstVisibleItemScrollOffset = getScrollOffset(tab)
                )
            }

            // タブ初回表示時にViewModelの初期処理を行うためのフラグ
            var hasInitialized by remember(getKey(tab)) { mutableStateOf(false) }
            val isActive = pagerState.currentPage == page
            LaunchedEffect(isActive, tab) {
                if (isActive && !hasInitialized) {
                    // 表示されたときに初期化処理を実行
                    initializeViewModel(viewModel, tab)
                    hasInitialized = true
                }
            }

            // リストのスクロール位置が変わったら一定時間デバウンスしてViewModelに保存する
            LaunchedEffect(listState, isActive) {
                if (isActive) {
                    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                        .debounce(200L)
                        .collectLatest { (index, offset) ->
                            // スクロール位置をViewModel側に伝える
                            updateScrollPosition(viewModel, tab, index, offset)
                        }
                }
            }

            val bottomBehavior = bottomBarScrollBehavior?.invoke(listState)
            val showBottomBar = bottomBehavior?.let { behavior ->
                {
                    behavior.state.heightOffset = 0f
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier
                        .let { modifier ->
                            bottomBehavior?.let { modifier.nestedScroll(it.nestedScrollConnection) }
                                ?: modifier
                        },
                    bottomBar = {
                        bottomBar(
                            viewModel,
                            uiState,
                            bottomBehavior
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
                        viewModel,
                        uiState,
                        listState,
                        contentModifier,
                        navController,
                        showBottomBar,
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
                optionalSheetContent(viewModel, uiState)
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
                tabsViewModel = tabsViewModel,
                navController = navController,
                onDismissRequest = { showTabListSheet = false },
                initialPage = initialPage,
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
                isValidating = tabsUiState.isUrlValidating,
                onValueChange = {
                    if (urlError != null) {
                        urlError = null
                    }
                },
                onOpen = { url ->
                    tabsViewModel.startUrlValidation()
                    val resolved = resolveUrl(url)
                    // --- itest board handling ---
                    if (resolved is ResolvedUrl.ItestBoard) {
                        // itest URLはホスト解決が必要なため非同期で処理する。
                        urlError = null
                        coroutineScope.launch {
                            try {
                                val host = tabsViewModel.resolveBoardHost(resolved.boardKey)
                                if (host != null) {
                                    val boardUrl = "https://$host/${resolved.boardKey}/"
                                    val route = AppRoute.Board(
                                        boardName = boardUrl,
                                        boardUrl = boardUrl,
                                    )
                                    navController.navigateToBoard(
                                        route = route,
                                        tabsViewModel = tabsViewModel,
                                    )
                                    urlError = null
                                    showUrlDialog = false
                                } else {
                                    // URL解析に失敗したため、エラーを表示して閉じない。
                                    urlError = invalidUrlMessage
                                }
                            } finally {
                                tabsViewModel.finishUrlValidation()
                            }
                        }
                        return@UrlOpenDialog
                    }
                    // --- Thread URL handling ---
                    if (resolved is ResolvedUrl.Thread) {
                        val boardUrl = "https://${resolved.host}/${resolved.boardKey}/"
                        val route = AppRoute.Thread(
                            threadKey = resolved.threadKey,
                            boardUrl = boardUrl,
                            boardName = resolved.boardKey,
                            threadTitle = url
                        )
                        navController.navigateToThread(
                            route = route,
                            tabsViewModel = tabsViewModel,
                        )
                        urlError = null
                        showUrlDialog = false
                        tabsViewModel.finishUrlValidation()
                        return@UrlOpenDialog
                    }
                    // --- Board URL handling ---
                    if (resolved is ResolvedUrl.Board) {
                        val boardUrl = "https://${resolved.host}/${resolved.boardKey}/"
                        val route = AppRoute.Board(
                            boardName = boardUrl,
                            boardUrl = boardUrl,
                        )
                        navController.navigateToBoard(
                            route = route,
                            tabsViewModel = tabsViewModel,
                        )
                        urlError = null
                        showUrlDialog = false
                        tabsViewModel.finishUrlValidation()
                        return@UrlOpenDialog
                    }
                    // --- Invalid URL ---
                    // URL解析に失敗したため、エラーを表示して閉じない。
                    urlError = invalidUrlMessage
                    tabsViewModel.finishUrlValidation()
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
 * 本文領域のドラッグ開始方向を分類し、タブ切り替えの誤伝播を抑止する。
 *
 * 横優勢はジェスチャー用に消費し、縦優勢は子要素へ任せ、斜めは無効入力として消費する。
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
            val touchSlop = viewConfiguration.touchSlop
            var totalOffset = Offset.Zero
            var dragLock: DragLock? = null
            var isPointerReleased = false

            // --- Touch slop detection ---
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
                if (!change.pressed) {
                    // Guard: ポインタが離れたら終了する。
                    isPointerReleased = true
                    break
                }
                val delta = change.position - change.previousPosition
                if (delta == Offset.Zero) {
                    continue
                }
                totalOffset += delta

                if (dragLock == null && totalOffset.getDistance() >= touchSlop) {
                    dragLock = detectDragLock(totalOffset)
                }
                if (dragLock == DragLock.Horizontal || dragLock == DragLock.Diagonal) {
                    // 横優勢・斜めの開始はPagerへ渡さないため先に消費する。
                    change.consume()
                }
                if (dragLock != null) {
                    break
                }
            }

            if (isPointerReleased || dragLock == null) {
                // Guard: 入力系列が終了している場合は後続処理を行わない。
                return@awaitEachGesture
            }

            // --- Drag handling ---
            when (dragLock) {
                DragLock.Horizontal -> {
                    // 横開始: ジェスチャー処理を保ちつつPagerへ伝播させない。
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
                        if (!change.pressed) {
                            break
                        }
                        change.consume()
                    }
                }

                DragLock.Vertical -> {
                    // 縦開始: 子要素のスクロールに任せる。
                }

                DragLock.Diagonal, null -> {
                    // 斜め開始は無効入力として消費を継続する。
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
                        if (!change.pressed) {
                            break
                        }
                        change.consume()
                    }
                }
            }
        }
    }
}

/**
 * ドラッグ開始の方向を判定する。
 *
 * 横/縦の優勢がつかない場合は斜めとして扱う。
 */
private fun detectDragLock(delta: Offset): DragLock? {
    if (delta == Offset.Zero) {
        // Guard: 移動量が無い場合は方向を確定しない。
        return null
    }
    val absX = abs(delta.x)
    val absY = abs(delta.y)
    return when {
        absX > absY * DRAG_AXIS_RATIO -> DragLock.Horizontal
        absY > absX * DRAG_AXIS_RATIO -> DragLock.Vertical
        else -> DragLock.Diagonal
    }
}

/**
 * ドラッグ開始方向の固定分類を表す。
 */
private enum class DragLock {
    Horizontal,
    Vertical,
    Diagonal,
}

// --- Drag direction tuning ---
private const val DRAG_AXIS_RATIO = 1.2f
