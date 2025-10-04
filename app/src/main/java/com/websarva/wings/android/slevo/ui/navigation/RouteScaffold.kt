package com.websarva.wings.android.slevo.ui.navigation

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.ui.board.BoardUiState
import com.websarva.wings.android.slevo.ui.board.BoardViewModel
import com.websarva.wings.android.slevo.ui.common.BaseUiState
import com.websarva.wings.android.slevo.ui.common.BaseViewModel
import com.websarva.wings.android.slevo.ui.common.bookmark.AddGroupDialog
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheet
import com.websarva.wings.android.slevo.ui.common.bookmark.DeleteGroupDialog
import com.websarva.wings.android.slevo.ui.common.bookmark.SingleBookmarkState
import com.websarva.wings.android.slevo.ui.tabs.TabsBottomSheet
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.thread.state.ThreadUiState
import com.websarva.wings.android.slevo.ui.thread.viewmodel.ThreadViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun <TabInfo : Any, UiState : BaseUiState<UiState>, ViewModel : BaseViewModel<UiState>> RouteScaffold(
    route: AppRoute,
    tabsViewModel: TabsViewModel,
    navController: NavHostController,
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
    bottomBar: @Composable (
        viewModel: ViewModel,
        uiState: UiState,
        scrollBehavior: BottomAppBarScrollBehavior?,
        openTabListSheet: () -> Unit,
    ) -> Unit,
    content: @Composable (viewModel: ViewModel, uiState: UiState, listState: LazyListState, modifier: Modifier, navController: NavHostController) -> Unit,
    bottomBarScrollBehavior: (@Composable (LazyListState) -> BottomAppBarScrollBehavior)? = null,
    optionalSheetContent: @Composable (viewModel: ViewModel, uiState: UiState) -> Unit = { _, _ -> }
) {
    // このComposableはタブベースの画面レイアウトを提供します。
    // - HorizontalPagerで複数タブを左右にスワイプできる
    // - 各タブごとにViewModelとリストのスクロール位置を保持/復元する
    // - 共通のボトムシートやダイアログを表示する

    var cachedTabs by remember { mutableStateOf(openTabs) }
    // openTabsが空の場合に前回のタブ一覧をキャッシュしておくための処理
    if (openTabs.isNotEmpty()) {
        cachedTabs = openTabs
    }
    val tabs = openTabs.ifEmpty { cachedTabs }
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

        // 共通で使うボトムシートの状態
        val bookmarkSheetState = rememberModalBottomSheetState()
        val tabListSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var showTabListSheet by rememberSaveable { mutableStateOf(false) }

        val pagerUserScrollEnabled = when (
            val currentUiState = currentTabInfo?.let { tabInfo ->
                val currentViewModel = getViewModel(tabInfo)
                currentViewModel.uiState.collectAsState().value
            }
        ) {
            is BoardUiState -> !currentUiState.isSearchActive
            is ThreadUiState -> !currentUiState.isSearchMode
            else -> true
        }

        HorizontalPager(
            state = pagerState,
            key = { page -> getKey(tabs[page]) },
            userScrollEnabled = pagerUserScrollEnabled
        ) { page ->
            val tab = tabs[page]
            val viewModel = getViewModel(tab)
            val uiState by viewModel.uiState.collectAsState()
            // Board / Thread 用のブックマーク状態を統一的に取得
            val bookmarkState = (uiState as? BoardUiState)?.singleBookmarkState
                ?: (uiState as? ThreadUiState)?.singleBookmarkState
                ?: SingleBookmarkState()


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
            val swipeBlockerState = rememberDraggableState { _ -> }
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
                    .draggable(
                        state = swipeBlockerState,
                        orientation = Orientation.Horizontal,
                        enabled = true
                    )
                // 各画面の実際のコンテンツを呼び出す
                content(
                    viewModel,
                    uiState,
                    listState,
                    contentModifier,
                    navController
                )

                // 共通のボトムシートとダイアログ
                if (bookmarkState.showBookmarkSheet) {
                    BookmarkBottomSheet(
                        sheetState = bookmarkSheetState,
                        onDismissRequest = {
                            // ViewModelの型に応じて適切なクローズ処理を呼ぶ
                            (viewModel as? BoardViewModel)?.closeBookmarkSheet()
                                ?: (viewModel as? ThreadViewModel)?.closeBookmarkSheet()
                        },
                        groups = bookmarkState.groups,
                        selectedGroupId = bookmarkState.selectedGroup?.id,
                        onGroupSelected = {
                            (viewModel as? BoardViewModel)?.saveBookmark(it)
                                ?: (viewModel as? ThreadViewModel)?.saveBookmark(it)
                        },
                        onUnbookmarkRequested = {
                            (viewModel as? BoardViewModel)?.unbookmarkBoard()
                                ?: (viewModel as? ThreadViewModel)?.unbookmarkBoard()
                        },
                        onAddGroup = {
                            (viewModel as? BoardViewModel)?.openAddGroupDialog()
                                ?: (viewModel as? ThreadViewModel)?.openAddGroupDialog()
                        },
                        onGroupLongClick = { group ->
                            (viewModel as? BoardViewModel)?.openEditGroupDialog(group)
                                ?: (viewModel as? ThreadViewModel)?.openEditGroupDialog(group)
                        }
                    )
                }

                if (bookmarkState.showAddGroupDialog) {
                    AddGroupDialog(
                        onDismissRequest = {
                            (viewModel as? BoardViewModel)?.closeAddGroupDialog()
                                ?: (viewModel as? ThreadViewModel)?.closeAddGroupDialog()
                        },
                        isEdit = bookmarkState.editingGroupId != null,
                        onConfirm = {
                            (viewModel as? BoardViewModel)?.confirmGroup()
                                ?: (viewModel as? ThreadViewModel)?.confirmGroup()
                        },
                        onDelete = {
                            (viewModel as? BoardViewModel)?.requestDeleteGroup()
                                ?: (viewModel as? ThreadViewModel)?.requestDeleteGroup()
                        },
                        onValueChange = {
                            (viewModel as? BoardViewModel)?.setEnteredGroupName(it)
                                ?: (viewModel as? ThreadViewModel)?.setEnteredGroupName(it)
                        },
                        enteredValue = bookmarkState.enteredGroupName,
                        onColorSelected = {
                            (viewModel as? BoardViewModel)?.setSelectedColor(it)
                                ?: (viewModel as? ThreadViewModel)?.setSelectedColor(it)
                        },
                        selectedColor = bookmarkState.selectedColor
                    )
                }

                if (bookmarkState.showDeleteGroupDialog) {
                    DeleteGroupDialog(
                        groupName = bookmarkState.deleteGroupName,
                        itemNames = bookmarkState.deleteGroupItems,
                        isBoard = bookmarkState.deleteGroupIsBoard,
                        onDismissRequest = {
                            (viewModel as? BoardViewModel)?.closeDeleteGroupDialog()
                                ?: (viewModel as? ThreadViewModel)?.closeDeleteGroupDialog()
                        },
                        onConfirm = {
                            (viewModel as? BoardViewModel)?.confirmDeleteGroup()
                                ?: (viewModel as? ThreadViewModel)?.confirmDeleteGroup()
                        }
                    )
                }
                // 各画面固有のシート
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
    } else {
        // 表示可能なタブがない場合はローディング表示を出す
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
