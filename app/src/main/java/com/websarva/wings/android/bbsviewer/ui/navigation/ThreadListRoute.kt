package com.websarva.wings.android.bbsviewer.ui.navigation

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.websarva.wings.android.bbsviewer.ui.threadlist.ThreadListScreen
import com.websarva.wings.android.bbsviewer.ui.threadlist.ThreadListViewModel
import com.websarva.wings.android.bbsviewer.ui.topbar.AppBarType
import com.websarva.wings.android.bbsviewer.ui.topbar.TopAppBarViewModel
import com.websarva.wings.android.bbsviewer.ui.util.keyToDatUrl

@RequiresApi(Build.VERSION_CODES.O)
fun NavGraphBuilder.addThreadListRoute(
    navController: NavHostController,
    topAppBarViewModel: TopAppBarViewModel
) {
    composable<AppRoute.ThreadList> {
        val viewModel: ThreadListViewModel = hiltViewModel()
        val threadListUiState by viewModel.uiState.collectAsState()
        val threadList: AppRoute.ThreadList = it.toRoute()
        topAppBarViewModel.setTopAppBar(
            title = threadList.boardName,
            type = AppBarType.HomeWithScroll
        )
        if (threadListUiState.threads == null) {
            viewModel.loadThreadList(threadList.boardUrl)
        }
        ThreadListScreen(
            threads = threadListUiState.threads ?: emptyList(),
            onClick = { threadInfo ->
                navController.navigate(
                    AppRoute.Thread(
                        title = threadInfo.title,
                        datUrl = keyToDatUrl(threadList.boardUrl, threadInfo.key),
                        resCount = threadInfo.resCount
                    )
                ) {
                    launchSingleTop = true
                }
            },
            isRefreshing = threadListUiState.isLoading,
            onRefresh = { viewModel.loadThreadList(threadList.boardUrl) }
        )
    }
}
