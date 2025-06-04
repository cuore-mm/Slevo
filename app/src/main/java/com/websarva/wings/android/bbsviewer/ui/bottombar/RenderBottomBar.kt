package com.websarva.wings.android.bbsviewer.ui.bottombar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.ui.bbslist.service.ServiceListViewModel
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadViewModel
import com.websarva.wings.android.bbsviewer.ui.util.isInRoute

@Composable
fun RenderBottomBar(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    navBackStackEntry: NavBackStackEntry?
) {
    val currentDestination = navBackStackEntry?.destination
    when {
        currentDestination.isInRoute(
            AppRoute.RouteName.BOOKMARK,
            AppRoute.RouteName.BBS_SERVICE_GROUP
        )
            -> {
            val viewModel: ServiceListViewModel = hiltViewModel(navBackStackEntry!!)
            val uiState by viewModel.uiState.collectAsState()

            if (!uiState.selectMode) {
                HomeBottomNavigationBar(
                    modifier = modifier,
                    currentDestination = currentDestination,
                    onClick = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            } else {
                BbsSelectBottomBar(
                    onDelete = { viewModel.toggleDeleteDialog(true) },
                    onOpen = { /* TODO: Handle open action */ }
                )
            }
        }

        else -> {}
    }
}
