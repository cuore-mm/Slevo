package com.websarva.wings.android.slevo.ui.navigation

import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel

/**
 * 板画面への遷移を共通化した拡張関数。
 */
fun NavHostController.navigateToBoard(
    route: AppRoute.Board,
    tabsViewModel: TabsViewModel? = null,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    tabsViewModel?.let { viewModel ->
        viewModel.ensureBoardTab(route).let { index ->
            if (index >= 0) {
                viewModel.setBoardCurrentPage(index)
            }
        }
    }
    navigate(route) {
        launchSingleTop = true
        builder()
    }
}

/**
 * スレ画面への遷移を共通化した拡張関数。
 */
fun NavHostController.navigateToThread(
    route: AppRoute.Thread,
    tabsViewModel: TabsViewModel? = null,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    tabsViewModel?.let { viewModel ->
        viewModel.ensureThreadTab(route).let { index ->
            if (index >= 0) {
                viewModel.setThreadCurrentPage(index)
            }
        }
    }
    navigate(route) {
        launchSingleTop = true
        builder()
    }
}
