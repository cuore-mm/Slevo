package com.websarva.wings.android.bbsviewer.ui.bottombar

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadViewModel
import com.websarva.wings.android.bbsviewer.ui.util.checkCurrentRoute

@Composable
fun RenderBottomBar(
    navController: NavHostController,
    navBackStackEntry: NavBackStackEntry?
) {
    val currentDestination = navBackStackEntry?.destination
    when {
        checkCurrentRoute(
            currentDestination = currentDestination,
            listOf(
                AppRoute.RouteName.BOOKMARK,
                AppRoute.RouteName.REGISTERED_BBS
            )
        ) -> {
            HomeBottomNavigationBar(
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
        }

        checkCurrentRoute(
            currentDestination = currentDestination,
            listOf(
                AppRoute.RouteName.THREAD_LIST
            )
        ) -> {
            BoardBottomBar(
                sortOptions = listOf("Option 1", "Option 2", "Option 3"),
                onSortOptionSelected = {}
            )
        }

        checkCurrentRoute(
            currentDestination = currentDestination,
            listOf(
                AppRoute.RouteName.THREAD
            )
        ) -> {
            val threadViewModel: ThreadViewModel? =
                navBackStackEntry?.let { hiltViewModel<ThreadViewModel>(it) }
            ThreadBottomBar(
                onPostClick = { threadViewModel?.showPostDialog() },
            )
        }

        else -> {}
    }
}
