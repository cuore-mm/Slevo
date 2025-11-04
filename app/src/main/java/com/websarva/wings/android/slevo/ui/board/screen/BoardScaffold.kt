package com.websarva.wings.android.slevo.ui.board.screen

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.ui.common.PostDialog
import com.websarva.wings.android.slevo.ui.common.PostingDialog
import com.websarva.wings.android.slevo.ui.common.SearchBottomBar
import com.websarva.wings.android.slevo.ui.common.TabToolBar
import com.websarva.wings.android.slevo.ui.common.TabToolBarAction
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.bbsroute.BbsRouteScaffold
import com.websarva.wings.android.slevo.ui.bbsroute.BbsRouteBottomBar
import com.websarva.wings.android.slevo.ui.navigation.navigateToThread
import com.websarva.wings.android.slevo.ui.tabs.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.thread.dialog.ResponseWebViewDialog
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import com.websarva.wings.android.slevo.ui.util.parseServiceName
import com.websarva.wings.android.slevo.ui.util.rememberBottomBarShowOnBottomBehavior
import com.websarva.wings.android.slevo.ui.viewer.ImageViewerDialog
import com.websarva.wings.android.slevo.ui.viewer.rememberImageViewerDialogState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun BoardScaffold(
    boardRoute: AppRoute.Board,
    navController: NavHostController,
    tabsViewModel: TabsViewModel,
) {
    val tabsUiState by tabsViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentPage by tabsViewModel.boardCurrentPage.collectAsState()

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

    val imageViewerState = rememberImageViewerDialogState()

    BbsRouteScaffold(
        route = boardRoute,
        tabsViewModel = tabsViewModel,
        navController = navController,
        isTabsLoaded = tabsUiState.boardLoaded,
        onEmptyTabs = { navController.navigateUp() },
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
        onPageChange = { tabsViewModel.setBoardCurrentPage(it) },
        animateToPageFlow = tabsViewModel.boardPageAnimation,
        bottomBarScrollBehavior = { listState -> rememberBottomBarShowOnBottomBehavior(listState) },
        bottomBar = { viewModel, uiState, barScrollBehavior, openTabListSheet ->
            val actions = listOf(
                TabToolBarAction(
                    icon = Icons.AutoMirrored.Filled.Sort,
                    contentDescriptionRes = R.string.sort,
                    onClick = { viewModel.openSortBottomSheet() },
                ),
                TabToolBarAction(
                    icon = Icons.Filled.Search,
                    contentDescriptionRes = R.string.search,
                    onClick = { viewModel.setSearchMode(true) },
                ),
                TabToolBarAction(
                    icon = Icons.Filled.CropSquare,
                    contentDescriptionRes = R.string.open_tablist,
                    onClick = openTabListSheet,
                ),
                TabToolBarAction(
                    icon = Icons.Filled.Create,
                    contentDescriptionRes = R.string.create_thread,
                    onClick = { viewModel.showCreateDialog() },
                ),
            )

            BbsRouteBottomBar(
                isSearchMode = uiState.isSearchActive,
                onCloseSearch = { viewModel.setSearchMode(false) },
                animationLabel = "BoardBottomBarAnimation",
                searchContent = { modifier, closeSearch ->
                    SearchBottomBar(
                        modifier = modifier,
                        searchQuery = uiState.searchQuery,
                        onQueryChange = { viewModel.setSearchQuery(it) },
                        onCloseSearch = closeSearch,
                        placeholderResId = R.string.search_in_board,
                    )
                },
                defaultContent = { modifier ->
                    TabToolBar(
                        modifier = modifier,
                        title = uiState.boardInfo.name,
                        bookmarkState = uiState.singleBookmarkState,
                        onBookmarkClick = { viewModel.openBookmarkSheet() },
                        actions = actions,
                        scrollBehavior = barScrollBehavior,
                        onRefreshClick = { viewModel.refreshBoardData() },
                        isLoading = uiState.isLoading,
                        loadProgress = uiState.loadProgress,
                        titleStyle = MaterialTheme.typography.titleMedium,
                        titleFontWeight = FontWeight.Bold,
                        titleMaxLines = 1,
                        titleTextAlign = TextAlign.Center,
                    )
                }
            )
        },
        content = { viewModel, uiState, listState, modifier, navController, showBottomBar, openTabListSheet, openUrlDialog ->
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
                    val route = AppRoute.Thread(
                        threadKey = threadInfo.key,
                        boardUrl = uiState.boardInfo.url,
                        boardName = uiState.boardInfo.name,
                        boardId = uiState.boardInfo.boardId,
                        threadTitle = threadInfo.title,
                        resCount = threadInfo.resCount
                    )
                    navController.navigateToThread(
                        route = route,
                        tabsViewModel = tabsViewModel,
                    )
                },
                isRefreshing = uiState.isLoading,
                onRefresh = { viewModel.refreshBoardData() },
                listState = listState,
                gestureSettings = uiState.gestureSettings,
                showBottomBar = showBottomBar,
                onGestureAction = { action ->
                    when (action) {
                        GestureAction.Refresh -> viewModel.refreshBoardData()
                        GestureAction.PostOrCreateThread -> viewModel.showCreateDialog()
                        GestureAction.Search -> viewModel.setSearchMode(true)
                        GestureAction.OpenTabList -> openTabListSheet()
                        GestureAction.OpenBookmarkList -> navController.navigate(AppRoute.BookmarkList)
                        GestureAction.OpenBoardList -> navController.navigate(AppRoute.ServiceList)
                        GestureAction.OpenHistory -> navController.navigate(AppRoute.HistoryList)
                        GestureAction.OpenNewTab -> openUrlDialog()
                        GestureAction.SwitchToNextTab -> tabsViewModel.animateBoardPage(1)
                        GestureAction.SwitchToPreviousTab -> tabsViewModel.animateBoardPage(-1)
                        GestureAction.CloseTab ->
                            if (uiState.boardInfo.url.isNotBlank()) {
                                tabsViewModel.closeBoardTabByUrl(uiState.boardInfo.url)
                            }
                        GestureAction.ToTop, GestureAction.ToBottom -> Unit
                    }
                },
                searchQuery = uiState.searchQuery,
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

            SharedTransitionLayout {
                val context = LocalContext.current
                AnimatedVisibility(
                    visible = uiState.createDialog,
                    label = "PostDialogAnimation"
                ) {
                        PostDialog(
                            onDismissRequest = { viewModel.hideCreateDialog() },
                            name = uiState.createFormState.name,
                            mail = uiState.createFormState.mail,
                            message = uiState.createFormState.message,
                            namePlaceholder = uiState.boardInfo.noname.ifBlank { stringResource(R.string.name) },
                            nameHistory = uiState.createNameHistory,
                            mailHistory = uiState.createMailHistory,
                            onNameChange = { viewModel.updateCreateName(it) },
                            onMailChange = { viewModel.updateCreateMail(it) },
                            onMessageChange = { viewModel.updateCreateMessage(it) },
                            onNameHistorySelect = { viewModel.selectCreateNameHistory(it) },
                            onMailHistorySelect = { viewModel.selectCreateMailHistory(it) },
                            onNameHistoryDelete = { viewModel.deleteCreateNameHistory(it) },
                            onMailHistoryDelete = { viewModel.deleteCreateMailHistory(it) },
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
                            onImageUrlClick = { url -> imageViewerState.show(url) },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@AnimatedVisibility
                        )
                    }
                
                AnimatedVisibility(
                    visible = imageViewerState.imageUrl != null,
                    label = "ImageViewerAnimation"
                ) {
                    ImageViewerDialog(
                        state = imageViewerState,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@AnimatedVisibility
                    )
                }
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
