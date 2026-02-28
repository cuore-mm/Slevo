package com.websarva.wings.android.slevo.ui.bbsroute

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import kotlinx.coroutines.Job

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
    // - HorizontalPagerで複数タブを管理し、手動スワイプはボトムバーに限定する
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
        // --- Edge pull state ---
        var edgePullOffsetPx by remember { mutableFloatStateOf(0f) }
        var edgePullJob by remember { mutableStateOf<Job?>(null) }

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

        val coroutineScope = rememberCoroutineScope()

        // --- Bottom bar swipe ---
        // ボトムバーからPagerを操作するためのスワイプ設定
        val pagerFlingBehavior = PagerDefaults.flingBehavior(state = pagerState)
        val bottomBarDragState = rememberDraggableState { delta ->
            edgePullJob?.cancel()

            // reverseDirection と同じ挙動に合わせるため、ドラッグ量を反転して伝える。
            val dragDelta = -delta
            val isFirstPage = pagerState.currentPage == 0
            val isLastPage = pagerState.currentPage == pagerState.pageCount - 1
            val isEdgePull = (isFirstPage && dragDelta > 0f) || (isLastPage && dragDelta < 0f)

            if (isEdgePull) {
                // 端ページの引っ張り感を再現するために減衰させて反映する。
                val resistance = 0.28f
                val nextOffset = edgePullOffsetPx + dragDelta * resistance
                edgePullOffsetPx = nextOffset.coerceIn(-120f, 120f)
                return@rememberDraggableState
            }

            edgePullOffsetPx = 0f
            pagerState.dispatchRawDelta(dragDelta)
        }
        val bottomBarSwipeModifier = Modifier.draggable(
            state = bottomBarDragState,
            orientation = Orientation.Horizontal,
            enabled = tabs.size > 1,
            onDragStopped = { velocity ->
                if (abs(edgePullOffsetPx) > 0f) {
                    edgePullJob?.cancel()
                edgePullJob = coroutineScope.launch {
                    animate(
                        initialValue = edgePullOffsetPx,
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                        ) { value, _ ->
                            edgePullOffsetPx = value
                        }
                    }
                    // 端の引っ張り戻しのみ行うため、フリング処理は行わない。
                    return@draggable
                }

                coroutineScope.launch {
                    // reverseDirection を反映した速度でスナップ位置へフリングする。
                    pagerState.scroll {
                        with(pagerFlingBehavior) {
                            performFling(-velocity)
                        }
                    }
                }
            },
        )

        // 共通で使うボトムシートの状態
        val bookmarkSheetState = rememberModalBottomSheetState()
        val tabListSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        // --- Dialog state ---
        var showTabListSheet by rememberSaveable { mutableStateOf(false) }
        var showUrlDialog by rememberSaveable { mutableStateOf(false) }
        var urlError by rememberSaveable { mutableStateOf<String?>(null) }
        val invalidUrlMessage = stringResource(R.string.invalid_url)
        val tabsUiState by tabsViewModel.uiState.collectAsState()

        HorizontalPager(
            state = pagerState,
            key = { page -> getKey(tabs[page]) },
            userScrollEnabled = false,
            modifier = Modifier.graphicsLayer {
                translationX = edgePullOffsetPx
            }
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
                    Box(modifier = bottomBarSwipeModifier) {
                        bottomBar(
                            viewModel,
                            uiState,
                            bottomBehavior,
                        ) {
                            showTabListSheet = true
                        }
                    }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        // 各画面の実際のコンテンツを呼び出す
                        content(
                            viewModel,
                            uiState,
                            listState,
                            Modifier.fillMaxSize(),
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
