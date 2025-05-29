package com.websarva.wings.android.bbsviewer.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.addThreadRoute(
    navController: NavHostController,
    tabsViewModel: TabsViewModel,
    openDrawer: () -> Unit,
) {
    composable<AppRoute.Thread> {
        val viewModel: ThreadViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsState()
        val thread: AppRoute.Thread = it.toRoute()

        // LaunchedEffect を使って、スレッド遷移時に一度だけタブを開く
        LaunchedEffect(thread.threadKey, thread.boardUrl) {
            val tab = TabInfo(
                key = thread.threadKey,
                title = thread.threadTitle,
                boardName = thread.boardName,
                boardUrl = thread.boardUrl,
                boardId = thread.boardId
            )
            tabsViewModel.openThread(tab)
        }

        if (uiState.posts == null) {
            viewModel.initializeThread(
                threadKey = thread.threadKey,
                boardInfo = BoardInfo(
                    name = thread.boardName,
                    url = thread.boardUrl,
                    boardId = thread.boardId,
                ),
                threadTitle = thread.threadTitle
            )
        }

        Scaffold(
            topBar = {
                ThreadTopBar(
                    onFavoriteClick = { viewModel.bookmarkThread() },
                    uiState = uiState,
                    onNavigationClick = openDrawer
                )
            },
        ) { innerPadding ->
            ThreadScreen(
                modifier = Modifier.padding(innerPadding),
                posts = uiState.posts ?: emptyList()
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
