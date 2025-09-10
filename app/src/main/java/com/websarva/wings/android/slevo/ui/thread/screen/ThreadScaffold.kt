package com.websarva.wings.android.slevo.ui.thread.screen

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.thread.components.ThreadBottomBar
import com.websarva.wings.android.slevo.ui.thread.components.ThreadInfoBottomSheet
import com.websarva.wings.android.slevo.ui.thread.dialog.ResponseWebViewDialog
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import com.websarva.wings.android.slevo.ui.thread.viewmodel.*
import com.websarva.wings.android.slevo.ui.topbar.SearchTopAppBar
import com.websarva.wings.android.slevo.ui.util.rememberBottomBarShowOnBottomBehavior
import com.websarva.wings.android.slevo.ui.util.isThreeButtonNavigation
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import com.websarva.wings.android.slevo.data.model.ThreadId
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
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

    var restoreLastTab by rememberSaveable { mutableStateOf(false) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    DisposableEffect(backStackEntry) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> restoreLastTab = false
                Lifecycle.Event.ON_RESUME -> restoreLastTab = true
                else -> {}
            }
        }
        backStackEntry?.lifecycle?.addObserver(observer)
        onDispose { backStackEntry?.lifecycle?.removeObserver(observer) }
    }

    RouteScaffold(
        route = threadRoute,
        tabsViewModel = tabsViewModel,
        navController = navController,
        openDrawer = openDrawer,
        openTabs = tabsUiState.openThreadTabs,
        currentRoutePredicate = { routeThreadId != null && it.id == routeThreadId },
        getViewModel = { tab -> tabsViewModel.getOrCreateThreadViewModel(tab.id.value) },
        getKey = { it.id },
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
        scrollBehavior = scrollBehavior,
        lastTabId = if (restoreLastTab) tabsUiState.lastThreadId else null,
        bottomBarScrollBehavior = { listState -> rememberBottomBarShowOnBottomBehavior(listState) },
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
                onBottomRefresh = { viewModel.reloadThread() },
                onLastRead = { resNum ->
                    routeThreadId?.let { viewModel.updateThreadLastRead(it, resNum) }
                },
                onReplyToPost = { viewModel.showReplyDialog(it) }
            )
        },
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
