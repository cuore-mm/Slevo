package com.websarva.wings.android.bbsviewer.ui.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.websarva.wings.android.bbsviewer.ui.drawer.TabsViewModel
import com.websarva.wings.android.bbsviewer.ui.settings.SettingsViewModel
import kotlinx.serialization.Serializable

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    settingsViewModel: SettingsViewModel,
    openDrawer: () -> Unit,
    tabsViewModel: TabsViewModel,
) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = AppRoute.Bookmark,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        //お気に入り
        addBookmarkRoute(
            scrollBehavior = scrollBehavior,
            navController = navController,
            openDrawer = openDrawer
        )
        //掲示板一覧
        addRegisteredBBSNavigation(
            navController = navController,
            openDrawer = openDrawer
        )
        //スレッド一覧
        addBoardRoute(
            navController = navController,
            openDrawer = openDrawer
        )
        //スレッド画面
        addThreadRoute(
            navController = navController,
            tabsViewModel = tabsViewModel,
            openDrawer = openDrawer,
        )
        //タブ画面
        addTabsRoute(
            navController = navController,
            tabsViewModel = tabsViewModel
        )
        //設定画面
        addSettingsRoute(
            viewModel = settingsViewModel,
            navController = navController
        )
    }
}

@Serializable
sealed class AppRoute {
    @Serializable
    data object Bookmark : AppRoute()

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
    data class Board(val boardId: Long, val boardName: String, val boardUrl: String) : AppRoute()

    @Serializable
    data class Thread(
        val threadKey: String,     // 必須：スレッド識別子
        val boardUrl: String,      // 必須：板URL（datUrl導出、投稿情報のため）
        val boardName: String,     // 推奨：表示用
        val boardId: Long,         // 推奨：将来的な機能（板情報連携）のため
        val threadTitle: String    // 推奨：UX向上（即時タイトル表示）のため
    ) : AppRoute()

    @Serializable
    data object Settings : AppRoute()

    @Serializable
    data object Tabs : AppRoute()

    data object RouteName {
        const val BOOKMARK = "Bookmark"
        const val BBS_SERVICE_GROUP = "BbsServiceGroup"
        const val SERVICE_LIST = "ServiceList"
        const val BOARD_CATEGORY_LIST = "BoardCategoryList"
        const val BOARD_LIST_BY_CATEGORY = "BoardListByCategory"
        const val BOARD = "Board"
        const val THREAD = "Thread"
        const val SETTINGS = "Settings"
        const val TABS = "Tabs"
    }
}
