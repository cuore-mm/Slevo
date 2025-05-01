package com.websarva.wings.android.bbsviewer.ui.topbar

import android.os.Build
import androidx.annotation.RequiresApi
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
import com.websarva.wings.android.bbsviewer.ui.bbslist.board.BbsBoardViewModel
import com.websarva.wings.android.bbsviewer.ui.bbslist.category.BbsCategoryViewModel
import com.websarva.wings.android.bbsviewer.ui.bbslist.service.BbsServiceViewModel
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadViewModel
import com.websarva.wings.android.bbsviewer.ui.board.BoardViewModel
import com.websarva.wings.android.bbsviewer.ui.util.isInRoute

@RequiresApi(Build.VERSION_CODES.O)
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

    when {
        currentDestination.isInRoute(
            AppRoute.RouteName.BOOKMARK,
        ) -> HomeTopAppBarScreen(
            title = topAppBarUiState.title,
            scrollBehavior = scrollBehavior
        )

        currentDestination.isInRoute(
            AppRoute.RouteName.BBS_LIST
        ) -> {
            val viewModel: BbsServiceViewModel = hiltViewModel(navBackStackEntry!!)
            val uiState by viewModel.uiState.collectAsState()
            Box {
                // 通常モードの AppBar
                AnimatedVisibility(
                    visible = !uiState.selectMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    BbsServiceListTopBarScreen(
                        onNavigationClick = {},
                        onAddClick = { viewModel.toggleAddDialog(true) },
                        onSearchClick = {}
                    )
                }
                // 編集モードの AppBar（上からスライドダウン）
                AnimatedVisibility(
                    visible = uiState.selectMode,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    SelectedBbsListTopBarScreen(
                        onBack = { viewModel.toggleSelectMode(false) },
                        selectedCount = uiState.selected.size
                    )
                }
            }
        }

        currentDestination.isInRoute(
            AppRoute.RouteName.BOARD_CATEGORY_LIST
        ) -> {
            val viewModel: BbsCategoryViewModel = hiltViewModel(navBackStackEntry!!)
            val uiState by viewModel.uiState.collectAsState()
            BbsCategoryListTopBarScreen(
                title = uiState.serviceName,
                onNavigationClick = {},
                onSearchClick = {}
            )
        }

        currentDestination.isInRoute(
            AppRoute.RouteName.CATEGORISED_BOARD_LIST
        ) -> {
            val viewModel: BbsBoardViewModel = hiltViewModel(navBackStackEntry!!)
            val uiState by viewModel.uiState.collectAsState()

            BbsCategoryListTopBarScreen(
                title = "${uiState.serviceName} > ${uiState.categoryName}",
                onNavigationClick = {},
                onSearchClick = {}
            )

        }

        currentDestination.isInRoute(
            AppRoute.RouteName.BOARD
        ) -> {
            val viewModel: BoardViewModel = hiltViewModel(navBackStackEntry!!)
            val uiState by viewModel.uiState.collectAsState()

            BoardTopBarScreen(
                title = uiState.boardInfo.name,
                onNavigationClick = {},
                onBookmarkClick = {},
                onInfoClick = {},
                scrollBehavior = scrollBehavior
            )

        }

        currentDestination.isInRoute(
            AppRoute.RouteName.THREAD
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
