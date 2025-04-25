package com.websarva.wings.android.bbsviewer.ui.topbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.ui.bbslist.service.BbsServiceViewModel
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadViewModel
import com.websarva.wings.android.bbsviewer.ui.util.checkCurrentRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderTopBar(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    topAppBarViewModel: TopAppBarViewModel,
    navBackStackEntry: NavBackStackEntry?,
) {
    val currentDestination = navBackStackEntry?.destination
    val topAppBarUiState by topAppBarViewModel.uiState.collectAsState()

    when  {
        checkCurrentRoute(
            currentDestination = currentDestination,
            listOf(
                AppRoute.RouteName.BOOKMARK,
            )
        ) -> HomeTopAppBarScreen(title = topAppBarUiState.title)
//        AppBarType.HomeWithScroll -> HomeTopAppBarScreen(
//            title = topAppBarUiState.title,
//            scrollBehavior = scrollBehavior
//        )

//        AppBarType.Small -> SmallTopAppBarScreen(
//            title = topAppBarUiState.title,
//            onNavigateUp = { navController.navigateUp() },
//            scrollBehavior = scrollBehavior
//        )

        checkCurrentRoute(
             currentDestination =currentDestination,
                listOf(AppRoute.RouteName.REGISTERED_BBS)
        )  -> {
            val viewModel: BbsServiceViewModel = hiltViewModel(navBackStackEntry!!)
            val uiState by viewModel.uiState.collectAsState()
            Box {
                // 通常モードの AppBar
                AnimatedVisibility(
                    visible = !uiState.selectMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    BBSListTopBarScreen(
                        onNavigationClick = {},
                        onAddClick       = { viewModel.toggleAddBBSDialog(true) },
                        onSearchClick     = {}
                    )
                }
                // 編集モードの AppBar（上からスライドダウン）
                AnimatedVisibility(
                    visible = uiState.selectMode,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    SelectedBbsListTopBarScreen(
                        onBack     = { viewModel.toggleSelectMode(false) },
                        selectedCount = uiState.selected.size
                    )
                }
            }
        }

        checkCurrentRoute(
            currentDestination = currentDestination,
            listOf(
                AppRoute.RouteName.THREAD
            )
        ) -> {
            val threadViewModel: ThreadViewModel? =
                navBackStackEntry?.let { hiltViewModel<ThreadViewModel>(it) }
            // threadViewModel が null でない場合にだけ処理を実施
            threadViewModel?.let { viewModel ->
                val uiState by viewModel.uiState.collectAsState()
                ThreadTopBar(
                    scrollBehavior = scrollBehavior,
                    onFavoriteClick = { viewModel.bookmarkThread() },
                    uiState = uiState
                )
            }
        }

        else -> {}
    }
}
