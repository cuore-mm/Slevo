package com.websarva.wings.android.bbsviewer.ui.bookmarklist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.ui.common.SelectedTopBarScreen
import com.websarva.wings.android.bbsviewer.ui.common.bookmark.BookmarkBottomSheet
import com.websarva.wings.android.bbsviewer.ui.common.bookmark.AddGroupDialog
import com.websarva.wings.android.bbsviewer.ui.common.bookmark.DeleteGroupDialog
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkListScaffold(
    parentPadding: PaddingValues,
    navController: NavHostController,
    topBarState: TopAppBarState,
    openDrawer: () -> Unit
) {
    val bookmarkViewModel: BookmarkViewModel = hiltViewModel()
    val uiState by bookmarkViewModel.uiState.collectAsState()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topBarState)

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
            modifier = Modifier.padding(
                // 左右と下は親のpadding、上は子のpaddingを使用
                start = parentPadding.calculateStartPadding(LayoutDirection.Ltr),
                top = innerPadding.calculateTopPadding(),
                end = parentPadding.calculateEndPadding(LayoutDirection.Ltr),
                bottom = parentPadding.calculateBottomPadding()
            ),
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
                        boardId = thread.boardId,
                        resCount = thread.resCount
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
                selectedGroupId = uiState.selectedGroupId,
                onGroupSelected = { bookmarkViewModel.applyGroupToSelection(it) },
                onUnbookmarkRequested = { bookmarkViewModel.unbookmarkSelection() },
                onAddGroup = {
                    val isBoard = uiState.selectedBoards.isNotEmpty()
                    bookmarkViewModel.openAddGroupDialog(isBoard)
                },
                onGroupLongClick = { group ->
                    val isBoard = uiState.selectedBoards.isNotEmpty()
                    bookmarkViewModel.openEditGroupDialog(group, isBoard)
                }
            )
        }

        if (uiState.showAddGroupDialog) {
            AddGroupDialog(
                onDismissRequest = { bookmarkViewModel.closeAddGroupDialog() },
                isEdit = uiState.editingGroupId != null,
                onConfirm = { bookmarkViewModel.confirmGroup() },
                onDelete = { bookmarkViewModel.requestDeleteGroup() },
                onValueChange = { bookmarkViewModel.setEnteredGroupName(it) },
                enteredValue = uiState.enteredGroupName,
                onColorSelected = { bookmarkViewModel.setSelectedColor(it) },
                selectedColor = uiState.selectedColor
            )
        }

        if (uiState.showDeleteGroupDialog) {
            DeleteGroupDialog(
                groupName = uiState.deleteGroupName,
                itemNames = uiState.deleteGroupItems,
                isBoard = uiState.deleteGroupIsBoard,
                onDismissRequest = { bookmarkViewModel.closeDeleteGroupDialog() },
                onConfirm = { bookmarkViewModel.confirmDeleteGroup() }
            )
        }
    }
}
