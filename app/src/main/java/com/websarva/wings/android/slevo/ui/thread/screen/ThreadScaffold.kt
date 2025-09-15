package com.websarva.wings.android.slevo.ui.thread.screen

import android.widget.Toast
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.ui.common.PostDialog
import com.websarva.wings.android.slevo.ui.common.PostingDialog
import com.websarva.wings.android.slevo.ui.thread.dialog.ThreadToolbarOverflowMenu
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.RouteScaffold
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.thread.components.ThreadToolBar
import com.websarva.wings.android.slevo.ui.thread.components.ThreadInfoBottomSheet
import com.websarva.wings.android.slevo.ui.thread.components.ThreadSearchBar
import com.websarva.wings.android.slevo.ui.thread.dialog.ResponseWebViewDialog
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import com.websarva.wings.android.slevo.ui.thread.viewmodel.ThreadPagerViewModel
import com.websarva.wings.android.slevo.ui.thread.viewmodel.hideConfirmationScreen
import com.websarva.wings.android.slevo.ui.thread.viewmodel.hideErrorWebView
import com.websarva.wings.android.slevo.ui.thread.viewmodel.hidePostDialog
import com.websarva.wings.android.slevo.ui.thread.viewmodel.postFirstPhase
import com.websarva.wings.android.slevo.ui.thread.viewmodel.postTo5chSecondPhase
import com.websarva.wings.android.slevo.ui.thread.viewmodel.showPostDialog
import com.websarva.wings.android.slevo.ui.thread.viewmodel.showReplyDialog
import com.websarva.wings.android.slevo.ui.thread.viewmodel.updatePostMail
import com.websarva.wings.android.slevo.ui.thread.viewmodel.updatePostMessage
import com.websarva.wings.android.slevo.ui.thread.viewmodel.updatePostName
import com.websarva.wings.android.slevo.ui.thread.viewmodel.uploadImage
import com.websarva.wings.android.slevo.ui.util.isThreeButtonNavigation
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import com.websarva.wings.android.slevo.ui.util.rememberBottomBarShowOnBottomBehavior
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScaffold(
    threadRoute: AppRoute.Thread,
    navController: NavHostController,
    tabsViewModel: TabsViewModel,
    openDrawer: () -> Unit,
    topBarState: TopAppBarState,
) {
    val tabsUiState by tabsViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val pagerViewModel: ThreadPagerViewModel = hiltViewModel()
    val currentPage by pagerViewModel.currentPage.collectAsState()

    val routeThreadId = parseBoardUrl(threadRoute.boardUrl)?.let { (host, board) ->
        ThreadId.of(host, board, threadRoute.threadKey)
    }

    LaunchedEffect(threadRoute) {
        val info = tabsViewModel.resolveBoardInfo(
            boardId = threadRoute.boardId,
            boardUrl = threadRoute.boardUrl,
            boardName = threadRoute.boardName
        )
        if (info == null || routeThreadId == null) {
            Toast.makeText(context, R.string.invalid_board_url, Toast.LENGTH_SHORT).show()
            navController.navigateUp()
            return@LaunchedEffect
        }
        val vm = tabsViewModel.getOrCreateThreadViewModel(routeThreadId.value)
        vm.initializeThread(
            threadKey = threadRoute.threadKey,
            boardInfo = info,
            threadTitle = threadRoute.threadTitle
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)

    RouteScaffold(
        route = threadRoute,
        tabsViewModel = tabsViewModel,
        navController = navController,
        openDrawer = openDrawer,
        openTabs = tabsUiState.openThreadTabs,
        currentRoutePredicate = { routeThreadId != null && it.id == routeThreadId },
        getViewModel = { tab -> tabsViewModel.getOrCreateThreadViewModel(tab.id.value) },
        getKey = { it.id.value },
        getScrollIndex = { it.firstVisibleItemIndex },
        getScrollOffset = { it.firstVisibleItemScrollOffset },
        initializeViewModel = { viewModel, tab ->
            viewModel.initializeThread(
                threadKey = tab.threadKey,
                boardInfo = BoardInfo(
                    name = tab.boardName,
                    url = tab.boardUrl,
                    boardId = tab.boardId
                ),
                threadTitle = tab.title
            )
        },
        updateScrollPosition = { viewModel, tab, index, offset ->
            viewModel.updateThreadScrollPosition(tab.id, index, offset)
        },
        currentPage = currentPage,
        onPageChange = { pagerViewModel.setCurrentPage(it) },
        scrollBehavior = scrollBehavior,
        bottomBarScrollBehavior = { listState -> rememberBottomBarShowOnBottomBehavior(listState) },
        topBar = { _, _, _, _ -> },
        bottomBar = { viewModel, uiState, barScrollBehavior ->
            val context = LocalContext.current
            val isThreeButtonBar = remember { isThreeButtonNavigation(context) }
            val modifier = if (isThreeButtonBar) {
                Modifier.navigationBarsPadding()
            } else {
                Modifier
            }
            if (uiState.isSearchMode) {
                ThreadSearchBar(
                    modifier = modifier,
                    searchQuery = uiState.searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    onCloseSearch = { viewModel.closeSearch() },
                    scrollBehavior = barScrollBehavior,
                )
            } else {
                ThreadToolBar(
                    modifier = modifier,
                    uiState = uiState,
                    isTreeSort = uiState.sortType == ThreadSortType.TREE,
                    onSortClick = { viewModel.toggleSortType() },
                    onPostClick = { viewModel.showPostDialog() },
                    onTabListClick = { viewModel.openTabListSheet() },
                    onRefreshClick = { viewModel.reloadThread() },
                    onSearchClick = { viewModel.startSearch() },
                    onBookmarkClick = { viewModel.openBookmarkSheet() },
                    onThreadInfoClick = { viewModel.openThreadInfoSheet() },
                    onMoreClick = { viewModel.openMoreSheet() },
                    onAutoScrollClick = { viewModel.toggleAutoScroll() },
                    scrollBehavior = barScrollBehavior,
                )
            }
        },
        content = { viewModel, uiState, listState, modifier, navController ->
            LaunchedEffect(uiState.threadInfo.key, uiState.isLoading) {
                // スレッドタイトルが空でなく、投稿リストが取得済みの場合にタブ情報を更新
                if (
                    !uiState.isLoading &&
                    uiState.threadInfo.title.isNotEmpty() &&
                    uiState.posts != null &&
                    uiState.threadInfo.key.isNotEmpty()
                ) {
                    parseBoardUrl(uiState.boardInfo.url)?.let { (host, board) ->
                        viewModel.updateThreadTabInfo(
                            threadId = ThreadId.of(host, board, uiState.threadInfo.key),
                            title = uiState.threadInfo.title,
                            resCount = uiState.posts.size
                        )
                    }
                }
            }

            val tabInfo = tabsUiState.openThreadTabs.find {
                it.threadKey == uiState.threadInfo.key && it.boardUrl == uiState.boardInfo.url
            }
            LaunchedEffect(tabInfo?.firstNewResNo, tabInfo?.prevResCount) {
                tabInfo?.let {
                    viewModel.setNewArrivalInfo(it.firstNewResNo, it.prevResCount)
                }
            }
            ThreadScreen(
                modifier = modifier,
                uiState = uiState,
                listState = listState,
                navController = navController,
                onAutoScrollBottom = { viewModel.onAutoScrollReachedBottom() },
                onBottomRefresh = { viewModel.reloadThread() },
                onLastRead = { resNum ->
                    routeThreadId?.let { viewModel.updateThreadLastRead(it, resNum) }
                },
                onReplyToPost = { viewModel.showReplyDialog(it) }
            )
        },
        optionalSheetContent = { viewModel, uiState ->
            val postUiState by viewModel.postUiState.collectAsState()

            ThreadInfoBottomSheet(
                showThreadInfoSheet = uiState.showThreadInfoSheet,
                onDismissRequest = { viewModel.closeThreadInfoSheet() },
                threadInfo = uiState.threadInfo,
                boardInfo = uiState.boardInfo,
                navController = navController,
            )

            if (uiState.showMoreSheet) {
                ThreadToolbarOverflowMenu(
                    onDismissRequest = { viewModel.closeMoreSheet() },
                    onBookmarkClick = {
                        viewModel.closeMoreSheet()
                        navController.navigate(AppRoute.BookmarkList)
                    },
                    onBoardListClick = {
                        viewModel.closeMoreSheet()
                        navController.navigate(AppRoute.ServiceList)
                    },
                    onHistoryClick = {
                        viewModel.closeMoreSheet()
                        navController.navigate(AppRoute.HistoryList)
                    },
                    onSettingsClick = {
                        viewModel.closeMoreSheet()
                        navController.navigate(AppRoute.SettingsHome)
                    }
                )
            }

            if (postUiState.postDialog) {
                val context = LocalContext.current
                PostDialog(
                    onDismissRequest = { viewModel.hidePostDialog() },
                    name = postUiState.postFormState.name,
                    mail = postUiState.postFormState.mail,
                    message = postUiState.postFormState.message,
                    namePlaceholder = uiState.boardInfo.noname.ifBlank { "name" },
                    onNameChange = { viewModel.updatePostName(it) },
                    onMailChange = { viewModel.updatePostMail(it) },
                    onMessageChange = { viewModel.updatePostMessage(it) },
                    onPostClick = {
                        parseBoardUrl(uiState.boardInfo.url)?.let { (host, boardKey) ->
                            viewModel.postFirstPhase(
                                host,
                                boardKey,
                                uiState.threadInfo.key,
                                postUiState.postFormState.name,
                                postUiState.postFormState.mail,
                                postUiState.postFormState.message
                            ) { resNum ->
                                viewModel.onPostSuccess(
                                    resNum,
                                    postUiState.postFormState.message,
                                    postUiState.postFormState.name,
                                    postUiState.postFormState.mail
                                )
                            }
                        }
                    },
                    confirmButtonText = stringResource(R.string.post),
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

            if (postUiState.isConfirmationScreen) {
                postUiState.postConfirmation?.let { confirmationData ->
                    ResponseWebViewDialog(
                        htmlContent = confirmationData.html,
                        onDismissRequest = { viewModel.hideConfirmationScreen() },
                        onConfirm = {
                            parseBoardUrl(uiState.boardInfo.url)?.let { (host, boardKey) ->
                                viewModel.postTo5chSecondPhase(
                                    host,
                                    boardKey,
                                    uiState.threadInfo.key,
                                    confirmationData
                                ) { resNum ->
                                    val form = postUiState.postFormState
                                    viewModel.onPostSuccess(
                                        resNum,
                                        form.message,
                                        form.name,
                                        form.mail
                                    )
                                }
                            }
                        },
                        title = "書き込み確認",
                        confirmButtonText = "書き込む"
                    )
                }
            }

            if (postUiState.showErrorWebView) {
                ResponseWebViewDialog(
                    htmlContent = postUiState.errorHtmlContent,
                    onDismissRequest = { viewModel.hideErrorWebView() },
                    title = "応答結果",
                    onConfirm = null // 確認ボタンは不要なのでnull
                )
            }

            if (postUiState.isPosting) {
                PostingDialog()
            }
        }
    )
}
