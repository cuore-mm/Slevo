package com.websarva.wings.android.slevo.ui.board.screen

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.ui.bbsroute.BbsRouteBottomBar
import com.websarva.wings.android.slevo.ui.bbsroute.BbsRouteScaffold
import com.websarva.wings.android.slevo.ui.common.PostDialog
import com.websarva.wings.android.slevo.ui.common.PostDialogMode
import com.websarva.wings.android.slevo.ui.common.PostingDialog
import com.websarva.wings.android.slevo.ui.common.SearchBottomBar
import com.websarva.wings.android.slevo.ui.common.TabToolBar
import com.websarva.wings.android.slevo.ui.common.TabToolBarAction
import com.websarva.wings.android.slevo.ui.common.interaction.CommonGestureActionHandlers
import com.websarva.wings.android.slevo.ui.common.interaction.dispatchCommonGestureAction
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.buildImageViewerRoute
import com.websarva.wings.android.slevo.ui.navigation.navigateToThread
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.thread.dialog.ResponseWebViewDialog
import com.websarva.wings.android.slevo.ui.thread.sheet.ThreadInfoBottomSheet
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogAction
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import com.websarva.wings.android.slevo.ui.util.rememberBottomBarShowOnBottomBehavior

/**
 * 板画面の表示とタブ解決をまとめて行う。
 *
 * URL検証に成功した場合のみタブを保存し、無効URLは保存せずに戻る。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun BoardScaffold(
    boardRoute: AppRoute.Board,
    navController: NavHostController,
    tabsViewModel: TabsViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    // --- Tab/state ---
    val tabsUiState by tabsViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentPage by tabsViewModel.boardCurrentPage.collectAsState()

    LaunchedEffect(boardRoute) {
        // --- Board resolution ---
        val info = tabsViewModel.resolveBoardInfo(
            boardId = boardRoute.boardId,
            boardUrl = boardRoute.boardUrl,
            boardName = boardRoute.boardName
        )
        if (info == null) {
            // URL検証に失敗したため、タブ保存を行わずに戻る。
            Toast.makeText(context, R.string.invalid_url, Toast.LENGTH_SHORT).show()
            navController.navigateUp()
            return@LaunchedEffect
        }
        val index = tabsViewModel.ensureBoardTab(
            AppRoute.Board(
                boardId = info.boardId,
                boardName = info.name,
                boardUrl = info.url
            )
        )
        if (index >= 0) {
            tabsViewModel.setBoardCurrentPage(index)
        }
    }

    // --- Scaffold ---
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
                    onClick = { viewModel.postDialogActions.showDialog() },
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
                        bookmarkState = uiState.bookmarkStatusState,
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
                onLongClick = { threadInfo ->
                    viewModel.openThreadInfoSheet(threadInfo)
                },
                isRefreshing = uiState.isLoading,
                onRefresh = { viewModel.refreshBoardData() },
                listState = listState,
                gestureSettings = uiState.gestureSettings,
                showBottomBar = showBottomBar,
                onGestureAction = { action ->
                    dispatchCommonGestureAction(
                        action = action,
                        handlers = CommonGestureActionHandlers(
                            onRefresh = { viewModel.refreshBoardData() },
                            onPostOrCreateThread = { viewModel.postDialogActions.showDialog() },
                            onSearch = { viewModel.setSearchMode(true) },
                            onOpenTabList = openTabListSheet,
                            onOpenBookmarkList = { navController.navigate(AppRoute.BookmarkList) },
                            onOpenBoardList = { navController.navigate(AppRoute.ServiceList) },
                            onOpenHistory = { navController.navigate(AppRoute.HistoryList) },
                            onOpenNewTab = openUrlDialog,
                            onSwitchToNextTab = { tabsViewModel.animateBoardPage(1) },
                            onSwitchToPreviousTab = { tabsViewModel.animateBoardPage(-1) },
                            onCloseTab = {
                                if (uiState.boardInfo.url.isNotBlank()) {
                                    tabsViewModel.closeBoardTabByUrl(uiState.boardInfo.url)
                                }
                            },
                        ),
                    )
                },
                searchQuery = uiState.searchQuery,
            )
            ThreadInfoBottomSheet(
                showThreadInfoSheet = uiState.showThreadInfoSheet,
                onDismissRequest = { viewModel.closeThreadInfoSheet() },
                threadInfo = uiState.threadInfoSheetTarget,
                boardInfo = uiState.boardInfo,
                navController = navController,
                tabsViewModel = tabsViewModel,
                showBoardAction = false,
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

            val postDialogState = uiState.postDialogState
            if (postDialogState.isDialogVisible) {
                val context = LocalContext.current
                PostDialog(
                    uiState = postDialogState,
                    onDismissRequest = { viewModel.postDialogActions.hideDialog() },
                    onAction = { action ->
                        when (action) {
                            is PostDialogAction.ChangeName -> viewModel.postDialogActions.updateName(action.value)
                            is PostDialogAction.ChangeMail -> viewModel.postDialogActions.updateMail(action.value)
                            is PostDialogAction.ChangeTitle -> viewModel.postDialogActions.updateTitle(action.value)
                            is PostDialogAction.ChangeMessage -> viewModel.postDialogActions.updateMessage(
                                action.value
                            )

                            is PostDialogAction.SelectNameHistory -> viewModel.postDialogActions.selectNameHistory(
                                action.value
                            )

                            is PostDialogAction.SelectMailHistory -> viewModel.postDialogActions.selectMailHistory(
                                action.value
                            )

                            is PostDialogAction.DeleteNameHistory -> viewModel.postDialogActions.deleteNameHistory(
                                action.value
                            )

                            is PostDialogAction.DeleteMailHistory -> viewModel.postDialogActions.deleteMailHistory(
                                action.value
                            )

                            PostDialogAction.Post -> {
                                parseBoardUrl(uiState.boardInfo.url)?.let { (host, boardKey) ->
                                    viewModel.postDialogActions.postFirstPhase(
                                        host,
                                        boardKey,
                                        threadKey = null,
                                    )
                                }
                            }
                        }
                    },
                    onImageUpload = { uri -> viewModel.uploadImage(context, uri) },
                    onImageUrlClick = { urls, tappedIndex, transitionNamespace ->
                        val route = buildImageViewerRoute(
                            imageUrls = urls,
                            tappedIndex = tappedIndex,
                            transitionNamespace = transitionNamespace,
                        )
                        route?.let(navController::navigate)
                    },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    mode = PostDialogMode.NewThread
                )
            }

            if (postDialogState.isConfirmationScreen) {
                postDialogState.postConfirmation?.let { confirmationData ->
                    ResponseWebViewDialog(
                        htmlContent = confirmationData.html,
                        onDismissRequest = { viewModel.postDialogActions.hideConfirmationScreen() },
                        onConfirm = {
                            parseBoardUrl(uiState.boardInfo.url)?.let { (host, boardKey) ->
                                viewModel.postDialogActions.postSecondPhase(
                                    host,
                                    boardKey,
                                    threadKey = null,
                                    confirmationData = confirmationData,
                                )
                            }
                        },
                        title = "書き込み確認",
                        confirmButtonText = "書き込む"
                    )
                }
            }

            if (postDialogState.showErrorWebView) {
                ResponseWebViewDialog(
                    htmlContent = postDialogState.errorHtmlContent,
                    onDismissRequest = { viewModel.postDialogActions.hideErrorWebView() },
                    title = "応答結果",
                    onConfirm = null
                )
            }

            if (postDialogState.isPosting) {
                PostingDialog()
            }
        }
    )
}
