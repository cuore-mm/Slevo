package com.websarva.wings.android.bbsviewer.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import com.websarva.wings.android.bbsviewer.ui.common.BookmarkBottomSheet
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.websarva.wings.android.bbsviewer.ui.bookmark.BookmarkScreen
import com.websarva.wings.android.bbsviewer.ui.bookmark.BookmarkTopBar
import com.websarva.wings.android.bbsviewer.ui.bookmark.BookmarkViewModel
import com.websarva.wings.android.bbsviewer.ui.common.SelectedTopBarScreen

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.addBookmarkRoute(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    openDrawer: () -> Unit
) {
    composable<AppRoute.Bookmark> {
        val bookmarkViewModel: BookmarkViewModel = hiltViewModel()
        val uiState by bookmarkViewModel.uiState.collectAsState()

        val editSheetState = rememberModalBottomSheetState()

        Scaffold(
            topBar = {
                Box {
                    AnimatedVisibility(
                        visible = !uiState.selectMode,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        BookmarkTopBar(
                            scrollBehavior = scrollBehavior,
                            onNavigationClick = openDrawer,
                            onAddClick = { },
                            onSearchClick = { }
                        )
                    }
                    AnimatedVisibility(
                        visible = uiState.selectMode,
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                    ) {
                        SelectedTopBarScreen(
                            onBack = { bookmarkViewModel.toggleSelectMode(false) },
                            selectedCount = uiState.selectedBoards.size + uiState.selectedThreads.size
                        )
                    }
                }
            },
        ) { innerPadding ->

            BookmarkScreen(
                modifier = modifier.padding(innerPadding),
                scrollBehavior = scrollBehavior,
                boardGroups = uiState.boardList,
                onBoardClick = { board ->
                    navController.navigate(
                        AppRoute.Board(
                            boardId = board.boardId,
                            boardName = board.name,
                            boardUrl = board.url
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
                threadGroups = uiState.groupedThreadBookmarks,
                onThreadClick = { thread ->
                    navController.navigate(
                        AppRoute.Thread(
                            threadKey = thread.threadKey,
                            boardName = thread.boardName,
                            boardUrl = thread.boardUrl,
                            threadTitle = thread.title,
                            boardId = thread.boardId
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
                selectMode = uiState.selectMode,
                selectedBoardIds = uiState.selectedBoards,
                selectedThreadIds = uiState.selectedThreads,
                onBoardLongClick = { id ->
                    bookmarkViewModel.toggleSelectMode(true)
                    bookmarkViewModel.toggleBoardSelect(id)
                },
                onThreadLongClick = { id ->
                    bookmarkViewModel.toggleSelectMode(true)
                    bookmarkViewModel.toggleThreadSelect(id)
                },
            )

            BackHandler(enabled = uiState.selectMode) {
                bookmarkViewModel.toggleSelectMode(false)
            }

            if (uiState.showEditSheet) {
                val groups = if (uiState.selectedBoards.isNotEmpty()) {
                    uiState.boardList.map { it.group }
                } else {
                    uiState.groupedThreadBookmarks.map { it.group }
                }
                BookmarkBottomSheet(
                    sheetState = editSheetState,
                    onDismissRequest = { bookmarkViewModel.closeEditSheet() },
                    groups = groups,
                    selectedGroupId = null,
                    onGroupSelected = { },
                    onUnbookmarkRequested = { },
                    onAddGroup = { }
                )
            }
        }

    }
}
