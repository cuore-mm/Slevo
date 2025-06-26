package com.websarva.wings.android.bbsviewer.ui.navigation

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.ui.tabs.TabsViewModel
import com.websarva.wings.android.bbsviewer.ui.tabs.ThreadTabInfo
import com.websarva.wings.android.bbsviewer.ui.thread.ConfirmationWebView
import com.websarva.wings.android.bbsviewer.ui.thread.PostDialog
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadBottomBar
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadScreen
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadTopBar
import com.websarva.wings.android.bbsviewer.ui.util.parseBoardUrl

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.addThreadRoute(
    navController: NavHostController,
    tabsViewModel: TabsViewModel,
    openDrawer: () -> Unit,
) {
    composable<AppRoute.Thread> { backStackEntry ->
        val threadRoute: AppRoute.Thread = backStackEntry.toRoute()
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
            content = { _, uiState, listState, modifier ->
                ThreadScreen(
                    modifier = modifier,
                    posts = uiState.posts ?: emptyList(),
                    listState = listState
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
                                viewModel.loadConfirmation(
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
                        ConfirmationWebView(
                            htmlContent = confirmationData.html,
                            onDismissRequest = { viewModel.hideConfirmationScreen() },
                            onPostClick = {
                                parseBoardUrl(uiState.boardInfo.url)?.let { (host, boardKey) ->
                                    viewModel.postTo5chSecondPhase(
                                        host,
                                        boardKey,
                                        uiState.threadInfo.key,
                                        confirmationData
                                    )
                                }
                                viewModel.hideConfirmationScreen()
                            }
                        )
                    }
                }
            }
        )
    }
}
