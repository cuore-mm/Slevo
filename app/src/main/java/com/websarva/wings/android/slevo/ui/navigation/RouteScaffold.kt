package com.websarva.wings.android.slevo.ui.navigation

import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun <TabInfo : Any, UiState : BaseUiState<UiState>, ViewModel : BaseViewModel<UiState>> RouteScaffold(
    route: AppRoute,
    tabsViewModel: TabsViewModel,
    navController: NavHostController,
    openDrawer: () -> Unit,
    openTabs: List<TabInfo>,
    currentRoutePredicate: (TabInfo) -> Boolean,
    getViewModel: (TabInfo) -> ViewModel,
    getKey: (TabInfo) -> Any,
    getScrollIndex: (TabInfo) -> Int,
    getScrollOffset: (TabInfo) -> Int,
    initializeViewModel: (viewModel: ViewModel, tabInfo: TabInfo) -> Unit,
    updateScrollPosition: (viewModel: ViewModel, tab: TabInfo, index: Int, offset: Int) -> Unit,
    topBar: @Composable (viewModel: ViewModel, uiState: UiState, openDrawer: () -> Unit, scrollBehavior: TopAppBarScrollBehavior) -> Unit,
    bottomBar: @Composable (viewModel: ViewModel, uiState: UiState) -> Unit,
    content: @Composable (viewModel: ViewModel, uiState: UiState, listState: LazyListState, modifier: Modifier,navController: NavHostController) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    bottomBarScrollBehavior: BottomAppBarScrollBehavior? = null,
    optionalSheetContent: @Composable (viewModel: ViewModel, uiState: UiState) -> Unit = { _, _ -> }
) {
    var cachedTabs by remember { mutableStateOf(openTabs) }
    if (openTabs.isNotEmpty()) {
        cachedTabs = openTabs
    }
    val tabs = openTabs.ifEmpty { cachedTabs }
    val currentTabInfo = tabs.find(currentRoutePredicate)

    if (currentTabInfo != null) {
        val initialPage = remember(route, tabs.size) {
            tabs.indexOfFirst(currentRoutePredicate).coerceAtLeast(0)
        }

        val pagerState =
            rememberPagerState(initialPage = initialPage, pageCount = { tabs.size })

        LaunchedEffect(initialPage) {
            if (pagerState.currentPage != initialPage) {
                pagerState.scrollToPage(initialPage)
            }
        }

        val bookmarkSheetState = rememberModalBottomSheetState()
        val tabListSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        HorizontalPager(
            state = pagerState,
            key = { page -> getKey(tabs[page]) }
        ) { page ->
            val tab = tabs[page]
            val viewModel = getViewModel(tab)
            val uiState by viewModel.uiState.collectAsState()
            val bookmarkState = (uiState as? BoardUiState)?.singleBookmarkState
                ?: (uiState as? ThreadUiState)?.singleBookmarkState
                ?: SingleBookmarkState()


            val listState = remember(getKey(tab)) {
                LazyListState(
                    firstVisibleItemIndex = getScrollIndex(tab),
                    firstVisibleItemScrollOffset = getScrollOffset(tab)
                )
            }

            var hasInitialized by remember(getKey(tab)) { mutableStateOf(false) }
            val isActive = pagerState.currentPage == page
            LaunchedEffect(isActive, tab) {
                if (isActive && !hasInitialized) {
                    initializeViewModel(viewModel, tab)
                    hasInitialized = true
                }
            }

            LaunchedEffect(listState, isActive) {
                if (isActive) {
                    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                        .debounce(200L)
                        .collectLatest { (index, offset) ->
                            updateScrollPosition(viewModel, tab, index, offset)
                        }
                }
            }

            Scaffold(
                modifier = Modifier
//                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .let { modifier ->
                        bottomBarScrollBehavior?.let { modifier.nestedScroll(it.nestedScrollConnection) }
                            ?: modifier
                    },
                topBar = { topBar(viewModel, uiState, openDrawer, scrollBehavior) },
                bottomBar = { bottomBar(viewModel, uiState) }
            ) { innerPadding ->
                content(viewModel, uiState, listState, Modifier.padding(innerPadding), navController)

                // 共通のボトムシートとダイアログ
                if (bookmarkState.showBookmarkSheet) {
                    BookmarkBottomSheet(
                        sheetState = bookmarkSheetState,
                        onDismissRequest = {
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

                if (uiState.showTabListSheet) {
                    val initialPage = when (route) {
                        is AppRoute.Thread -> 1
                        else -> 0
                    }
                    TabsBottomSheet(
                        sheetState = tabListSheetState,
                        tabsViewModel = tabsViewModel,
                        navController = navController,
                        onDismissRequest = { viewModel.closeTabListSheet() },
                        initialPage = initialPage,
                    )
                }

                // 各画面固有のシート
                optionalSheetContent(viewModel, uiState)
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
