package com.websarva.wings.android.slevo.ui.mainshell

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.websarva.wings.android.slevo.ui.bookmarklist.BookmarkListScaffold
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.MainShellStartDestination
import com.websarva.wings.android.slevo.ui.navigation.MainShellBbsOrigin
import com.websarva.wings.android.slevo.ui.navigation.addRegisteredBBSNavigation
import com.websarva.wings.android.slevo.ui.tabs.TabsScaffold
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.navigation.defaultEnterTransition
import com.websarva.wings.android.slevo.ui.navigation.defaultExitTransition
import com.websarva.wings.android.slevo.ui.navigation.defaultPopEnterTransition
import com.websarva.wings.android.slevo.ui.navigation.defaultPopExitTransition
import com.websarva.wings.android.slevo.ui.util.isInRoute

/**
 * MainShell内で表示するTabs、Bookmark、BBS一覧のdestinationを構成する。
 *
 * RootNavControllerへ遷移する操作はcallbackへ委譲し、inner controllerは一覧履歴だけを管理する。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MainShellNavGraph(
    appChromePadding: PaddingValues,
    navController: NavHostController,
    startDestination: MainShellStartDestination,
    sourceRoute: AppRoute?,
    openDrawer: () -> Unit,
    tabSessionStore: TabSessionStore,
    topBarState: TopAppBarState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    onBoardSelected: (MainShellBbsOrigin, String, AppRoute.Board) -> Unit,
    onThreadSelected: (MainShellBbsOrigin, String, AppRoute.Thread) -> Unit,
    onOpenBoard: (AppRoute.Board) -> Unit,
    onOpenThread: (AppRoute.Thread) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination.toRoute(),
    ) {
        composable<AppRoute.Tabs>(
            enterTransition = {
                if (initialState.destination.isInRoute(
                        AppRoute.RouteName.BOOKMARK_LIST,
                        AppRoute.RouteName.BBS_SERVICE_GROUP,
                    )
                ) EnterTransition.None else defaultEnterTransition()
            },
            exitTransition = {
                if (targetState.destination.isInRoute(
                        AppRoute.RouteName.BOOKMARK_LIST,
                        AppRoute.RouteName.BBS_SERVICE_GROUP,
                    )
                ) ExitTransition.None else defaultExitTransition()
            },
            popEnterTransition = {
                if (initialState.destination.isInRoute(
                        AppRoute.RouteName.BOOKMARK_LIST,
                        AppRoute.RouteName.BBS_SERVICE_GROUP,
                    )
                ) EnterTransition.None else defaultPopEnterTransition()
            },
            popExitTransition = {
                if (targetState.destination.isInRoute(
                        AppRoute.RouteName.BOOKMARK_LIST,
                        AppRoute.RouteName.BBS_SERVICE_GROUP,
                    )
                ) ExitTransition.None else defaultPopExitTransition()
            },
        ) { backStackEntry ->
            TabsScaffold(
                appChromePadding = appChromePadding,
                tabSessionStore = tabSessionStore,
                navController = navController,
                sourceRoute = sourceRoute,
                tabsEntryId = backStackEntry.id,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                 onBoardSelected = { route ->
                     onBoardSelected(MainShellBbsOrigin.Tabs, backStackEntry.id, route)
                 },
                 onThreadSelected = { route ->
                     onThreadSelected(MainShellBbsOrigin.Tabs, backStackEntry.id, route)
                 },
            )
        }

        composable<AppRoute.BookmarkList>(
            enterTransition = {
                if (initialState.destination.isInRoute(
                        AppRoute.RouteName.TABS,
                        AppRoute.RouteName.BBS_SERVICE_GROUP,
                    )
                ) EnterTransition.None else defaultEnterTransition()
            },
            exitTransition = {
                if (targetState.destination.isInRoute(
                        AppRoute.RouteName.TABS,
                        AppRoute.RouteName.BBS_SERVICE_GROUP,
                    )
                ) ExitTransition.None else defaultExitTransition()
            },
            popEnterTransition = {
                if (initialState.destination.isInRoute(
                        AppRoute.RouteName.TABS,
                        AppRoute.RouteName.BBS_SERVICE_GROUP,
                    )
                ) EnterTransition.None else defaultPopEnterTransition()
            },
            popExitTransition = {
                if (targetState.destination.isInRoute(
                        AppRoute.RouteName.TABS,
                        AppRoute.RouteName.BBS_SERVICE_GROUP,
                    )
                ) ExitTransition.None else defaultPopExitTransition()
            },
        ) {
            BookmarkListScaffold(
                appChromePadding = appChromePadding,
                navController = navController,
                topBarState = topBarState,
                openDrawer = openDrawer,
                tabSessionStore = tabSessionStore,
                onOpenBoard = onOpenBoard,
                onOpenThread = onOpenThread,
            )
        }

        addRegisteredBBSNavigation(
            appChromePadding = appChromePadding,
            navController = navController,
            openDrawer = openDrawer,
            tabSessionStore = tabSessionStore,
            onOpenBoard = onOpenBoard,
        )
    }
}

/** MainShellの初期表示指定をinner graphへ登録する具体的なrouteへ変換する。 */
private fun MainShellStartDestination.toRoute(): AppRoute = when (this) {
    MainShellStartDestination.Tabs -> AppRoute.Tabs
    MainShellStartDestination.BookmarkList -> AppRoute.BookmarkList
    MainShellStartDestination.BbsServiceGroup -> AppRoute.BbsServiceGroup
}
