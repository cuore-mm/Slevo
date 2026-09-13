package com.websarva.wings.android.slevo.ui.mainshell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.websarva.wings.android.slevo.ui.bottombar.RenderBottomBar
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.MainShellMode
import com.websarva.wings.android.slevo.ui.navigation.MainShellBbsOrigin
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore

/**
 * NavigationBarとMainShell内部のNavigationを所有するRoot destination。
 *
 * Root NavigationからBoardやSettingsへ遷移しても、MainShellごとのinner back stackを保持する。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MainShell(
    route: AppRoute.MainShell,
    sourceRoute: AppRoute?,
    tabSessionStore: TabSessionStore,
    topBarState: androidx.compose.material3.TopAppBarState,
    openDrawer: () -> Unit,
    onMoreClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBoardSelected: (MainShellBbsOrigin, NavHostController, String, AppRoute.Board) -> Unit,
    onThreadSelected: (MainShellBbsOrigin, NavHostController, String, AppRoute.Thread) -> Unit,
    onOpenBoard: (AppRoute.Board) -> Unit,
    onOpenThread: (AppRoute.Thread) -> Unit,
    onMainShellBottomChromeHeightChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val mainShellNavController = rememberNavController()
    val currentEntry by mainShellNavController.currentBackStackEntryAsState()
    val contextualSourceRoute = sourceRoute.takeIf {
        route.mode == MainShellMode.ContextualTabs
    }

    MainShellBackHandler(mainShellNavController)

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Box(
                modifier = Modifier.onSizeChanged { size ->
                    onMainShellBottomChromeHeightChanged(size.height)
                },
            ) {
                RenderBottomBar(
                    navController = mainShellNavController,
                    navBackStackEntry = currentEntry,
                    onMoreClick = onMoreClick,
                )
            }
        },
    ) { innerPadding ->
        MainShellNavGraph(
            appChromePadding = innerPadding,
            navController = mainShellNavController,
            startDestination = route.startDestination,
            sourceRoute = contextualSourceRoute,
            openDrawer = openDrawer,
            tabSessionStore = tabSessionStore,
            topBarState = topBarState,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            onBoardSelected = { origin, tabsEntryId, boardRoute ->
                onBoardSelected(origin, mainShellNavController, tabsEntryId, boardRoute)
            },
            onThreadSelected = { origin, tabsEntryId, threadRoute ->
                onThreadSelected(origin, mainShellNavController, tabsEntryId, threadRoute)
            },
            onOpenBoard = onOpenBoard,
            onOpenThread = onOpenThread,
        )
    }
}

/**
 * MainShell内にpop可能なentryがある場合だけinner Backを消費する。
 *
 * start destinationではhandlerを無効にし、Root NavControllerのBack処理へ委譲する。
 */
@Composable
internal fun MainShellBackHandler(navController: NavHostController) {
    // MainShell内に履歴がある場合は、Root Backより先にinner Backを消費する。
    BackHandler(enabled = navController.previousBackStackEntry != null) {
        navController.popBackStack()
    }
}
