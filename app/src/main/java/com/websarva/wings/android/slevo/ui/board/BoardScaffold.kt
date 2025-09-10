package com.websarva.wings.android.slevo.ui.board

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.ui.common.PostDialog
import com.websarva.wings.android.slevo.ui.common.PostingDialog
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.RouteScaffold
import com.websarva.wings.android.slevo.ui.tabs.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.theme.bookmarkColor
import com.websarva.wings.android.slevo.ui.thread.dialog.ResponseWebViewDialog
import com.websarva.wings.android.slevo.ui.topbar.SearchTopAppBar
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import com.websarva.wings.android.slevo.ui.util.parseServiceName
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScaffold(
    boardRoute: AppRoute.Board,
    navController: NavHostController,
    openDrawer: () -> Unit,
    tabsViewModel: TabsViewModel,
    topBarState: TopAppBarState
) {
    val tabsUiState by tabsViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val pagerViewModel: BoardPagerViewModel = hiltViewModel()
    val currentPage by pagerViewModel.currentPage.collectAsState()

    LaunchedEffect(boardRoute) {
        val info = tabsViewModel.resolveBoardInfo(
            boardId = boardRoute.boardId,
            boardUrl = boardRoute.boardUrl,
            boardName = boardRoute.boardName
        )
        if (info == null) {
            Toast.makeText(context, R.string.invalid_board_url, Toast.LENGTH_SHORT).show()
            navController.navigateUp()
            return@LaunchedEffect
        }
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
        openTabs = tabsUiState.openBoardTabs,
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
        updateScrollPosition = { _, tab, index, offset ->
            tabsViewModel.updateBoardScrollPosition(tab.boardUrl, index, offset)
        },
        currentPage = currentPage,
        onPageChange = { pagerViewModel.setCurrentPage(it) },
        scrollBehavior = scrollBehavior,
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
        bottomBar = { viewModel, _, _ ->
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
            LaunchedEffect(uiState.resetScroll) {
                if (uiState.resetScroll) {
                    listState.scrollToItem(0)
                    tabsViewModel.updateBoardScrollPosition(uiState.boardInfo.url, 0, 0)
                    viewModel.consumeResetScroll()
                }
            }
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
                loadProgress = uiState.loadProgress,
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
                val context = LocalContext.current
                PostDialog(
                    onDismissRequest = { viewModel.hideCreateDialog() },
                    name = uiState.createFormState.name,
                    mail = uiState.createFormState.mail,
                    message = uiState.createFormState.message,
                    namePlaceholder = uiState.boardInfo.noname.ifBlank { stringResource(R.string.name) },
                    onNameChange = { viewModel.updateCreateName(it) },
                    onMailChange = { viewModel.updateCreateMail(it) },
                    onMessageChange = { viewModel.updateCreateMessage(it) },
                    onPostClick = {
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
                    },
                    confirmButtonText = stringResource(R.string.create_thread),
                    title = uiState.createFormState.title,
                    onTitleChange = { viewModel.updateCreateTitle(it) },
                    onImageSelect = { uri -> viewModel.uploadImage(context, uri) },
                    onImageUrlClick = { url ->
                        navController.navigate(
                            AppRoute.ImageViewer(
                                imageUrl = URLEncoder.encode(
                                    url,
                                    StandardCharsets.UTF_8.toString()
                                )
                            )
                        )
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

            if (uiState.isPosting) {
                PostingDialog()
            }
        }
    )
}
