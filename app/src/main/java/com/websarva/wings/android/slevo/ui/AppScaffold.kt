package com.websarva.wings.android.slevo.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.websarva.wings.android.slevo.ui.bottombar.MoreMenuDialog
import com.websarva.wings.android.slevo.ui.navigation.DeepLinkHandler
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.RootNavGraph
import com.websarva.wings.android.slevo.ui.settings.SettingsViewModel
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
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
    var rootBottomChromeHeights by remember { mutableStateOf(RootBottomChromeHeights()) }
    val bottomChromeOwner = when {
        navBackStackEntry?.destination?.hasRoute<AppRoute.MainShell>() == true -> {
            RootBottomChromeOwner.MainShell
        }

        navBackStackEntry?.destination?.hasRoute<AppRoute.Board>() == true ||
            navBackStackEntry?.destination?.hasRoute<AppRoute.Thread>() == true -> {
            RootBottomChromeOwner.Bbs
        }

        else -> RootBottomChromeOwner.None
    }
    val bottomChromeHeightPx = rootBottomChromeHeights.heightFor(bottomChromeOwner)
    val bottomChromeHeight = with(LocalDensity.current) {
        bottomChromeHeightPx.toDp()
    }

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

    Box(modifier = modifier.fillMaxSize()) {
        SharedTransitionLayout {
            RootNavGraph(
                navController = navController,
                topBarState = topBarState,
                settingsViewModel = settingsViewModel,
                openDrawer = openDrawer,
                tabSessionStore = tabSessionStore,
                sharedTransitionScope = this,
                onMainShellBottomChromeHeightChanged = { height ->
                    if (rootBottomChromeHeights.mainShellHeightPx != height) {
                        rootBottomChromeHeights = rootBottomChromeHeights.copy(
                            mainShellHeightPx = height,
                        )
                    }
                },
                onBbsBottomChromeHeightChanged = { height ->
                    if (rootBottomChromeHeights.bbsHeightPx != height) {
                        rootBottomChromeHeights = rootBottomChromeHeights.copy(
                            bbsHeightPx = height,
                        )
                    }
                },
                onMoreClick = { showMoreMenu = true },
                onExitApp = onExitApp,
            )
        }

        // アプリ全体の通知はRoot Navigationの遷移対象外にし、下部chromeだけを避ける。
        RootSnackbarHost(
            hostState = pendingRestoreSnackbarHostState,
            bottomChromeHeight = bottomChromeHeight,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
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
