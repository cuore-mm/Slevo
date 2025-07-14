package com.websarva.wings.android.bbsviewer.ui.board

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.bbsviewer.ui.theme.bookmarkColor
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute
import com.websarva.wings.android.bbsviewer.ui.navigation.RouteScaffold
import com.websarva.wings.android.bbsviewer.ui.tabs.BoardTabInfo
import com.websarva.wings.android.bbsviewer.ui.tabs.TabsViewModel
import com.websarva.wings.android.bbsviewer.ui.util.parseServiceName
import com.websarva.wings.android.bbsviewer.ui.util.parseBoardUrl
import com.websarva.wings.android.bbsviewer.ui.topbar.SearchTopAppBar
import com.websarva.wings.android.bbsviewer.ui.board.CreateThreadDialog
import com.websarva.wings.android.bbsviewer.ui.thread.ResponseWebViewDialog

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun BoardScaffold(
    boardRoute: AppRoute.Board,
    navController: NavHostController,
    openDrawer: () -> Unit,
    tabsViewModel: TabsViewModel,
    topBarState: TopAppBarState
) {
    val openBoards by tabsViewModel.openBoardTabs.collectAsState()

    LaunchedEffect(boardRoute) {
        val info = tabsViewModel.resolveBoardInfo(
            boardId = boardRoute.boardId,
            boardUrl = boardRoute.boardUrl,
            boardName = boardRoute.boardName
        )
        tabsViewModel.openBoardTab(
            BoardTabInfo(
                boardId = info.boardId,
                boardName = info.name,
                boardUrl = info.url,
                serviceName = parseServiceName(info.url)
            )
        )
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topBarState)

    RouteScaffold(
        route = boardRoute,
        tabsViewModel = tabsViewModel,
        navController = navController,
        openDrawer = openDrawer,
        openTabs = openBoards,
        currentRoutePredicate = { it.boardUrl == boardRoute.boardUrl },
        getViewModel = { tab -> tabsViewModel.getOrCreateBoardViewModel(tab.boardUrl) },
        getKey = { it.boardUrl },
        getScrollIndex = { it.firstVisibleItemIndex },
        getScrollOffset = { it.firstVisibleItemScrollOffset },
        initializeViewModel = { viewModel, tab ->
            viewModel.initializeBoard(
                boardInfo = BoardInfo(
                    boardId = tab.boardId,
                    name = tab.boardName,
                    url = tab.boardUrl
                )
            )
        },
        updateScrollPosition = { tab, index, offset ->
            tabsViewModel.updateBoardScrollPosition(tab.boardUrl, index, offset)
        },
        navigateToTab = { tab ->
            navController.navigate(
                AppRoute.Board(
                    boardId = tab.boardId,
                    boardName = tab.boardName,
                    boardUrl = tab.boardUrl
                )
            ) {
                launchSingleTop = true
                restoreState = true
            }
        },
        scrollBehavior = scrollBehavior ,
        topBar = { viewModel, uiState, drawer, scrollBehavior ->
            val bookmarkState = uiState.singleBookmarkState
            val bookmarkIconColor =
                if (bookmarkState.isBookmarked && bookmarkState.selectedGroup?.colorName != null) {
                    bookmarkColor(bookmarkState.selectedGroup.colorName)
                } else {
                    Color.Unspecified
                }

            BackHandler(enabled = uiState.isSearchActive) {
                viewModel.setSearchMode(false)
            }

            if (uiState.isSearchActive) {
                SearchTopAppBar(
                    searchQuery = uiState.searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    onCloseSearch = { viewModel.setSearchMode(false) },
                )
            } else {
                BoardTopBarScreen(
                    title = uiState.boardInfo.name,
                    onNavigationClick = drawer,
                    onBookmarkClick = { viewModel.openBookmarkSheet() },
                    onInfoClick = { viewModel.openInfoDialog() },
                    isBookmarked = bookmarkState.isBookmarked,
                    bookmarkIconColor = bookmarkIconColor,
                    scrollBehavior = scrollBehavior
                )
            }
        },
        bottomBar = { viewModel, _ ->
            BoardBottomBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(56.dp),
                onSortClick = { viewModel.openSortBottomSheet() },
                onRefreshClick = { viewModel.refreshBoardData() },
                onSearchClick = { viewModel.setSearchMode(true) },
                onTabListClick = { viewModel.openTabListSheet() },
                onCreateThreadClick = { viewModel.showCreateDialog() }
            )
        },
        content = { viewModel, uiState, listState, modifier, navController ->
            BoardScreen(
                modifier = modifier,
                threads = uiState.threads ?: emptyList(),
                onClick = { threadInfo ->
                    navController.navigate(
                        AppRoute.Thread(
                            threadKey = threadInfo.key,
                            boardUrl = uiState.boardInfo.url,
                            boardName = uiState.boardInfo.name,
                            boardId = uiState.boardInfo.boardId,
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
            if (uiState.showInfoDialog) {
                BoardInfoDialog(
                    serviceName = uiState.serviceName,
                    boardName = uiState.boardInfo.name,
                    boardUrl = uiState.boardInfo.url,
                    onDismissRequest = { viewModel.closeInfoDialog() }
                )
            }
        },
        optionalSheetContent = { viewModel, uiState ->
            val sortSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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

            if (uiState.createDialog) {
                CreateThreadDialog(
                    onDismissRequest = { viewModel.hideCreateDialog() },
                    formState = uiState.createFormState,
                    onNameChange = { viewModel.updateCreateName(it) },
                    onMailChange = { viewModel.updateCreateMail(it) },
                    onTitleChange = { viewModel.updateCreateTitle(it) },
                    onMessageChange = { viewModel.updateCreateMessage(it) },
                    onCreateClick = {
                        parseBoardUrl(uiState.boardInfo.url)?.let { (host, boardKey) ->
                            viewModel.createThreadFirstPhase(
                                host,
                                boardKey,
                                uiState.createFormState.title,
                                uiState.createFormState.name,
                                uiState.createFormState.mail,
                                uiState.createFormState.message
                            )
                        }
                    }
                )
            }

            if (uiState.isConfirmationScreen) {
                uiState.postConfirmation?.let { confirmationData ->
                    ResponseWebViewDialog(
                        htmlContent = confirmationData.html,
                        onDismissRequest = { viewModel.hideConfirmationScreen() },
                        onConfirm = {
                            parseBoardUrl(uiState.boardInfo.url)?.let { (host, boardKey) ->
                                viewModel.createThreadSecondPhase(host, boardKey, confirmationData)
                            }
                        },
                        title = "書き込み確認",
                        confirmButtonText = "書き込む"
                    )
                }
            }

            if (uiState.showErrorWebView) {
                ResponseWebViewDialog(
                    htmlContent = uiState.errorHtmlContent,
                    onDismissRequest = { viewModel.hideErrorWebView() },
                    title = "応答結果",
                    onConfirm = null
                )
            }
        }
    )
}
