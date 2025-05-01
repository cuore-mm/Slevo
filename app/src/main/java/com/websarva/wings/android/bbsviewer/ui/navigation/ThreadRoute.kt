package com.websarva.wings.android.bbsviewer.ui.navigation

import android.util.Log
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.ui.thread.ConfirmationWebView
import com.websarva.wings.android.bbsviewer.ui.thread.PostDialog
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadScreen
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadViewModel
import com.websarva.wings.android.bbsviewer.ui.topbar.AppBarType
import com.websarva.wings.android.bbsviewer.ui.topbar.TopAppBarViewModel
import com.websarva.wings.android.bbsviewer.ui.util.parseBoardUrl

fun NavGraphBuilder.addThreadRoute(
    topAppBarViewModel: TopAppBarViewModel
) {
    composable<AppRoute.Thread> {
        val viewModel: ThreadViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsState()
        val thread: AppRoute.Thread = it.toRoute()
        topAppBarViewModel.setTopAppBar(
            type = AppBarType.Thread
        )
        if (uiState.posts == null) {
            viewModel.initializeThread(
                threadKey = thread.threadKey,
                datUrl = thread.datUrl,
                boardInfo = BoardInfo(
                    name = thread.boardName,
                    url = thread.boardUrl,
                    boardId = 0,
                )
            )
            Log.i("ThreadRoute", thread.datUrl)
        }
        ThreadScreen(
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
