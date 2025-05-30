package com.websarva.wings.android.bbsviewer.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.ui.drawer.TabInfo
import com.websarva.wings.android.bbsviewer.ui.drawer.TabsViewModel
import com.websarva.wings.android.bbsviewer.ui.thread.ConfirmationWebView
import com.websarva.wings.android.bbsviewer.ui.thread.PostDialog
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadScreen
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadViewModel
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadTopBar
import com.websarva.wings.android.bbsviewer.ui.util.parseBoardUrl
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
fun NavGraphBuilder.addThreadRoute(
    navController: NavHostController,
    tabsViewModel: TabsViewModel,
    openDrawer: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior
) {
    composable<AppRoute.Thread> { backStackEntry -> // backStackEntry を引数に追加
        val viewModel: ThreadViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsState()
        val threadRoute: AppRoute.Thread = backStackEntry.toRoute() // toRoute() で引数を取得

        val lazyListState = rememberLazyListState()

        // スレッドのキー (threadKey と boardUrl) が変更されたら、スレッドを初期化し、タブ情報を更新する
        LaunchedEffect(threadRoute.threadKey, threadRoute.boardUrl) {
            // ViewModelの初期化
            viewModel.initializeThread( //
                threadKey = threadRoute.threadKey,
                boardInfo = BoardInfo( //
                    name = threadRoute.boardName,
                    url = threadRoute.boardUrl,
                    boardId = threadRoute.boardId,
                ),
                threadTitle = threadRoute.threadTitle
            )

            // タブの処理とスクロール位置の復元
            val existingTab = tabsViewModel.getTabInfo(threadRoute.threadKey, threadRoute.boardUrl) //
            val tabToOpen = TabInfo( //
                key = threadRoute.threadKey,
                title = threadRoute.threadTitle,
                boardName = threadRoute.boardName,
                boardUrl = threadRoute.boardUrl,
                boardId = threadRoute.boardId,
                firstVisibleItemIndex = existingTab?.firstVisibleItemIndex ?: 0,
                firstVisibleItemScrollOffset = existingTab?.firstVisibleItemScrollOffset ?: 0
            )
            tabsViewModel.openThread(tabToOpen) //

            if (existingTab != null) {
                lazyListState.scrollToItem(
                    existingTab.firstVisibleItemIndex,
                    existingTab.firstVisibleItemScrollOffset
                )
            } else {
                // 新しいタブの場合、リストの先頭にスクロール
                lazyListState.scrollToItem(0, 0)
            }
        }

        // スクロール位置の変更を監視し、TabsViewModel に保存する
        // スクロールが頻繁に発生するため、debounce を挟んで更新頻度を調整
        LaunchedEffect(lazyListState) {
            snapshotFlow { lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset }
                .debounce(200L) // 200ミリ秒待ってから最新の値で更新
                .collectLatest { (index, offset) ->
                    tabsViewModel.updateScrollPosition(
                        threadRoute.threadKey,
                        threadRoute.boardUrl,
                        index,
                        offset
                    )
                }
        }

        Scaffold(
            topBar = {
                ThreadTopBar(
                    onFavoriteClick = { viewModel.bookmarkThread() },
                    uiState = uiState,
                    onNavigationClick = openDrawer,
                    scrollBehavior = scrollBehavior
                )
            },
        ) { innerPadding ->
            ThreadScreen(
                modifier = Modifier.padding(innerPadding),
                posts = uiState.posts ?: emptyList(),
                listState = lazyListState // LazyListState を渡す
            )

            if (uiState.postDialog) {
                PostDialog(
                    onDismissRequest = { viewModel.hidePostDialog() },
                    postFormState = uiState.postFormState,
                    onNameChange = { name ->
                        viewModel.updatePostName(name)
                    },
                    onMailChange = { mail ->
                        viewModel.updatePostMail(mail)
                    },
                    onMessageChange = { message ->
                        viewModel.updatePostMessage(message)
                    },
                    onPostClick = {
                        parseBoardUrl(uiState.boardInfo.url)?.let { (host, boardKey) ->
                            viewModel.loadConfirmation(
                                host = host,
                                board = boardKey,
                                threadKey = uiState.threadInfo.key,
                                name = uiState.postFormState.name,
                                mail = uiState.postFormState.mail,
                                message = uiState.postFormState.message
                            )
                        }
                    },
                )
            }

            if (uiState.isConfirmationScreen) {
                uiState.postConfirmation?.let { it1 ->
                    ConfirmationWebView(
                        htmlContent = it1.html,
                        onDismissRequest = { viewModel.hideConfirmationScreen() },
                        onPostClick = {
                            parseBoardUrl(uiState.boardInfo.url)?.let { (host, boardKey) ->
                                uiState.postConfirmation?.let { confirmationData ->
                                    viewModel.postTo5chSecondPhase(
                                        host = host,
                                        board = boardKey,
                                        threadKey = uiState.threadInfo.key,
                                        confirmationData = confirmationData
                                    )
                                }
                            }
                            viewModel.hideConfirmationScreen()
                        }
                    )
                }
            }
        }
    }
}
