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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
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
import com.websarva.wings.android.slevo.ui.navigation.navigateToThreadScreen
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.thread.dialog.ResponseWebViewDialog
import com.websarva.wings.android.slevo.ui.thread.sheet.ThreadInfoBottomSheet
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogAction
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import com.websarva.wings.android.slevo.ui.board.viewmodel.BoardRouteViewModel
import kotlinx.coroutines.launch

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
    tabSessionStore: TabSessionStore,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val routeViewModel: BoardRouteViewModel = hiltViewModel()
    // --- Tab/state ---
    val boardLoaded by tabSessionStore.boardLoaded.collectAsState()
    val openBoardTabs by tabSessionStore.openBoardTabs.collectAsState()
    val selectedBoardTabKey by tabSessionStore.selectedBoardTabKey.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(boardRoute, boardLoaded) {
        if (!boardLoaded) {
            return@LaunchedEffect
        }
        // route 引数は初期化入力・placeholder として扱い、既に有効な選択中タブがある場合は上書きしない。
        if (selectedBoardTabKey != null && openBoardTabs.any { it.boardUrl == selectedBoardTabKey }) {
            return@LaunchedEffect
        }
        // --- Board resolution ---
        val info = tabSessionStore.resolveBoardInfo(
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
        val index = tabSessionStore.ensureBoardTab(
            AppRoute.Board(
                boardId = info.boardId,
                boardName = info.name,
                boardUrl = info.url
            )
        )
        if (index >= 0) {
            tabSessionStore.selectBoardTab(info.url)
        }
    }

    // --- Scaffold ---
    BbsRouteScaffold(
        route = boardRoute,
        tabSessionStore = tabSessionStore,
        navController = navController,
        isTabsLoaded = boardLoaded,
        onEmptyTabs = { navController.navigateUp() },
        openTabs = openBoardTabs,
        selectedTabKey = selectedBoardTabKey,
        getUiState = { tab -> routeViewModel.uiStateFor(tab.boardUrl) },
        getBookmarkSheetHolder = { tab -> routeViewModel.bookmarkSheetHolderFor(tab.boardUrl) },
        getKey = { it.boardUrl },
        getScrollIndex = { it.firstVisibleItemIndex },
        getScrollOffset = { it.firstVisibleItemScrollOffset },
        updateScrollPosition = { tab, index, offset ->
            tabSessionStore.updateBoardScrollPosition(tab.boardUrl, index, offset)
        },
        onTabSelected = { tabSessionStore.selectBoardTab(it.boardUrl) },
        animateToPageFlow = tabSessionStore.boardPageAnimation,
        bottomBar = { tab, uiState, actionProgress, openTabListSheet ->
            val actions = listOf(
                TabToolBarAction(
                    icon = Icons.AutoMirrored.Filled.Sort,
                    contentDescriptionRes = R.string.sort,
                    onClick = { routeViewModel.openSortBottomSheet(tab.boardUrl) },
                ),
                TabToolBarAction(
                    icon = Icons.Filled.Search,
                    contentDescriptionRes = R.string.search,
                    onClick = { routeViewModel.setSearchMode(tab.boardUrl, true) },
                ),
                TabToolBarAction(
                    icon = Icons.Filled.CropSquare,
                    contentDescriptionRes = R.string.open_tablist,
                    onClick = openTabListSheet,
                ),
                TabToolBarAction(
                    icon = Icons.Filled.Create,
                    contentDescriptionRes = R.string.create_thread,
                    onClick = { routeViewModel.postDialogActionsFor(tab.boardUrl).showDialog() },
                ),
            )

            BbsRouteBottomBar(
                isSearchMode = uiState.isSearchActive,
                onCloseSearch = { routeViewModel.setSearchMode(tab.boardUrl, false) },
                animationLabel = "BoardBottomBarAnimation",
                searchContent = { modifier, closeSearch ->
                    SearchBottomBar(
                        modifier = modifier,
                        searchInputValue = uiState.searchInputValue,
                        onSearchInputChange = { routeViewModel.updateSearchInput(tab.boardUrl, it) },
                        onCloseSearch = closeSearch,
                        placeholderResId = R.string.search_in_board,
                    )
                },
                defaultContent = { modifier ->
                    TabToolBar(
                        modifier = modifier,
                        title = uiState.boardInfo.name,
                        bookmarkState = uiState.bookmarkStatusState,
                        onBookmarkClick = { routeViewModel.openBookmarkSheet(tab.boardUrl) },
                        actions = actions,
                        onTabListClick = openTabListSheet,
                        onPostClick = { routeViewModel.postDialogActionsFor(tab.boardUrl).showDialog() },
                        tabIconContentDescriptionRes = R.string.open_tablist,
                        postIconContentDescriptionRes = R.string.create_thread,
                        actionsProgress = if (uiState.isSearchActive) 0f else actionProgress,
                        onTitleClick = { routeViewModel.openBoardInfoSheet(tab.boardUrl) },
                        onRefreshClick = { routeViewModel.refreshBoard(tab.boardUrl) },
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
        content = { tab, uiState, listState, modifier, navController, openTabListSheet, openUrlDialog ->
            LaunchedEffect(uiState.pendingToastResId) {
                uiState.pendingToastResId?.let { resId ->
                    Toast.makeText(context, resId, Toast.LENGTH_SHORT).show()
                    routeViewModel.consumeToast(tab.boardUrl)
                }
            }
            LaunchedEffect(uiState.resetScroll) {
                if (uiState.resetScroll) {
                    listState.scrollToItem(0)
                    tabSessionStore.updateBoardScrollPosition(uiState.boardInfo.url, 0, 0)
                    routeViewModel.consumeResetScroll(tab.boardUrl)
                }
            }
            BoardScreen(
                modifier = modifier,
                threads = uiState.threads ?: emptyList(),
                onClick = { threadInfo ->
                    coroutineScope.launch {
                        val route = tabSessionStore.normalizeThreadRouteForNavigation(
                            AppRoute.Thread(
                                threadKey = threadInfo.key,
                                boardUrl = uiState.boardInfo.url,
                                boardName = uiState.boardInfo.name,
                                boardId = uiState.boardInfo.boardId,
                                threadTitle = threadInfo.title,
                                resCount = threadInfo.resCount
                            )
                        )
                        val index = tabSessionStore.registerAndSelectThreadRoute(route)
                        if (index >= 0) navController.navigateToThreadScreen(route)
                    }
                },
                onLongClick = { threadInfo ->
                    routeViewModel.openThreadInfoSheet(tab.boardUrl, threadInfo)
                },
                isRefreshing = uiState.isLoading,
                onRefresh = { routeViewModel.refreshBoard(tab.boardUrl) },
                listState = listState,
                gestureSettings = uiState.gestureSettings,
                onGestureAction = { action ->
                    dispatchCommonGestureAction(
                        action = action,
                        handlers = CommonGestureActionHandlers(
                            onRefresh = { routeViewModel.refreshBoard(tab.boardUrl) },
                            onPostOrCreateThread = { routeViewModel.postDialogActionsFor(tab.boardUrl).showDialog() },
                            onSearch = { routeViewModel.setSearchMode(tab.boardUrl, true) },
                            onOpenTabList = openTabListSheet,
                            onOpenBookmarkList = { navController.navigate(AppRoute.BookmarkList) },
                            onOpenBoardList = { navController.navigate(AppRoute.ServiceList) },
                            onOpenHistory = { navController.navigate(AppRoute.HistoryList) },
                            onOpenNewTab = openUrlDialog,
                            onSwitchToNextTab = { tabSessionStore.animateBoardPage(1) },
                            onSwitchToPreviousTab = { tabSessionStore.animateBoardPage(-1) },
                            onCloseTab = {
                                if (uiState.boardInfo.url.isNotBlank()) {
                                    tabSessionStore.closeBoardTabByUrl(uiState.boardInfo.url)
                                }
                            },
                        ),
                    )
                },
                searchQuery = uiState.searchQuery,
            )
            ThreadInfoBottomSheet(
                showThreadInfoSheet = uiState.showThreadInfoSheet,
                onDismissRequest = { routeViewModel.closeThreadInfoSheet(tab.boardUrl) },
                threadInfo = uiState.threadInfoSheetTarget,
                boardInfo = uiState.boardInfo,
                navController = navController,
                tabSessionStore = tabSessionStore,
                showBoardAction = false,
            )
            BoardInfoBottomSheet(
                showBoardInfoSheet = uiState.showBoardInfoSheet,
                onDismissRequest = { routeViewModel.closeBoardInfoSheet(tab.boardUrl) },
                boardName = uiState.boardInfo.name,
                serviceName = uiState.serviceName,
                boardUrl = uiState.boardInfo.url,
            )
        },
        optionalSheetContent = { tab, uiState ->
            val sortSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            if (uiState.showSortSheet) {
                SortBottomSheet(
                    sheetState = sortSheetState,
                    onDismissRequest = { routeViewModel.closeSortBottomSheet(tab.boardUrl) },
                    sortKeys = uiState.sortKeys,
                    currentSortKey = uiState.currentSortKey,
                    isSortAscending = uiState.isSortAscending,
                    onSortKeySelected = { routeViewModel.setSortKey(tab.boardUrl, it) },
                    onToggleSortOrder = { routeViewModel.toggleSortOrder(tab.boardUrl) },
                )
            }

            val postDialogState = uiState.postDialogState
            if (postDialogState.isDialogVisible) {
                val context = LocalContext.current
                PostDialog(
                    uiState = postDialogState,
                    onDismissRequest = { routeViewModel.postDialogActionsFor(tab.boardUrl).hideDialog() },
                    onAction = { action ->
                        when (action) {
                            is PostDialogAction.ChangeName -> routeViewModel.postDialogActionsFor(tab.boardUrl).updateName(action.value)
                            is PostDialogAction.ChangeMail -> routeViewModel.postDialogActionsFor(tab.boardUrl).updateMail(action.value)
                            is PostDialogAction.ChangeTitle -> routeViewModel.postDialogActionsFor(tab.boardUrl).updateTitle(
                                action.value
                            )

                            is PostDialogAction.ChangeMessage -> routeViewModel.postDialogActionsFor(tab.boardUrl).updateMessage(
                                action.value
                            )

                            is PostDialogAction.SelectNameHistory -> routeViewModel.postDialogActionsFor(tab.boardUrl).selectNameHistory(
                                action.value
                            )

                            is PostDialogAction.SelectMailHistory -> routeViewModel.postDialogActionsFor(tab.boardUrl).selectMailHistory(
                                action.value
                            )

                            is PostDialogAction.DeleteNameHistory -> routeViewModel.postDialogActionsFor(tab.boardUrl).deleteNameHistory(
                                action.value
                            )

                            is PostDialogAction.DeleteMailHistory -> routeViewModel.postDialogActionsFor(tab.boardUrl).deleteMailHistory(
                                action.value
                            )

                            PostDialogAction.Post -> {
                                parseBoardUrl(uiState.boardInfo.url)?.let { (host, boardKey) ->
                                    routeViewModel.postDialogActionsFor(tab.boardUrl).postFirstPhase(
                                        host,
                                        boardKey,
                                        threadKey = null,
                                    )
                                }
                            }
                        }
                    },
                    onImageUpload = { uri -> routeViewModel.uploadPostDialogImage(tab.boardUrl, context, uri) },

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
                    onDismissRequest = { routeViewModel.postDialogActionsFor(tab.boardUrl).hideConfirmationScreen() },
                    onConfirm = {
                        parseBoardUrl(uiState.boardInfo.url)?.let { (host, boardKey) ->
                            routeViewModel.postDialogActionsFor(tab.boardUrl).postSecondPhase(
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
                    onDismissRequest = { routeViewModel.postDialogActionsFor(tab.boardUrl).hideErrorWebView() },
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
