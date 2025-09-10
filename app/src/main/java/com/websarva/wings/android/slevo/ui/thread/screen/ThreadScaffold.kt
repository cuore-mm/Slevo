package com.websarva.wings.android.slevo.ui.thread.screen

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.ui.common.PostDialog
import com.websarva.wings.android.slevo.ui.common.PostingDialog
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.RouteScaffold
import com.websarva.wings.android.slevo.ui.navigation.RoutePagerViewModel
import com.websarva.wings.android.slevo.ui.tabs.TabListViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.websarva.wings.android.slevo.ui.thread.components.ThreadBottomBar
import com.websarva.wings.android.slevo.ui.thread.components.ThreadInfoBottomSheet
import com.websarva.wings.android.slevo.ui.thread.dialog.ResponseWebViewDialog
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import com.websarva.wings.android.slevo.ui.thread.viewmodel.*
import com.websarva.wings.android.slevo.ui.topbar.SearchTopAppBar
import com.websarva.wings.android.slevo.ui.util.rememberBottomBarShowOnBottomBehavior
import com.websarva.wings.android.slevo.ui.util.isThreeButtonNavigation
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ThreadScaffold(
    threadRoute: AppRoute.Thread,
    navController: NavHostController,
    tabListViewModel: TabListViewModel,
    openDrawer: () -> Unit,
    topBarState: TopAppBarState,
) {
    val tabsUiState by tabListViewModel.uiState.collectAsState()
    val pagerViewModel: RoutePagerViewModel = hiltViewModel()
    val pagerUiState by pagerViewModel.uiState.collectAsState()
    val context = LocalContext.current

    val viewModel: ThreadViewModel = hiltViewModel(key = threadRoute.threadKey + threadRoute.boardUrl)

    LaunchedEffect(threadRoute) {
        val info = tabListViewModel.resolveBoardInfo(
            boardId = threadRoute.boardId,
            boardUrl = threadRoute.boardUrl,
            boardName = threadRoute.boardName
        )
        if (info == null) {
            Toast.makeText(context, R.string.invalid_board_url, Toast.LENGTH_SHORT).show()
            navController.navigateUp()
            return@LaunchedEffect
        }
        viewModel.initializeThread(
            threadKey = threadRoute.threadKey,
            boardInfo = info,
            threadTitle = threadRoute.threadTitle
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)

    RouteScaffold(
        route = threadRoute,
        tabListViewModel = tabListViewModel,
        navController = navController,
        openDrawer = openDrawer,
        openTabs = tabsUiState.openThreadTabs,
        currentRoutePredicate = { it.key == threadRoute.threadKey && it.boardUrl == threadRoute.boardUrl },
        provideViewModel = { tab -> hiltViewModel<ThreadViewModel>(key = tab.key + tab.boardUrl) },
        getKey = { it.key + it.boardUrl },
        getScrollIndex = { it.firstVisibleItemIndex },
        getScrollOffset = { it.firstVisibleItemScrollOffset },
        initializeViewModel = { viewModel, tab ->
            viewModel.initializeThread(
                threadKey = tab.key,
                boardInfo = BoardInfo(
                    name = tab.boardName,
                    url = tab.boardUrl,
                    boardId = tab.boardId
                ),
                threadTitle = tab.title
            )
        },
        updateScrollPosition = { viewModel, tab, index, offset ->
            viewModel.updateThreadScrollPosition(tab.key, tab.boardUrl, index, offset)
        },
        scrollBehavior = scrollBehavior,
        currentPage = pagerUiState.currentPage,
        onPageChange = { pagerViewModel.setCurrentPage(it) },
        topBar = { viewModel, uiState, _, scrollBehavior ->
            if (uiState.isSearchMode) {
                SearchTopAppBar(
                    searchQuery = uiState.searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    onCloseSearch = { viewModel.closeSearch() },
                    scrollBehavior = scrollBehavior
                )
            }
        },
        bottomBar = { viewModel, uiState, barScrollBehavior ->
            val context = LocalContext.current
            val isThreeButtonBar = remember { isThreeButtonNavigation(context) }
            ThreadBottomBar(
                modifier = if (isThreeButtonBar) {
                    Modifier.navigationBarsPadding()
                } else {
                    Modifier
                },
                uiState = uiState,
                isTreeSort = uiState.sortType == ThreadSortType.TREE,
                onSortClick = { viewModel.toggleSortType() },
                onPostClick = { viewModel.showPostDialog() },
                onTabListClick = { viewModel.openTabListSheet() },
                onRefreshClick = { viewModel.reloadThread() },
                onSearchClick = { viewModel.startSearch() },
                onBookmarkClick = { viewModel.openBookmarkSheet() },
                onThreadInfoClick = { viewModel.openThreadInfoSheet() },
                scrollBehavior = barScrollBehavior,
            )
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
                    viewModel.updateThreadTabInfo(
                        key = uiState.threadInfo.key,
                        boardUrl = uiState.boardInfo.url,
                        title = uiState.threadInfo.title,
                        resCount = uiState.posts.size
                    )
                }
            }

            val tabInfo = tabsUiState.openThreadTabs.find {
                it.key == uiState.threadInfo.key && it.boardUrl == uiState.boardInfo.url
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
                onBottomRefresh = { viewModel.reloadThread() },
                onLastRead = { resNum ->
                    viewModel.updateThreadLastRead(
                        uiState.threadInfo.key,
                        uiState.boardInfo.url,
                        resNum
                    )
                },
                onReplyToPost = { viewModel.showReplyDialog(it) }
            )
        },
        bottomBarScrollBehavior = { listState -> rememberBottomBarShowOnBottomBehavior(listState) },
        optionalSheetContent = { viewModel, uiState ->
            val postUiState by viewModel.postUiState.collectAsState()
            val threadInfoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            if (uiState.showThreadInfoSheet) {
                val threadUrl = parseBoardUrl(uiState.boardInfo.url)?.let { (host, boardKey) ->
                    "https://$host/test/read.cgi/$boardKey/${uiState.threadInfo.key}/"
                } ?: ""
                ThreadInfoBottomSheet(
                    sheetState = threadInfoSheetState,
                    onDismissRequest = { viewModel.closeThreadInfoSheet() },
                    threadInfo = uiState.threadInfo,
                    threadUrl = threadUrl,
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
