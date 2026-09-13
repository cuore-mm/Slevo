package com.websarva.wings.android.slevo.ui.bottombar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.ui.bbslist.service.ServiceListViewModel
import com.websarva.wings.android.slevo.ui.bookmarklist.BookmarkViewModel
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.navigateToMainShellTopLevel
import com.websarva.wings.android.slevo.ui.util.isInRoute

/**
 * 現在のMainShell内destinationに対応する下部バーを描画する。
 *
 * Tabs、Bookmark、BBS一覧だけがこのroute chromeを使用し、選択モード中は一覧固有の操作バーへ
 * 切り替える。それ以外では共通のトップレベル切替をMainShell内controllerへ委譲する。
 */
@Composable
fun RenderBottomBar(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    navBackStackEntry: NavBackStackEntry?,
    onMoreClick: () -> Unit
) {
    val currentDestination = navBackStackEntry?.destination
    when {
        currentDestination.isInRoute(
            AppRoute.RouteName.BOOKMARK_LIST
        ) -> {
            val viewModel: BookmarkViewModel = hiltViewModel(navBackStackEntry!!)
            val uiState by viewModel.uiState.collectAsState()

            if (!uiState.selectMode) {
                NavigationBottomBar(
                    modifier = modifier,
                    currentDestination = currentDestination,
                    onClick = navController::navigateToMainShellTopLevel,
                    onMoreClick = onMoreClick
                )
            } else {
                BookmarkSelectBottomBar(
                    modifier = modifier,
                    onEdit = { viewModel.openEditSheet() },
                    onOpen = { /* TODO: Handle open action */ }
                )
            }
        }

        currentDestination.isInRoute(
            AppRoute.RouteName.BBS_SERVICE_GROUP,
            AppRoute.RouteName.TABS
        ) -> {
            val viewModel: ServiceListViewModel = hiltViewModel(navBackStackEntry!!)
            val uiState by viewModel.uiState.collectAsState()

            if (!uiState.selectMode) {
                NavigationBottomBar(
                    modifier = modifier,
                    currentDestination = currentDestination,
                    onClick = navController::navigateToMainShellTopLevel,
                    onMoreClick = onMoreClick
                )
            } else {
                BbsSelectBottomBar(
                    modifier = modifier,
                    onDelete = { viewModel.toggleDeleteDialog(true) },
                    onOpen = { /* TODO: Handle open action */ }
                )
            }
        }

        else -> {}
    }
}
