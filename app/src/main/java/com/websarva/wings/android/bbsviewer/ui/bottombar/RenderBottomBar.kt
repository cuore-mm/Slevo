package com.websarva.wings.android.bbsviewer.ui.bottombar

import androidx.compose.runtime.Composable
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute
import com.websarva.wings.android.bbsviewer.ui.util.checkCurrentRoute

@Composable
fun RenderBottomBar(
    navController: NavHostController,
    currentDestination: NavDestination?
){
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
            BoardAppBar(
                sortOptions = listOf("Option 1", "Option 2", "Option 3"),
                onSortOptionSelected = {}
            )
        }

        else -> {}
    }
}
