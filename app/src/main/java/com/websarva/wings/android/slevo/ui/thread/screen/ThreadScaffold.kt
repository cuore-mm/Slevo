package com.websarva.wings.android.slevo.ui.thread.screen

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.ui.common.PostDialog
import com.websarva.wings.android.slevo.ui.common.PostingDialog
import com.websarva.wings.android.slevo.ui.common.SearchBottomBar
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.bbsroute.BbsRouteScaffold
import com.websarva.wings.android.slevo.ui.bbsroute.BbsRouteBottomBar
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.thread.components.DisplaySettingsBottomSheet
import com.websarva.wings.android.slevo.ui.thread.components.ThreadInfoBottomSheet
import com.websarva.wings.android.slevo.ui.thread.components.ThreadToolBar
import com.websarva.wings.android.slevo.ui.thread.dialog.ResponseWebViewDialog
import com.websarva.wings.android.slevo.ui.thread.dialog.ThreadToolbarOverflowMenu
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import com.websarva.wings.android.slevo.ui.thread.viewmodel.deletePostMailHistory
import com.websarva.wings.android.slevo.ui.thread.viewmodel.deletePostNameHistory
import com.websarva.wings.android.slevo.ui.thread.viewmodel.hideConfirmationScreen
import com.websarva.wings.android.slevo.ui.thread.viewmodel.hideErrorWebView
import com.websarva.wings.android.slevo.ui.thread.viewmodel.hidePostDialog
import com.websarva.wings.android.slevo.ui.thread.viewmodel.postFirstPhase
import com.websarva.wings.android.slevo.ui.thread.viewmodel.postTo5chSecondPhase
import com.websarva.wings.android.slevo.ui.thread.viewmodel.selectPostMailHistory
import com.websarva.wings.android.slevo.ui.thread.viewmodel.selectPostNameHistory
import com.websarva.wings.android.slevo.ui.thread.viewmodel.showPostDialog
import com.websarva.wings.android.slevo.ui.thread.viewmodel.showReplyDialog
import com.websarva.wings.android.slevo.ui.thread.viewmodel.updatePostMail
import com.websarva.wings.android.slevo.ui.thread.viewmodel.updatePostMessage
import com.websarva.wings.android.slevo.ui.thread.viewmodel.updatePostName
import com.websarva.wings.android.slevo.ui.thread.viewmodel.uploadImage
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import com.websarva.wings.android.slevo.ui.util.rememberBottomBarShowOnBottomBehavior
import com.websarva.wings.android.slevo.ui.viewer.ImageViewerDialog
import com.websarva.wings.android.slevo.ui.viewer.rememberImageViewerDialogState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ThreadScaffold(
    threadRoute: AppRoute.Thread,
    navController: NavHostController,
    tabsViewModel: TabsViewModel,
) {
    val tabsUiState by tabsViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentPage by tabsViewModel.threadCurrentPage.collectAsState()

    val imageViewerState = rememberImageViewerDialogState()

    val routeThreadId = parseBoardUrl(threadRoute.boardUrl)?.let { (host, board) ->
        ThreadId.of(host, board, threadRoute.threadKey)
    }

    LaunchedEffect(threadRoute, tabsUiState.threadLoaded) {
        if (!tabsUiState.threadLoaded) {
            return@LaunchedEffect
        }
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
        tabsViewModel.ensureThreadTab(
            threadRoute.copy(
                boardId = info.boardId,
                boardName = info.name
            )
        )
        val vm = tabsViewModel.getOrCreateThreadViewModel(routeThreadId.value)
        vm.initializeThread(
            threadKey = threadRoute.threadKey,
            boardInfo = info,
            threadTitle = threadRoute.threadTitle
        )
    }

    BbsRouteScaffold(
        route = threadRoute,
        tabsViewModel = tabsViewModel,
        navController = navController,
        isTabsLoaded = tabsUiState.threadLoaded,
        onEmptyTabs = { navController.navigateUp() },
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
        onPageChange = { tabsViewModel.setThreadCurrentPage(it) },
        animateToPageFlow = tabsViewModel.threadPageAnimation,
        bottomBarScrollBehavior = { listState -> rememberBottomBarShowOnBottomBehavior(listState) },
        bottomBar = { viewModel, uiState, barScrollBehavior, openTabListSheet ->
            BbsRouteBottomBar(
                isSearchMode = uiState.isSearchMode,
                onCloseSearch = { viewModel.closeSearch() },
                animationLabel = "BottomBarAnimation",
                searchContent = { modifier, closeSearch ->
                    SearchBottomBar(
                        modifier = modifier,
                        searchQuery = uiState.searchQuery,
                        onQueryChange = { viewModel.updateSearchQuery(it) },
                        onCloseSearch = closeSearch,
                        placeholderResId = R.string.search_in_thread,
                    )
                },
                defaultContent = { modifier ->
                    ThreadToolBar(
                        modifier = modifier,
                        uiState = uiState,
                        isTreeSort = uiState.sortType == ThreadSortType.TREE,
                        onSortClick = { viewModel.toggleSortType() },
                        onPostClick = { viewModel.showPostDialog() },
                        onTabListClick = openTabListSheet,
                        onRefreshClick = { viewModel.reloadThread() },
                        onSearchClick = { viewModel.startSearch() },
                        onBookmarkClick = { viewModel.openBookmarkSheet() },
                        onThreadInfoClick = { viewModel.openThreadInfoSheet() },
                        onMoreClick = { viewModel.openMoreSheet() },
                        onAutoScrollClick = { viewModel.toggleAutoScroll() },
                        scrollBehavior = barScrollBehavior,
                    )
                }
            )
        },
        content = { viewModel, uiState, listState, modifier, navController, showBottomBar, openTabListSheet, openUrlDialog ->
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
                tabsViewModel = tabsViewModel,
                showBottomBar = showBottomBar,
                onAutoScrollBottom = { viewModel.onAutoScrollReachedBottom() },
                onBottomRefresh = { viewModel.reloadThread() },
                onLastRead = { resNum ->
                    routeThreadId?.let { viewModel.updateThreadLastRead(it, resNum) }
                },
                onReplyToPost = { viewModel.showReplyDialog(it) },
                gestureSettings = uiState.gestureSettings,
                onGestureAction = { action ->
                    when (action) {
                        GestureAction.Refresh -> viewModel.reloadThread()
                        GestureAction.PostOrCreateThread -> viewModel.showPostDialog()
                        GestureAction.Search -> viewModel.startSearch()
                        GestureAction.OpenTabList -> openTabListSheet()
                        GestureAction.OpenBookmarkList -> navController.navigate(AppRoute.BookmarkList)
                        GestureAction.OpenBoardList -> navController.navigate(AppRoute.ServiceList)
                        GestureAction.OpenHistory -> navController.navigate(AppRoute.HistoryList)
                        GestureAction.OpenNewTab -> openUrlDialog()
                        GestureAction.SwitchToNextTab -> tabsViewModel.animateThreadPage(1)
                        GestureAction.SwitchToPreviousTab -> tabsViewModel.animateThreadPage(-1)
                        GestureAction.CloseTab ->
                            if (uiState.threadInfo.key.isNotBlank() && uiState.boardInfo.url.isNotBlank()) {
                                tabsViewModel.closeThreadTab(uiState.threadInfo.key, uiState.boardInfo.url)
                            }
                        GestureAction.ToTop, GestureAction.ToBottom -> Unit
                    }
                },
                onImageClick = { url -> imageViewerState.show(url) }
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
                tabsViewModel = tabsViewModel,
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
                    },
                    onDisplaySettingsClick = {
                        viewModel.closeMoreSheet()
                        viewModel.openDisplaySettingsSheet()
                    }
                )
            }

            DisplaySettingsBottomSheet(
                show = uiState.showDisplaySettingsSheet,
                textScale = uiState.textScale,
                isIndividual = uiState.isIndividualTextScale,
                headerTextScale = uiState.headerTextScale,
                bodyTextScale = uiState.bodyTextScale,
                lineHeight = uiState.lineHeight,
                onDismissRequest = { viewModel.closeDisplaySettingsSheet() },
                onTextScaleChange = { viewModel.updateTextScale(it) },
                onIndividualChange = { viewModel.updateIndividualTextScale(it) },
                onHeaderTextScaleChange = { viewModel.updateHeaderTextScale(it) },
                onBodyTextScaleChange = { viewModel.updateBodyTextScale(it) },
                onLineHeightChange = { viewModel.updateLineHeight(it) }
            )

            SharedTransitionLayout {
                if (postUiState.postDialog) {
                    val context = LocalContext.current
                    AnimatedVisibility(
                        visible = postUiState.postDialog,
                        label = "PostDialogAnimation"
                    ) {
                        PostDialog(
                            onDismissRequest = { viewModel.hidePostDialog() },
                            name = postUiState.postFormState.name,
                            mail = postUiState.postFormState.mail,
                            message = postUiState.postFormState.message,
                            namePlaceholder = uiState.boardInfo.noname.ifBlank { "name" },
                            nameHistory = postUiState.nameHistory,
                            mailHistory = postUiState.mailHistory,
                            onNameChange = { viewModel.updatePostName(it) },
                            onMailChange = { viewModel.updatePostMail(it) },
                            onMessageChange = { viewModel.updatePostMessage(it) },
                            onNameHistorySelect = { viewModel.selectPostNameHistory(it) },
                            onMailHistorySelect = { viewModel.selectPostMailHistory(it) },
                            onNameHistoryDelete = { viewModel.deletePostNameHistory(it) },
                            onMailHistoryDelete = { viewModel.deletePostMailHistory(it) },
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
                            onImageUrlClick = { url -> imageViewerState.show(url) },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@AnimatedVisibility
                        )
                    }
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
