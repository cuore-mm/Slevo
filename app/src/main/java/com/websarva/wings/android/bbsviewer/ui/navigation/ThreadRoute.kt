package com.websarva.wings.android.bbsviewer.ui.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadScreen
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadViewModel
import com.websarva.wings.android.bbsviewer.ui.topbar.AppBarType
import com.websarva.wings.android.bbsviewer.ui.topbar.TopAppBarViewModel

fun NavGraphBuilder.addThreadRoute(
    topAppBarViewModel: TopAppBarViewModel
) {
    composable<AppRoute.Thread> {
        val viewModel: ThreadViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsState()
        val thread: AppRoute.Thread = it.toRoute()
        topAppBarViewModel.setTopAppBar(
            title = thread.title,
            type = AppBarType.Thread
        )
        if (uiState.posts == null) {
            viewModel.loadThread(thread.datUrl)
        }
        ThreadScreen(
            posts = uiState.posts ?: emptyList(),
        )
    }
}
