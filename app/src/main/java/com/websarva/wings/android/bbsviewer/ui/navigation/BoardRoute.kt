package com.websarva.wings.android.bbsviewer.ui.navigation

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.ui.board.BoardBottomBar
import com.websarva.wings.android.bbsviewer.ui.board.BoardInfoDialog
import com.websarva.wings.android.bbsviewer.ui.board.BoardScreen
import com.websarva.wings.android.bbsviewer.ui.board.BoardTopBarScreen
import com.websarva.wings.android.bbsviewer.ui.board.BoardViewModel
import com.websarva.wings.android.bbsviewer.ui.board.SortBottomSheet
import com.websarva.wings.android.bbsviewer.ui.common.AddGroupDialog
import com.websarva.wings.android.bbsviewer.ui.common.BookmarkBottomSheet
import com.websarva.wings.android.bbsviewer.ui.tabs.BoardTabInfo
import com.websarva.wings.android.bbsviewer.ui.tabs.TabsBottomSheet
import com.websarva.wings.android.bbsviewer.ui.tabs.TabsViewModel
import com.websarva.wings.android.bbsviewer.ui.topbar.SearchTopAppBar
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@RequiresApi(Build.VERSION_CODES.O)
fun NavGraphBuilder.addBoardRoute(
    navController: NavHostController,
    openDrawer: () -> Unit,
    tabsViewModel: TabsViewModel,
) {
    composable<AppRoute.Board> { backStackEntry ->
        val board: AppRoute.Board = backStackEntry.toRoute()

        LaunchedEffect(board) {
            tabsViewModel.openBoardTab(
                BoardTabInfo(
                    boardId = board.boardId,
                    boardName = board.boardName,
                    boardUrl = board.boardUrl
                )
            )
        }

        val openBoards by tabsViewModel.openBoardTabs.collectAsState()
        val currentTabInfo = openBoards.find { it.boardUrl == board.boardUrl }

        if (currentTabInfo != null) {
            val initialPage = remember(board, openBoards.size) {
                openBoards.indexOfFirst { it.boardUrl == board.boardUrl }.coerceAtLeast(0)
            }

            val pagerState = rememberPagerState(
                initialPage = initialPage,
                pageCount = { openBoards.size }
            )

            LaunchedEffect(initialPage) {
                if (pagerState.currentPage != initialPage) {
                    pagerState.scrollToPage(initialPage)
                }
            }

            val bookmarkSheetState = rememberModalBottomSheetState()
            val sortSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val tabListSheetState = rememberModalBottomSheetState()

            HorizontalPager(state = pagerState) { page ->
                val tab = openBoards[page]
                val viewModel: BoardViewModel =
                    tabsViewModel.getOrCreateBoardViewModel(tab.boardUrl)
                val uiState by viewModel.uiState.collectAsState()
                val favoriteState = uiState.favoriteState

                val listState = remember(tab.firstVisibleItemIndex, tab.firstVisibleItemScrollOffset) {
                    LazyListState(
                        firstVisibleItemIndex = tab.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = tab.firstVisibleItemScrollOffset
                    )
                }
                val isActive = pagerState.currentPage == page

                LaunchedEffect(isActive, tab) {
                    if (isActive) {
                        // BoardInfoを渡して初期化
                        viewModel.initializeBoard(
                            boardInfo = BoardInfo(
                                boardId = tab.boardId,
                                name = tab.boardName,
                                url = tab.boardUrl
                            )
                        )
                    }
                }

                LaunchedEffect(listState, isActive) {
                    if (isActive) {
                        snapshotFlow {
                            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                        }
                            .debounce(200L)
                            .collectLatest { (index, offset) ->
                                tabsViewModel.updateBoardScrollPosition(
                                    tab.boardUrl,
                                    firstVisibleIndex = index,
                                    scrollOffset = offset
                                )
                            }
                    }
                }

                BackHandler(enabled = uiState.isSearchActive) {
                    viewModel.setSearchMode(false)
                }

                val bookmarkIconColor =
                    if (favoriteState.isBookmarked && favoriteState.selectedGroup?.colorHex != null) {
                        try {
                            Color(favoriteState.selectedGroup.colorHex.toColorInt())
                        } catch (e: IllegalArgumentException) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    } else {
                        Color.Unspecified
                    }

                val topBarState = rememberTopAppBarState()
                val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topBarState)

                Scaffold(
                    topBar = {
                        if (uiState.isSearchActive) {
                            SearchTopAppBar(
                                searchQuery = uiState.searchQuery,
                                onQueryChange = { viewModel.setSearchQuery(it) },
                                onCloseSearch = { viewModel.setSearchMode(false) },
                            )
                        } else {
                            BoardTopBarScreen(
                                title = uiState.boardInfo.name,
                                onNavigationClick = openDrawer,
                                onBookmarkClick = {
                                    viewModel.openBookmarkSheet()
                                },
                                onInfoClick = { viewModel.openInfoDialog() },
                                isBookmarked = favoriteState.isBookmarked,
                                bookmarkIconColor = bookmarkIconColor,
                                scrollBehavior = scrollBehavior
                            )
                        }
                    },
                    bottomBar = {
                        BoardBottomBar(
                            modifier = Modifier
                                .navigationBarsPadding()
                                .height(56.dp),
                            onSortClick = { viewModel.openSortBottomSheet() },
                            onRefreshClick = { viewModel.loadThreadList() },
                            onSearchClick = { viewModel.setSearchMode(true) },
                            onTabListClick = { viewModel.openTabListSheet() }
                        )
                    },
                ) { innerPadding ->
                    BoardScreen(
                        modifier = Modifier
                            .padding(innerPadding)
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        threads = uiState.threads ?: emptyList(),
                        onClick = { threadInfo ->
                            navController.navigate(
                                AppRoute.Thread(
                                    threadKey = threadInfo.key,
                                    boardName = tab.boardName,
                                    boardUrl = tab.boardUrl,
                                    boardId = tab.boardId,
                                    threadTitle = threadInfo.title,
                                    resCount = threadInfo.resCount
                                )
                            ) {
                                launchSingleTop = true
                            }
                        },
                        isRefreshing = uiState.isLoading,
                        onRefresh = { viewModel.refreshBoardData() },
                        listState = listState
                    )

                    if (favoriteState.showBookmarkSheet) {
                        BookmarkBottomSheet(
                            sheetState = bookmarkSheetState,
                            onDismissRequest = { viewModel.closeBookmarkSheet() },
                            groups = favoriteState.groups,
                            selectedGroupId = favoriteState.selectedGroup?.id,
                            onAddGroup = { viewModel.openAddGroupDialog() },
                            onGroupSelected = { viewModel.saveBookmark(it) },
                            onUnbookmarkRequested = { viewModel.unbookmarkBoard() }
                        )
                    }

                    if (favoriteState.showAddGroupDialog) {
                        AddGroupDialog(
                            onDismissRequest = { viewModel.closeAddGroupDialog() },
                            onAdd = { viewModel.addGroup() },
                            onValueChange = { viewModel.setEnteredGroupName(it) },
                            enteredValue = favoriteState.enteredGroupName,
                            onColorSelected = { viewModel.setSelectedColor(it) },
                            selectedColor = favoriteState.selectedColor,
                        )
                    }

                    if (uiState.showSortSheet) {
                        SortBottomSheet(
                            sheetState = sortSheetState,
                            onDismissRequest = { viewModel.closeSortBottomSheet() },
                            sortKeys = uiState.sortKeys,
                            currentSortKey = uiState.currentSortKey,
                            isSortAscending = uiState.isSortAscending,
                            onSortKeySelected = { viewModel.setSortKey(it) },
                            onToggleSortOrder = { viewModel.toggleSortOrder() },
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
                }

                if (uiState.showInfoDialog) {
                    BoardInfoDialog(
                        serviceName = uiState.serviceName,
                        boardName = uiState.boardInfo.name,
                        boardUrl = uiState.boardInfo.url,
                        onDismissRequest = { viewModel.closeInfoDialog() }
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
