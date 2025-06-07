package com.websarva.wings.android.bbsviewer.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.websarva.wings.android.bbsviewer.ui.drawer.TabsViewModel
import com.websarva.wings.android.bbsviewer.ui.tabs.TabsScreen

fun NavGraphBuilder.addTabsRoute(
    navController: NavHostController,
    tabsViewModel: TabsViewModel
) {
    composable<AppRoute.Tabs> {
        TabsScreen(
            tabsViewModel = tabsViewModel,
            navController = navController
        )
    }
}
