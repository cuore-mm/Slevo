package com.websarva.wings.android.bbsviewer.ui.navigation

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.websarva.wings.android.bbsviewer.ui.board.AddGroupDialog
import com.websarva.wings.android.bbsviewer.ui.board.BoardScreen
import com.websarva.wings.android.bbsviewer.ui.board.BoardViewModel
import com.websarva.wings.android.bbsviewer.ui.board.BookmarkBottomSheet
import com.websarva.wings.android.bbsviewer.ui.topbar.BoardTopBarScreen
import com.websarva.wings.android.bbsviewer.ui.util.keyToDatUrl

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
fun NavGraphBuilder.addBoardRoute(
    navController: NavHostController,
) {
    composable<AppRoute.Board> { backStackEntry ->
        val board: AppRoute.Board = backStackEntry.toRoute()

        val viewModel: BoardViewModel = hiltViewModel(backStackEntry)
        val uiState by viewModel.uiState.collectAsState()

        val sheetState = rememberModalBottomSheetState()

        Scaffold(
            topBar = {
                BoardTopBarScreen(
                    title = uiState.boardInfo.name,
                    onNavigationClick = {},
                    onBookmarkClick = {
                        viewModel.loadGroups()
                        viewModel.openBookmarkSheet()
                        Log.d("BoardTopBarScreen", uiState.showBookmarkSheet.toString())
                    },
                    onInfoClick = {},
//                    scrollBehavior = scrollBehavior
                )
            },
        ) { innerPadding ->
            BoardScreen(
                modifier = Modifier.padding(innerPadding),
                threads = uiState.threads ?: emptyList(),
                onClick = { threadInfo ->
                    navController.navigate(
                        AppRoute.Thread(
                            threadKey = threadInfo.key,
                            datUrl = keyToDatUrl(board.boardUrl, threadInfo.key),
                            boardName = board.boardName,
                            boardUrl = board.boardUrl,
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
                isRefreshing = uiState.isLoading,
                onRefresh = {
//                viewModel.loadThreadList(board.boardUrl)
                }
            )

            if (uiState.showBookmarkSheet) {
                BookmarkBottomSheet(
                    sheetState = sheetState,
                    onDismissRequest = { viewModel.closeBookmarkSheet() },
                    groups = uiState.groups,
                    selectedGroupId = uiState.selectedGroup?.groupId,
                    onAddGroup = { viewModel.openAddGroupDialog() },
                    onGroupSelected = { viewModel.saveBookmark(it) }
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
        }
    }
}
