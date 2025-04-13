package com.websarva.wings.android.bbsviewer.ui.topbar

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderTopBar(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    topAppBarViewModel: TopAppBarViewModel,
    navBackStackEntry: NavBackStackEntry?
) {
    val topAppBarUiState by topAppBarViewModel.uiState.collectAsState()

    when (topAppBarUiState.type) {
        AppBarType.Home -> HomeTopAppBarScreen(title = topAppBarUiState.title)
        AppBarType.HomeWithScroll -> HomeTopAppBarScreen(
            title = topAppBarUiState.title,
            scrollBehavior = scrollBehavior
        )

        AppBarType.Small -> SmallTopAppBarScreen(
            title = topAppBarUiState.title,
            onNavigateUp = { navController.navigateUp() },
            scrollBehavior = scrollBehavior
        )

        AppBarType.Thread -> {
            val threadViewModel: ThreadViewModel? =
                navBackStackEntry?.let { hiltViewModel<ThreadViewModel>(it) }
            // threadViewModel が null でない場合にだけ処理を実施
            threadViewModel?.let { viewModel ->
                val uiState by viewModel.uiState.collectAsState()
                ThreadTopBar(
                    title = uiState.title,
                    scrollBehavior = scrollBehavior
                )
            }
        }

        else -> {}
    }
}
