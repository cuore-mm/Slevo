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
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.websarva.wings.android.bbsviewer.ui.board.BoardInfoDialog
import com.websarva.wings.android.bbsviewer.ui.topbar.SearchTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
fun NavGraphBuilder.addBoardRoute(
    navController: NavHostController,
    openDrawer: () -> Unit,
) {
    composable<AppRoute.Board> { backStackEntry ->
        val board: AppRoute.Board = backStackEntry.toRoute()

        val viewModel: BoardViewModel = hiltViewModel(backStackEntry)
        val uiState by viewModel.uiState.collectAsState()

        val bookmarkSheetState = rememberModalBottomSheetState() // Bookmark用
        val sortSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true) // Sort用

        // 検索モード中に「戻る」が押された場合の処理
        BackHandler(enabled = uiState.isSearchActive) {
            viewModel.setSearchMode(false)
        }

        // ブックマークアイコンの色を決定
        val bookmarkIconColor =
            if (uiState.isBookmarked && uiState.selectedGroup?.colorHex != null) {
                try {
                    Color(uiState.selectedGroup!!.colorHex.toColorInt())
                } catch (e: IllegalArgumentException) {
                    // HEX文字列が無効な場合、デフォルトの色を使用
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            } else {
                Color.Unspecified // ブックマークされていない場合、またはグループの色がない場合のデフォルト
            }

        val topBarState = rememberTopAppBarState()
        val scrollBehavior = TopAppBarDefaults
            .enterAlwaysScrollBehavior(topBarState)

        Scaffold(
            topBar = {
                // 検索モードに応じてトップバーを切り替え
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
                        onInfoClick = { viewModel.openInfoDialog() },
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
                    onSearchClick = { viewModel.setSearchMode(true) }
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
                            boardName = board.boardName,
                            boardUrl = board.boardUrl,
                            boardId = board.boardId,
                            threadTitle = threadInfo.title,
                            resCount = threadInfo.resCount
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
                isRefreshing = uiState.isLoading,
                onRefresh = {
                    viewModel.refreshBoardData()
                }
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

            // 並び替えボトムシートの表示
            if (uiState.showSortSheet) {
                SortBottomSheet(
                    sheetState = sortSheetState,
                    onDismissRequest = { viewModel.closeSortBottomSheet() },
                    sortKeys = uiState.sortKeys, // ViewModelからソート基準のリストを渡す
                    currentSortKey = uiState.currentSortKey, // 現在のソート基準
                    isSortAscending = uiState.isSortAscending, // 現在の昇順/降順
                    onSortKeySelected = { selectedKey -> // ソート基準が選択された
                        viewModel.setSortKey(selectedKey)
                    },
                    onToggleSortOrder = { // 昇順/降順ボタンが押された
                        viewModel.toggleSortOrder()
                    },
                )
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
    }
}
