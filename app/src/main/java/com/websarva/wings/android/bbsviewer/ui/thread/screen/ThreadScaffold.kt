package com.websarva.wings.android.bbsviewer.ui.thread.screen

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.ui.common.PostDialog
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute
import com.websarva.wings.android.bbsviewer.ui.navigation.RouteScaffold
import com.websarva.wings.android.bbsviewer.ui.tabs.TabsViewModel
import com.websarva.wings.android.bbsviewer.ui.tabs.ThreadTabInfo
import com.websarva.wings.android.bbsviewer.ui.thread.components.ThreadBottomBar
import com.websarva.wings.android.bbsviewer.ui.thread.components.ThreadTopBar
import com.websarva.wings.android.bbsviewer.ui.thread.dialog.ResponseWebViewDialog
import com.websarva.wings.android.bbsviewer.ui.util.parseBoardUrl
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

    LaunchedEffect(threadRoute) {
        val info = tabsViewModel.resolveBoardInfo(
            boardId = threadRoute.boardId,
            boardUrl = threadRoute.boardUrl,
            boardName = threadRoute.boardName
        )
        tabsViewModel.openThreadTab(
            ThreadTabInfo(
                key = threadRoute.threadKey,
                title = threadRoute.threadTitle,
                boardName = info.name,
                boardUrl = info.url,
                boardId = info.boardId,
                resCount = threadRoute.resCount
            )
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)

    RouteScaffold(
        route = threadRoute,
        tabsViewModel = tabsViewModel,
        navController = navController,
        openDrawer = openDrawer,
        openTabs = tabsUiState.openThreadTabs,
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
        scrollBehavior = scrollBehavior,
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
        content = { viewModel, uiState, listState, modifier, navController ->
            LaunchedEffect(uiState.threadInfo) {
                // スレッドタイトルが空でなく、投稿リストが取得済みの場合にタブ情報を更新
                if (uiState.threadInfo.title.isNotEmpty() && uiState.posts != null && uiState.threadInfo.key.isNotEmpty()) {
                    tabsViewModel.updateThreadTabInfo(
                        key = uiState.threadInfo.key,
                        boardUrl = uiState.boardInfo.url,
                        title = uiState.threadInfo.title,
                        resCount = uiState.posts.size
                    )
                    Log.d(
                        "ThreadScaffold",
                        "Updated thread tab info: ${uiState.threadInfo.title}, posts size: ${uiState.posts.size}"
                    )
                }
            }

            ThreadScreen(
                modifier = modifier,
                uiState = uiState,
                listState = listState,
                navController = navController,
                onBottomRefresh = { viewModel.reloadThread() }
            )
        },
        optionalSheetContent = { viewModel, uiState ->
            if (uiState.postDialog) {
                val context = LocalContext.current
                PostDialog(
                    onDismissRequest = { viewModel.hidePostDialog() },
                    name = uiState.postFormState.name,
                    mail = uiState.postFormState.mail,
                    message = uiState.postFormState.message,
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
                                uiState.postFormState.name,
                                uiState.postFormState.mail,
                                uiState.postFormState.message
                            )
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
