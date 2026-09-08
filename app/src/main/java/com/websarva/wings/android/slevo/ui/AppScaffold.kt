package com.websarva.wings.android.slevo.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.websarva.wings.android.slevo.ui.bottombar.MoreMenuDialog
import com.websarva.wings.android.slevo.ui.bottombar.RenderBottomBar
import com.websarva.wings.android.slevo.ui.navigation.DeepLinkHandler
import com.websarva.wings.android.slevo.ui.navigation.AppNavGraph
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.settings.SettingsViewModel
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.util.isInRoute
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

/**
 * Hosts the main app scaffold and reacts to Deep Link events.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    settingsViewModel: SettingsViewModel,
    tabSessionStore: TabSessionStore,
    pendingRestoreResultUiState: PendingRestoreResultUiState = PendingRestoreResultUiState(),
    onPendingRestoreResultDisplayed: (String) -> Unit = {},
    deepLinkUrlFlow: StateFlow<String?>,
    onDeepLinkConsumed: () -> Unit,
    onExitApp: () -> Unit = {},
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val deepLinkUrl by deepLinkUrlFlow.collectAsState()
    val hasRootBottomBar = navBackStackEntry?.destination.isInRoute(
        AppRoute.RouteName.BOOKMARK_LIST,
        AppRoute.RouteName.BBS_SERVICE_GROUP,
        AppRoute.RouteName.TABS,
    )

    /* ① 共有する TopAppBarState を用意 */
    val topBarState = rememberTopAppBarState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

    // 画面遷移ごとにheightOffsetをリセット
    LaunchedEffect(navBackStackEntry?.destination?.route) {
        topBarState.heightOffset = 0f
    }

    var showMoreMenu by remember { mutableStateOf(false) }
    val pendingRestoreSnackbarHostState = remember { SnackbarHostState() }

    PendingRestoreResultSnackbar(
        notification = pendingRestoreResultUiState.notification,
        snackbarHostState = pendingRestoreSnackbarHostState,
        onDisplayed = onPendingRestoreResultDisplayed,
    )

    DeepLinkHandler(
        deepLinkUrl = deepLinkUrl,
        navController = navController,
        tabSessionStore = tabSessionStore,
        onConsumed = onDeepLinkConsumed
    )

    Scaffold(
        // ルートは下部アプリ chrome の占有領域だけを子画面へ渡す。
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            SnackbarHost(
                hostState = pendingRestoreSnackbarHostState,
                modifier = if (hasRootBottomBar) {
                    Modifier
                } else {
                    // ルート下部バーがない画面ではSnackbar自身がnavigation barを避ける。
                    Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                    )
                },
            )
        },
        bottomBar = {
            RenderBottomBar(
                modifier = Modifier,
                navController = navController,
                navBackStackEntry = navBackStackEntry,
                onMoreClick = { showMoreMenu = true }
            )
        }
    ) { innerPadding ->

        SharedTransitionLayout {
            AppNavGraph(
                appChromePadding = innerPadding,
                navController = navController,
                topBarState = topBarState,
                settingsViewModel = settingsViewModel,
                openDrawer = openDrawer,
                tabSessionStore = tabSessionStore,
                sharedTransitionScope = this,
                onExitApp = onExitApp,
            )
        }
    }

    if (showMoreMenu) {
        MoreMenuDialog(
            onDismissRequest = { showMoreMenu = false },
            onHistoryClick = {
                showMoreMenu = false
                navController.navigate(AppRoute.HistoryList)
            },
            onSettingsClick = {
                showMoreMenu = false
                navController.navigate(AppRoute.SettingsHome)
            },
            onAboutClick = {
                showMoreMenu = false
                navController.navigate(AppRoute.About)
            }
        )
    }
}
