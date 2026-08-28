package com.websarva.wings.android.slevo.ui.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.websarva.wings.android.slevo.ui.settings.SettingsCookieScreen
import com.websarva.wings.android.slevo.ui.settings.SettingsGeneralScreen
import com.websarva.wings.android.slevo.ui.settings.SettingsGestureScreen
import com.websarva.wings.android.slevo.ui.settings.SettingsNgScreen
import com.websarva.wings.android.slevo.ui.settings.SettingsScreen
import com.websarva.wings.android.slevo.ui.settings.SettingsThreadScreen
import com.websarva.wings.android.slevo.ui.settings.SettingsViewModel
import com.websarva.wings.android.slevo.ui.settings.backup.BackupScreen

fun NavGraphBuilder.addSettingsRoute(
    navController: NavHostController,
    viewModel: SettingsViewModel,
    onExitApp: () -> Unit = {},
) {
    navigation<AppRoute.Settings>(
        startDestination = AppRoute.SettingsHome,
        enterTransition = { defaultEnterTransition() },
        exitTransition = { defaultExitTransition() },
        popEnterTransition = { defaultPopEnterTransition() },
        popExitTransition = { defaultPopExitTransition() }
    ) {
        composable<AppRoute.SettingsHome> {
            SettingsScreen(
                onGeneralClick = { navController.navigate(AppRoute.SettingsGeneral) },
                onGestureClick = { navController.navigate(AppRoute.SettingsGesture) },
                onThreadClick = { navController.navigate(AppRoute.SettingsThread) },
                onNgClick = { navController.navigate(AppRoute.SettingsNg) },
                onCookieClick = { navController.navigate(AppRoute.SettingsCookie) },
                onBackupClick = { navController.navigate(AppRoute.SettingsBackup) },
                onNavigateUp = { navController.navigateUp() }
            )
        }
        composable<AppRoute.SettingsGeneral> {
            val uiState by viewModel.uiState.collectAsState()
            SettingsGeneralScreen(
                themeMode = uiState.themeMode,
                isRedirect5chNetToIoEnabled = uiState.isRedirect5chNetToIoEnabled,
                isReplyNotificationEnabled = uiState.isReplyNotificationEnabled,
                onSelectThemeMode = { viewModel.updateThemeMode(it) },
                onToggleRedirect5chNetToIoEnabled = {
                    viewModel.updateRedirect5chNetToIoEnabled(it)
                },
                onToggleReplyNotification = { viewModel.updateReplyNotificationEnabled(it) },
                onReplyNotificationPermissionResult = {
                    viewModel.updateReplyNotificationPermissionResult(it)
                },
                onNavigateUp = { navController.navigateUp() }
            )
        }
        composable<AppRoute.SettingsNg> {
            SettingsNgScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }
        composable<AppRoute.SettingsThread> {
            SettingsThreadScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }
        composable<AppRoute.SettingsCookie> {
            SettingsCookieScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }
        composable<AppRoute.SettingsGesture> {
            SettingsGestureScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }
        composable<AppRoute.SettingsBackup> {
            BackupScreen(
                onNavigateUp = { navController.navigateUp() },
                onFinishActivity = onExitApp,
            )
        }
    }
}
