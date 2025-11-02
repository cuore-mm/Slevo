package com.websarva.wings.android.slevo.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.websarva.wings.android.slevo.ui.about.AboutScreen
import com.websarva.wings.android.slevo.ui.about.OpenSourceLicenseScreen
import com.websarva.wings.android.slevo.ui.board.screen.BoardScaffold
import com.websarva.wings.android.slevo.ui.bookmarklist.BookmarkListScaffold
import com.websarva.wings.android.slevo.ui.history.HistoryListScaffold
import com.websarva.wings.android.slevo.ui.settings.SettingsViewModel
import com.websarva.wings.android.slevo.ui.tabs.TabsScaffold
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.thread.screen.ThreadScaffold
import com.websarva.wings.android.slevo.ui.util.isInRoute
import kotlinx.serialization.Serializable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavGraph(
    parentPadding: PaddingValues,
    navController: NavHostController,
    topBarState: TopAppBarState,
    settingsViewModel: SettingsViewModel,
    openDrawer: () -> Unit,
    tabsViewModel: TabsViewModel,
) {
    NavHost(
        navController = navController,
        startDestination = AppRoute.Tabs,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        //お気に入り一覧
        composable<AppRoute.BookmarkList>(
            enterTransition = {
                if (initialState.destination.isInRoute(
                        AppRoute.RouteName.TABS,
                        AppRoute.RouteName.BBS_SERVICE_GROUP
                    )
                ) {
                    EnterTransition.None
                } else {
                    defaultEnterTransition()
                }
            },
            exitTransition = {
                if (targetState.destination.isInRoute(
                        AppRoute.RouteName.TABS,
                        AppRoute.RouteName.BBS_SERVICE_GROUP
                    )
                ) {
                    ExitTransition.None
                } else {
                    defaultExitTransition()
                }
            },
            popEnterTransition = {
                if (initialState.destination.isInRoute(
                        AppRoute.RouteName.TABS,
                        AppRoute.RouteName.BBS_SERVICE_GROUP
                    )
                ) {
                    EnterTransition.None
                } else {
                    defaultPopEnterTransition()
                }
            },
            popExitTransition = {
                if (targetState.destination.isInRoute(
                        AppRoute.RouteName.TABS,
                        AppRoute.RouteName.BBS_SERVICE_GROUP
                    )
                ) {
                    ExitTransition.None
                } else {
                    defaultPopExitTransition()
                }
            }
        ) {
            BookmarkListScaffold(
                parentPadding = parentPadding,
                topBarState = topBarState,
                navController = navController,
                openDrawer = openDrawer,
                tabsViewModel = tabsViewModel
            )
        }
        //履歴一覧
        composable<AppRoute.HistoryList>(
            enterTransition = { defaultEnterTransition() },
            exitTransition = { defaultExitTransition() },
            popEnterTransition = { defaultPopEnterTransition() },
            popExitTransition = { defaultPopExitTransition() }
        ) {
            HistoryListScaffold(
                navController = navController,
                topBarState = topBarState,
                parentPadding = parentPadding,
                tabsViewModel = tabsViewModel
            )
        }
        //掲示板一覧
        addRegisteredBBSNavigation(
            parentPadding = parentPadding,
            navController = navController,
            openDrawer = openDrawer,
            tabsViewModel = tabsViewModel
        )
        //スレッド一覧
        composable<AppRoute.Board>(
            enterTransition = { defaultEnterTransition() },
            exitTransition = { defaultExitTransition() },
            popEnterTransition = { defaultPopEnterTransition() },
            popExitTransition = { defaultPopExitTransition() }
        ) { backStackEntry ->
            val boardRoute: AppRoute.Board = backStackEntry.toRoute()
            BoardScaffold(
                boardRoute = boardRoute,
                navController = navController,
                tabsViewModel = tabsViewModel,
            )
        }
        //スレッド画面
        composable<AppRoute.Thread>(
            enterTransition = { defaultEnterTransition() },
            exitTransition = { defaultExitTransition() },
            popEnterTransition = { defaultPopEnterTransition() },
            popExitTransition = { defaultPopExitTransition() }
        ) { backStackEntry ->
            val threadRoute: AppRoute.Thread = backStackEntry.toRoute()
            ThreadScaffold(
                threadRoute = threadRoute,
                navController = navController,
                tabsViewModel = tabsViewModel,
            )
        }
        //タブ画面
        composable<AppRoute.Tabs> {
            TabsScaffold(
                parentPadding = parentPadding,
                tabsViewModel = tabsViewModel,
                navController = navController
            )
        }
        //設定画面
        addSettingsRoute(
            viewModel = settingsViewModel,
            navController = navController
        )
        //このアプリについて
        composable<AppRoute.About>(
            enterTransition = { defaultEnterTransition() },
            exitTransition = { defaultExitTransition() },
            popEnterTransition = { defaultPopEnterTransition() },
            popExitTransition = { defaultPopExitTransition() }
        ) {
            AboutScreen(
                onNavigateUp = { navController.navigateUp() },
                onOpenSourceLicenseClick = { navController.navigate(AppRoute.OpenSourceLicense) }
            )
        }
        //オープンソースライセンス
        composable<AppRoute.OpenSourceLicense>(
            enterTransition = { defaultEnterTransition() },
            exitTransition = { defaultExitTransition() },
            popEnterTransition = { defaultPopEnterTransition() },
            popExitTransition = { defaultPopExitTransition() }
        ) {
            OpenSourceLicenseScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }
    }
}

@Serializable
sealed class AppRoute {
    @Serializable
    data object BookmarkList : AppRoute()

    @Serializable
    data object HistoryList : AppRoute()

    @Serializable
    data object BbsServiceGroup : AppRoute()

    @Serializable
    data object ServiceList : AppRoute()

    @Serializable
    data class BoardCategoryList(val serviceId: Long, val serviceName: String) : AppRoute()

    @Serializable
    data class BoardListByCategory(
        val serviceId: Long,
        val categoryId: Long,
        val serviceName: String,
        val categoryName: String
    ) : AppRoute()

    @Serializable
    data class Board(
        val boardId: Long? = null, // 任意：未登録の場合は画面側で解決
        val boardName: String,
        val boardUrl: String
    ) : AppRoute()

    @Serializable
    data class Thread(
        val threadKey: String,     // 必須：スレッド識別子
        val boardUrl: String,      // 必須：板URL（datUrl導出、投稿情報のため）
        val boardName: String,     // 推奨：表示用
        val boardId: Long? = null, // 任意：未登録の場合は画面側で解決
        val threadTitle: String,   // 推奨：UX向上（即時タイトル表示）のため
        val resCount: Int = 0      // 表示用: レス数
    ) : AppRoute()

    @Serializable
    data object Settings : AppRoute()

    @Serializable
    data object SettingsHome : AppRoute()

    @Serializable
    data object SettingsGeneral : AppRoute()

    @Serializable
    data object SettingsNg : AppRoute()

    @Serializable
    data object SettingsThread : AppRoute()

    @Serializable
    data object SettingsCookie : AppRoute()

    @Serializable
    data object SettingsGesture : AppRoute()

    @Serializable
    data object Tabs : AppRoute()

    @Serializable
    data object About : AppRoute()

    @Serializable
    data object OpenSourceLicense : AppRoute()

    data object RouteName {
        const val BOOKMARK_LIST = "BookmarkList"
        const val BBS_SERVICE_GROUP = "BbsServiceGroup"
        const val SERVICE_LIST = "ServiceList"
        const val BOARD_CATEGORY_LIST = "BoardCategoryList"
        const val BOARD_LIST_BY_CATEGORY = "BoardListByCategory"
        const val BOARD = "Board"
        const val THREAD = "Thread"
        const val SETTINGS = "Settings"
        const val SETTINGS_HOME = "SettingsHome"
        const val SETTINGS_GENERAL = "SettingsGeneral"
        const val SETTINGS_NG = "SettingsNg"
        const val SETTINGS_THREAD = "SettingsThread"
        const val SETTINGS_COOKIE = "SettingsCookie"
        const val SETTINGS_GESTURE = "SettingsGesture"
        const val TABS = "Tabs"
        const val HISTORY_LIST = "HistoryList"
        const val ABOUT = "About"
        const val OPEN_SOURCE_LICENSE = "OpenSourceLicense"
    }
}
