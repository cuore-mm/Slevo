package com.websarva.wings.android.bbsviewer.ui.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.websarva.wings.android.bbsviewer.ui.settings.SettingsScreen
import com.websarva.wings.android.bbsviewer.ui.settings.SettingsViewModel

fun NavGraphBuilder.addSettingsRoute(
    navController: NavHostController,
    viewModel: SettingsViewModel
) {
    composable<AppRoute.Settings> {
        val uiState by viewModel.uiState.collectAsState()
        SettingsScreen(
            isDark = uiState.isDark,
            onToggleTheme = {viewModel.toggleTheme()}
        )
    }
}
