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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.ui.bbslist.board.BbsBoardViewModel
import com.websarva.wings.android.bbsviewer.ui.bbslist.BbsListTopBarScreen
import com.websarva.wings.android.bbsviewer.ui.bbslist.category.BoardCategoryListViewModel
import com.websarva.wings.android.bbsviewer.ui.common.SelectedTopBarScreen
import com.websarva.wings.android.bbsviewer.ui.bbslist.service.ServiceListTopBarScreen
import com.websarva.wings.android.bbsviewer.ui.bbslist.service.ServiceListViewModel
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadTopBar
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadViewModel
import com.websarva.wings.android.bbsviewer.ui.util.isInRoute

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderTopBar(
    navController: NavHostController,
    navBackStackEntry: NavBackStackEntry?,
) {
    val currentDestination = navBackStackEntry?.destination

    when {
        currentDestination.isInRoute(
            AppRoute.RouteName.BOOKMARK_LIST,
        ) -> ServiceListTopBarScreen(
            onNavigationClick = { },
            onAddClick = { },
            onSearchClick = { }
        )

        currentDestination.isInRoute(
            AppRoute.RouteName.SERVICE_LIST
        ) -> {
            val viewModel: ServiceListViewModel = hiltViewModel(navBackStackEntry!!)
            val uiState by viewModel.uiState.collectAsState()
            Box {
                // 通常モードの AppBar
                AnimatedVisibility(
                    visible = !uiState.selectMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    ServiceListTopBarScreen(
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
                    SelectedTopBarScreen(
                        onBack = { viewModel.toggleSelectMode(false) },
                        selectedCount = uiState.selected.size
                    )
                }
            }
        }

        currentDestination.isInRoute(
            AppRoute.RouteName.BOARD_CATEGORY_LIST
        ) -> {
            val viewModel: BoardCategoryListViewModel = hiltViewModel(navBackStackEntry!!)
            val uiState by viewModel.uiState.collectAsState()
            BbsListTopBarScreen(
                title = uiState.serviceName,
                onNavigationClick = {},
                onSearchClick = {}
            )
        }

        currentDestination.isInRoute(
            AppRoute.RouteName.BOARD_LIST_BY_CATEGORY
        ) -> {
            val viewModel: BbsBoardViewModel = hiltViewModel(navBackStackEntry!!)
            val uiState by viewModel.uiState.collectAsState()

            BbsListTopBarScreen(
                title = "${uiState.serviceName} > ${uiState.categoryName}",
                onNavigationClick = {},
                onSearchClick = {}
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
                    onBookmarkClick = { viewModel.openBookmarkSheet() },
                    uiState = uiState,
                    onNavigationClick = {}
                )
            }
        }

        else -> {}
    }
}
