package com.websarva.wings.android.bbsviewer.ui.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.websarva.wings.android.bbsviewer.ui.board.BoardScreen
import com.websarva.wings.android.bbsviewer.ui.board.BoardViewModel
import com.websarva.wings.android.bbsviewer.ui.util.keyToDatUrl

@RequiresApi(Build.VERSION_CODES.O)
fun NavGraphBuilder.addBoardRoute(
    navController: NavHostController,
) {
    composable<AppRoute.Board> {
        val board: AppRoute.Board = it.toRoute()

        val viewModel: BoardViewModel = hiltViewModel(it)
        val threadListUiState by viewModel.uiState.collectAsState()

        BoardScreen(
            threads = threadListUiState.threads ?: emptyList(),
            onClick = { threadInfo ->
                navController.navigate(
                    AppRoute.Thread(
                        threadKey = threadInfo.key,
                        datUrl = keyToDatUrl(board.boardUrl, threadInfo.key),
                        boardName = board.boardName,
                        boardUrl = board.boardUrl,
                    )
                ) {
                    launchSingleTop = true
                }
            },
            isRefreshing = threadListUiState.isLoading,
            onRefresh = {
//                viewModel.loadThreadList(board.boardUrl)
            }
        )
    }
}
