package com.websarva.wings.android.bbsviewer.ui.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.websarva.wings.android.bbsviewer.ui.settings.SettingsGeneralScreen
import com.websarva.wings.android.bbsviewer.ui.settings.SettingsNgScreen
import com.websarva.wings.android.bbsviewer.ui.settings.SettingsScreen
import com.websarva.wings.android.bbsviewer.ui.settings.SettingsViewModel

fun NavGraphBuilder.addSettingsRoute(
    navController: NavHostController,
    viewModel: SettingsViewModel
) {
    navigation<AppRoute.Settings>(startDestination = AppRoute.SettingsHome) {
        composable<AppRoute.SettingsHome> {
            SettingsScreen(
                onGeneralClick = { navController.navigate(AppRoute.SettingsGeneral) },
                onNgClick = { navController.navigate(AppRoute.SettingsNg) }
            )
        }
        composable<AppRoute.SettingsGeneral> {
            val uiState by viewModel.uiState.collectAsState()
            SettingsGeneralScreen(
                isDark = uiState.isDark,
                onToggleTheme = { viewModel.toggleTheme() },
                onNavigateUp = { navController.navigateUp() }
            )
        }
        composable<AppRoute.SettingsNg> {
            SettingsNgScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }
    }
}

