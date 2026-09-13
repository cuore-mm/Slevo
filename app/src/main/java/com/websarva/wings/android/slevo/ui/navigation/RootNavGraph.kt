package com.websarva.wings.android.slevo.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.websarva.wings.android.slevo.ui.about.AboutScreen
import com.websarva.wings.android.slevo.ui.about.AboutViewModel
import com.websarva.wings.android.slevo.ui.about.OpenSourceLicenseScreen
import com.websarva.wings.android.slevo.ui.board.screen.BoardScaffold
import com.websarva.wings.android.slevo.ui.history.HistoryListScaffold
import com.websarva.wings.android.slevo.ui.mainshell.MainShell
import com.websarva.wings.android.slevo.ui.settings.SettingsViewModel
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.thread.screen.ThreadScaffold
import com.websarva.wings.android.slevo.ui.viewer.ImageViewerScreen
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * MainShellとMainShell外の画面をRoot Navigationで管理する。
 *
 * MainShell内の画面遷移はinner controllerへ委譲し、Board / Thread等の詳細画面だけをRoot履歴へ積む。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun RootNavGraph(
    navController: NavHostController,
    topBarState: TopAppBarState,
    settingsViewModel: SettingsViewModel,
    openDrawer: () -> Unit,
    tabSessionStore: TabSessionStore,
    sharedTransitionScope: SharedTransitionScope,
    onMainShellBottomChromeHeightChanged: (Int) -> Unit = {},
    onBbsBottomChromeHeightChanged: (Int) -> Unit = {},
    onMoreClick: () -> Unit,
    onExitApp: () -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = AppRoute.MainShell(),
    ) {
        composable<AppRoute.MainShell>(
            enterTransition = {
                if (isBbsToContextualMainShellTransition(initialState, targetState)) {
                    bbsPageEnterTransition()
                } else {
                    defaultEnterTransition()
                }
            },
            exitTransition = {
                 if (isContextualMainShellToTabsSharedBbsTransition(initialState, targetState)) {
                    bbsPageExitTransition()
                } else {
                    defaultExitTransition()
                }
            },
            popEnterTransition = {
                if (isBbsToContextualMainShellTransition(initialState, targetState)) {
                    bbsPageEnterTransition()
                } else {
                    defaultPopEnterTransition()
                }
            },
            popExitTransition = {
                 if (isContextualMainShellToTabsSharedBbsTransition(initialState, targetState)) {
                    bbsPageExitTransition()
                } else {
                    defaultPopExitTransition()
                }
            },
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<AppRoute.MainShell>()
            val sourceRoute = if (route.mode == MainShellMode.ContextualTabs) {
                navController.previousBackStackEntry?.toBbsRouteOrNull()
            } else {
                null
            }
            MainShell(
                route = route,
                sourceRoute = sourceRoute,
                tabSessionStore = tabSessionStore,
                topBarState = topBarState,
                openDrawer = openDrawer,
                onMoreClick = onMoreClick,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = this@composable,
                onBoardSelected = { origin, mainShellNavController, tabsEntryId, boardRoute ->
                    navController.showBoardScreenFromMainShell(
                        sourceRoute = sourceRoute,
                        origin = origin,
                        mainShellEntryId = backStackEntry.id,
                        tabsEntryId = tabsEntryId,
                        mainShellNavController = mainShellNavController,
                        route = boardRoute,
                    )
                },
                onThreadSelected = { origin, mainShellNavController, tabsEntryId, threadRoute ->
                    navController.showThreadScreenFromMainShell(
                        sourceRoute = sourceRoute,
                        origin = origin,
                        mainShellEntryId = backStackEntry.id,
                        tabsEntryId = tabsEntryId,
                        mainShellNavController = mainShellNavController,
                        route = threadRoute,
                    )
                },
                onOpenBoard = { boardRoute ->
                    navController.navigateToBoardScreen(
                        boardRoute.copy(entryTransition = BbsEntryTransition.MainShellSlide),
                    )
                },
                onOpenThread = { threadRoute ->
                    navController.navigateToThreadScreen(
                        threadRoute.copy(entryTransition = BbsEntryTransition.MainShellSlide),
                    )
                },
                onMainShellBottomChromeHeightChanged = onMainShellBottomChromeHeightChanged,
            )
        }

        composable<AppRoute.Board>(
            enterTransition = {
                when {
                    isThreadToBoardTransition(initialState.destination.route, targetState.destination.route) ->
                        boardThreadPopEnterTransition()
                    isMainShellToTabsSharedBbsTransition(initialState, targetState) ->
                        bbsPageEnterTransition()
                    isBoardToThreadTransition(initialState.destination.route, targetState.destination.route) ->
                        boardThreadEnterTransition()
                    else -> defaultEnterTransition()
                }
            },
            exitTransition = {
                when {
                    isBbsToContextualMainShellTransition(initialState, targetState) ->
                        bbsPageExitTransition()
                    isBoardToThreadTransition(initialState.destination.route, targetState.destination.route) ->
                        boardThreadExitTransition()
                    else -> defaultExitTransition()
                }
            },
            popEnterTransition = {
                when {
                    isThreadToBoardTransition(initialState.destination.route, targetState.destination.route) ->
                        boardThreadPopEnterTransition()
                    isMainShellToTabsSharedBbsTransition(initialState, targetState) ->
                        bbsPageEnterTransition()
                    else -> defaultPopEnterTransition()
                }
            },
            popExitTransition = {
                when {
                    isBbsToContextualMainShellTransition(initialState, targetState) ->
                        bbsPageExitTransition()
                    isBoardToThreadTransition(initialState.destination.route, targetState.destination.route) ->
                        boardThreadPopExitTransition()
                    else -> defaultPopExitTransition()
                }
            },
        ) { backStackEntry ->
            val boardRoute = backStackEntry.toRoute<AppRoute.Board>()
            BoardScaffold(
                boardRoute = boardRoute,
                navController = navController,
                tabSessionStore = tabSessionStore,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = this@composable,
                onOpenTabList = {
                    navController.navigate(AppRoute.MainShell(MainShellMode.ContextualTabs))
                },
                onOpenBookmarkList = {
                    navController.navigate(
                        AppRoute.MainShell(
                            startDestination = MainShellStartDestination.BookmarkList,
                        ),
                    )
                },
                onOpenBoardList = {
                    navController.navigate(
                        AppRoute.MainShell(
                            startDestination = MainShellStartDestination.BbsServiceGroup,
                        ),
                    )
                },
                onBottomChromeHeightChanged = onBbsBottomChromeHeightChanged,
            )
        }

        composable<AppRoute.Thread>(
            enterTransition = {
                when {
                    initialState.destination.isRoute<AppRoute.ImageViewer>() -> null
                    isMainShellToTabsSharedBbsTransition(initialState, targetState) ->
                        bbsPageEnterTransition()
                    isBoardToThreadTransition(initialState.destination.route, targetState.destination.route) ->
                        boardThreadEnterTransition()
                    else -> defaultEnterTransition()
                }
            },
            exitTransition = {
                when {
                    targetState.destination.isRoute<AppRoute.ImageViewer>() -> null
                    isBbsToContextualMainShellTransition(initialState, targetState) ->
                        bbsPageExitTransition()
                    isThreadToBoardTransition(initialState.destination.route, targetState.destination.route) ->
                        boardThreadPopExitTransition()
                    isBoardToThreadTransition(initialState.destination.route, targetState.destination.route) ->
                        boardThreadExitTransition()
                    else -> defaultExitTransition()
                }
            },
            popEnterTransition = {
                when {
                    initialState.destination.isRoute<AppRoute.ImageViewer>() -> null
                    isMainShellToTabsSharedBbsTransition(initialState, targetState) ->
                        bbsPageEnterTransition()
                    isBoardToThreadTransition(initialState.destination.route, targetState.destination.route) ->
                        boardThreadPopEnterTransition()
                    else -> defaultPopEnterTransition()
                }
            },
            popExitTransition = {
                when {
                    targetState.destination.isRoute<AppRoute.ImageViewer>() -> null
                    isBbsToContextualMainShellTransition(initialState, targetState) ->
                        bbsPageExitTransition()
                    isThreadToBoardTransition(initialState.destination.route, targetState.destination.route) ->
                        boardThreadPopExitTransition()
                    else -> defaultPopExitTransition()
                }
            },
        ) { backStackEntry ->
            val threadRoute = backStackEntry.toRoute<AppRoute.Thread>()
            ThreadScaffold(
                threadRoute = threadRoute,
                navController = navController,
                tabSessionStore = tabSessionStore,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = this@composable,
                onOpenTabList = {
                    navController.navigate(AppRoute.MainShell(MainShellMode.ContextualTabs))
                },
                onOpenBookmarkList = {
                    navController.navigate(
                        AppRoute.MainShell(
                            startDestination = MainShellStartDestination.BookmarkList,
                        ),
                    )
                },
                onOpenBoardList = {
                    navController.navigate(
                        AppRoute.MainShell(
                            startDestination = MainShellStartDestination.BbsServiceGroup,
                        ),
                    )
                },
                onBottomChromeHeightChanged = onBbsBottomChromeHeightChanged,
            )
        }

        composable<AppRoute.HistoryList>(
            enterTransition = { defaultEnterTransition() },
            exitTransition = { defaultExitTransition() },
            popEnterTransition = { defaultPopEnterTransition() },
            popExitTransition = { defaultPopExitTransition() },
        ) {
            HistoryListScaffold(
                navController = navController,
                topBarState = topBarState,
                appChromePadding = PaddingValues(),
                tabSessionStore = tabSessionStore,
            )
        }

        addSettingsRoute(
            viewModel = settingsViewModel,
            navController = navController,
            onExitApp = onExitApp,
        )

        composable<AppRoute.About>(
            enterTransition = { defaultEnterTransition() },
            exitTransition = { defaultExitTransition() },
            popEnterTransition = { defaultPopEnterTransition() },
            popExitTransition = { defaultPopExitTransition() },
        ) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val aboutViewModel: AboutViewModel = hiltViewModel()
            AboutScreen(
                onNavigateUp = { navController.navigateUp() },
                onOpenSourceLicenseClick = { navController.navigate(AppRoute.OpenSourceLicense) },
                onShareLogClick = { aboutViewModel.shareLog(context) },
            )
        }

        composable<AppRoute.OpenSourceLicense>(
            enterTransition = { defaultEnterTransition() },
            exitTransition = { defaultExitTransition() },
            popEnterTransition = { defaultPopEnterTransition() },
            popExitTransition = { defaultPopExitTransition() },
        ) {
            OpenSourceLicenseScreen(onNavigateUp = { navController.navigateUp() })
        }

        composable<AppRoute.ImageViewer> { backStackEntry ->
            val route = backStackEntry.toRoute<AppRoute.ImageViewer>()
            val decodedUrls = route.imageUrls.map { url ->
                URLDecoder.decode(url, StandardCharsets.UTF_8.toString())
            }
            val initialIndex = if (decodedUrls.isNotEmpty()) {
                route.initialIndex.coerceIn(decodedUrls.indices)
            } else {
                // Guard: URLリストが空の場合は範囲外アクセスを避ける。
                0
            }
            ImageViewerScreen(
                imageUrls = decodedUrls,
                initialIndex = initialIndex,
                transitionNamespace = route.transitionNamespace,
                onNavigateUp = { navController.navigateUp() },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = this@composable,
            )
        }
    }
}

/** Root entryが具体的なroute型を表すかを判定する。 */
private inline fun <reified T : Any> androidx.navigation.NavDestination.isRoute(): Boolean =
    hasRoute<T>()

/** Board / Thread routeからTabs用contextual MainShellへShared Boundsで遷移する組み合わせを判定する。 */
private fun isBbsToContextualMainShellTransition(
    initialState: NavBackStackEntry,
    targetState: NavBackStackEntry,
): Boolean =
    initialState.isBbsRoute() &&
        initialState.bbsEntryTransition() != BbsEntryTransition.MainShellSlide &&
        targetState.isContextualMainShell()

/** contextual MainShellのTabsからBoard / ThreadへShared Boundsで遷移する組み合わせを判定する。 */
private fun isContextualMainShellToTabsSharedBbsTransition(
    initialState: NavBackStackEntry,
    targetState: NavBackStackEntry,
): Boolean =
    initialState.isContextualMainShell() &&
        targetState.bbsEntryTransition() == BbsEntryTransition.TabsSharedBounds

/** MainShellからShared Bounds対象のBoard / Threadへ遷移する組み合わせを判定する。 */
private fun isMainShellToTabsSharedBbsTransition(
    initialState: NavBackStackEntry,
    targetState: NavBackStackEntry,
): Boolean =
    initialState.isMainShell() && targetState.bbsEntryTransition() == BbsEntryTransition.TabsSharedBounds

/** BoardまたはThread routeかを判定する。 */
private fun NavBackStackEntry.isBbsRoute(): Boolean =
    destination.hasRoute<AppRoute.Board>() || destination.hasRoute<AppRoute.Thread>()

/** MainShell routeかを判定する。 */
private fun NavBackStackEntry.isMainShell(): Boolean =
    destination.hasRoute<AppRoute.MainShell>()

/** ContextualTabs用途のMainShell routeかを判定する。 */
private fun NavBackStackEntry.isContextualMainShell(): Boolean =
    destination.hasRoute<AppRoute.MainShell>() &&
        toRoute<AppRoute.MainShell>().mode == MainShellMode.ContextualTabs

/** Board / Thread routeから保存済みの入口transitionを取り出す。 */
private fun NavBackStackEntry.bbsEntryTransition(): BbsEntryTransition? = when {
    destination.hasRoute<AppRoute.Board>() -> toRoute<AppRoute.Board>().entryTransition
    destination.hasRoute<AppRoute.Thread>() -> toRoute<AppRoute.Thread>().entryTransition
    else -> null
}

/** Tabs直前のRoot entryがBoardまたはThreadなら、contextual Tabsのsource routeへ変換する。 */
private fun NavBackStackEntry.toBbsRouteOrNull(): AppRoute? = when {
    destination.hasRoute<AppRoute.Board>() -> toRoute<AppRoute.Board>()
    destination.hasRoute<AppRoute.Thread>() -> toRoute<AppRoute.Thread>()
    else -> null
}
