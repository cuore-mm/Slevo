package com.websarva.wings.android.bbsviewer.ui.thread

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute
import com.websarva.wings.android.bbsviewer.ui.navigation.RouteScaffold
import com.websarva.wings.android.bbsviewer.ui.tabs.TabsViewModel
import com.websarva.wings.android.bbsviewer.ui.tabs.ThreadTabInfo
import com.websarva.wings.android.bbsviewer.ui.util.parseBoardUrl

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ThreadScaffold(
    threadRoute: AppRoute.Thread,
    navController: NavHostController,
    tabsViewModel: TabsViewModel,
    openDrawer: () -> Unit,
) {
    val openTabs by tabsViewModel.openThreadTabs.collectAsState()

    LaunchedEffect(threadRoute) {
        tabsViewModel.openThreadTab(
            ThreadTabInfo(
                key = threadRoute.threadKey,
                title = threadRoute.threadTitle,
                boardName = threadRoute.boardName,
                boardUrl = threadRoute.boardUrl,
                boardId = threadRoute.boardId,
                resCount = threadRoute.resCount
            )
        )
    }

    RouteScaffold(
        route = threadRoute,
        tabsViewModel = tabsViewModel,
        navController = navController,
        openDrawer = openDrawer,
        openTabs = openTabs,
        currentRoutePredicate = { it.key == threadRoute.threadKey && it.boardUrl == threadRoute.boardUrl },
        getViewModel = { tab -> tabsViewModel.getOrCreateThreadViewModel(tab.key + tab.boardUrl) },
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
        updateScrollPosition = { tab, index, offset ->
            tabsViewModel.updateThreadScrollPosition(tab.key, tab.boardUrl, index, offset)
        },
        getScrollBehavior = {
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
                rememberTopAppBarState()
            )
        },
        topBar = { viewModel, uiState, drawer, scrollBehavior ->
            Column {
                ThreadTopBar(
                    onBookmarkClick = { viewModel.openBookmarkSheet() },
                    uiState = uiState,
                    onNavigationClick = drawer,
                    scrollBehavior = scrollBehavior
                )
                if (uiState.isLoading) {
                    LinearProgressIndicator(
                        progress = { uiState.loadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        bottomBar = { viewModel, _ ->
            ThreadBottomBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(56.dp),
                onPostClick = { viewModel.showPostDialog() },
                onTabListClick = { viewModel.openTabListSheet() },
                onRefreshClick = { viewModel.reloadThread() }
            )
        },
        content = { _, uiState, listState, modifier, navController ->
            ThreadScreen(
                modifier = modifier,
                posts = uiState.posts ?: emptyList(),
                listState = listState,
                navController = navController
            )
        },
        optionalSheetContent = { viewModel, uiState ->
            if (uiState.postDialog) {
                PostDialog(
                    onDismissRequest = { viewModel.hidePostDialog() },
                    postFormState = uiState.postFormState,
                    onNameChange = { viewModel.updatePostName(it) },
                    onMailChange = { viewModel.updatePostMail(it) },
                    onMessageChange = { viewModel.updatePostMessage(it) },
                    onPostClick = {
                        parseBoardUrl(uiState.boardInfo.url)?.let { (host, boardKey) ->
                            viewModel.postFirstPhase(
                                host,
                                boardKey,
                                uiState.threadInfo.key,
                                uiState.postFormState.name,
                                uiState.postFormState.mail,
                                uiState.postFormState.message
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
                                viewModel.postTo5chSecondPhase(
                                    host,
                                    boardKey,
                                    uiState.threadInfo.key,
                                    confirmationData
                                )
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
                    onConfirm = null // 確認ボタンは不要なのでnull
                )
            }
        }
    )
}
