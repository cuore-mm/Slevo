package com.websarva.wings.android.bbsviewer.ui.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.ui.board.BoardUiState
import com.websarva.wings.android.bbsviewer.ui.board.BoardViewModel
import com.websarva.wings.android.bbsviewer.ui.common.BaseUiState
import com.websarva.wings.android.bbsviewer.ui.common.BaseViewModel
import com.websarva.wings.android.bbsviewer.ui.common.bookmark.AddGroupDialog
import com.websarva.wings.android.bbsviewer.ui.common.bookmark.BookmarkBottomSheet
import com.websarva.wings.android.bbsviewer.ui.common.bookmark.DeleteGroupDialog
import com.websarva.wings.android.bbsviewer.ui.common.bookmark.SingleBookmarkState
import com.websarva.wings.android.bbsviewer.ui.tabs.TabsBottomSheet
import com.websarva.wings.android.bbsviewer.ui.tabs.TabsViewModel
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadUiState
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadViewModel
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
    updateScrollPosition: (tab: TabInfo, index: Int, offset: Int) -> Unit,
    topBar: @Composable (viewModel: ViewModel, uiState: UiState, openDrawer: () -> Unit, scrollBehavior: TopAppBarScrollBehavior) -> Unit,
    bottomBar: @Composable (viewModel: ViewModel, uiState: UiState) -> Unit,
    content: @Composable (viewModel: ViewModel, uiState: UiState, listState: LazyListState, modifier: Modifier) -> Unit,
    getScrollBehavior: @Composable () -> TopAppBarScrollBehavior,
    optionalSheetContent: @Composable (viewModel: ViewModel, uiState: UiState) -> Unit = { _, _ -> }
) {
    val currentTabInfo = openTabs.find(currentRoutePredicate)

    if (currentTabInfo != null) {
        val initialPage = remember(route, openTabs.size) {
            openTabs.indexOfFirst(currentRoutePredicate).coerceAtLeast(0)
        }

        val pagerState =
            rememberPagerState(initialPage = initialPage, pageCount = { openTabs.size })

        LaunchedEffect(initialPage) {
            if (pagerState.currentPage != initialPage) {
                pagerState.scrollToPage(initialPage)
            }
        }

        val bookmarkSheetState = rememberModalBottomSheetState()
        val tabListSheetState = rememberModalBottomSheetState()

        HorizontalPager(state = pagerState) { page ->
            val tab = openTabs[page]
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

            val isActive = pagerState.currentPage == page
            LaunchedEffect(isActive, tab) {
                if (isActive) {
                    initializeViewModel(viewModel, tab)
                }
            }

            LaunchedEffect(listState, isActive) {
                if (isActive) {
                    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                        .debounce(200L)
                        .collectLatest { (index, offset) ->
                            updateScrollPosition(tab, index, offset)
                        }
                }
            }

            val scrollBehavior = getScrollBehavior()

            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = { topBar(viewModel, uiState, openDrawer, scrollBehavior) },
                bottomBar = { bottomBar(viewModel, uiState) }
            ) { innerPadding ->
                content(viewModel, uiState, listState, Modifier.padding(innerPadding))

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
                    TabsBottomSheet(
                        sheetState = tabListSheetState,
                        tabsViewModel = tabsViewModel,
                        navController = navController,
                        onDismissRequest = { viewModel.closeTabListSheet() },
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
