package com.websarva.wings.android.bbsviewer.ui.navigation

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.websarva.wings.android.bbsviewer.ui.common.AddGroupDialog
import com.websarva.wings.android.bbsviewer.ui.board.BoardBottomBar
import com.websarva.wings.android.bbsviewer.ui.board.BoardScreen
import com.websarva.wings.android.bbsviewer.ui.board.BoardViewModel
import com.websarva.wings.android.bbsviewer.ui.common.BookmarkBottomSheet
import com.websarva.wings.android.bbsviewer.ui.board.SortBottomSheet
import com.websarva.wings.android.bbsviewer.ui.board.BoardTopBarScreen
import com.websarva.wings.android.bbsviewer.ui.topbar.SearchTopAppBar
import com.websarva.wings.android.bbsviewer.ui.tabs.BoardTabInfo
import com.websarva.wings.android.bbsviewer.ui.tabs.TabsBottomSheet
import com.websarva.wings.android.bbsviewer.ui.tabs.TabsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
fun NavGraphBuilder.addBoardRoute(
    navController: NavHostController,
    openDrawer: () -> Unit,
    tabsViewModel: TabsViewModel,
) {
    composable<AppRoute.Board> { backStackEntry ->
        val board: AppRoute.Board = backStackEntry.toRoute()

        LaunchedEffect(board) {
            tabsViewModel.openBoard(
                BoardTabInfo(
                    boardId = board.boardId,
                    boardName = board.boardName,
                    boardUrl = board.boardUrl
                )
            )
        }

        val openBoards by tabsViewModel.openBoardTabs.collectAsState()

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

        HorizontalPager(state = pagerState) { page ->
            val tab = openBoards[page]
            val viewModel: BoardViewModel =
                tabsViewModel.getOrCreateBoardViewModel(tab.boardUrl, tab.boardName, tab.boardId)
            val uiState by viewModel.uiState.collectAsState()

            val bookmarkSheetState = rememberModalBottomSheetState()
            val sortSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val tabListSheetState = rememberModalBottomSheetState()

            BackHandler(enabled = uiState.isSearchActive) {
                viewModel.setSearchMode(false)
            }

            val bookmarkIconColor =
                if (uiState.isBookmarked && uiState.selectedGroup?.colorHex != null) {
                    try {
                        Color(uiState.selectedGroup!!.colorHex.toColorInt())
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
                                viewModel.loadGroups()
                                viewModel.openBookmarkSheet()
                            },
                            onInfoClick = {},
                            isBookmarked = uiState.isBookmarked,
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
                    onRefresh = { viewModel.refreshBoardData() }
                )

                if (uiState.showBookmarkSheet) {
                    BookmarkBottomSheet(
                        sheetState = bookmarkSheetState,
                        onDismissRequest = { viewModel.closeBookmarkSheet() },
                        groups = uiState.groups,
                        selectedGroupId = uiState.selectedGroup?.groupId,
                        onAddGroup = { viewModel.openAddGroupDialog() },
                        onGroupSelected = { viewModel.saveBookmark(it) },
                        onUnbookmarkRequested = { viewModel.unbookmarkBoard() }
                    )
                }

                if (uiState.showAddGroupDialog) {
                    AddGroupDialog(
                        onDismissRequest = { viewModel.closeAddGroupDialog() },
                        onAdd = { viewModel.addGroup() },
                        onValueChange = { viewModel.setGroupName(it) },
                        enteredValue = uiState.enteredGroupName,
                        onColorSelected = { viewModel.setColorCode(it) },
                        selectedColor = uiState.selectedColor ?: "",
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
        }
    }
}
