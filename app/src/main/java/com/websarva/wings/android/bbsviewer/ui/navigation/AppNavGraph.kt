package com.websarva.wings.android.bbsviewer.ui.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.websarva.wings.android.bbsviewer.ui.board.BoardScaffold
import com.websarva.wings.android.bbsviewer.ui.bookmarklist.BookmarkListScaffold
import com.websarva.wings.android.bbsviewer.ui.history.HistoryListScaffold
import com.websarva.wings.android.bbsviewer.ui.settings.SettingsViewModel
import com.websarva.wings.android.bbsviewer.ui.tabs.TabsScaffold
import com.websarva.wings.android.bbsviewer.ui.tabs.TabsViewModel
import com.websarva.wings.android.bbsviewer.ui.thread.screen.ThreadScaffold
import com.websarva.wings.android.bbsviewer.ui.viewer.ImageViewerScreen
import kotlinx.serialization.Serializable
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
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
        composable<AppRoute.BookmarkList> {
            BookmarkListScaffold(
                parentPadding = parentPadding,
                topBarState = topBarState,
                navController = navController,
                openDrawer = openDrawer
            )
        }
        //履歴一覧
        composable<AppRoute.HistoryList> {
            HistoryListScaffold(
                navController = navController,
                topBarState = topBarState,
                parentPadding = parentPadding
            )
        }
        //掲示板一覧
        addRegisteredBBSNavigation(
            parentPadding = parentPadding,
            navController = navController,
            openDrawer = openDrawer
        )
        //スレッド一覧
        composable<AppRoute.Board> { backStackEntry ->
            val boardRoute: AppRoute.Board = backStackEntry.toRoute()
            BoardScaffold(
                boardRoute = boardRoute,
                navController = navController,
                openDrawer = openDrawer,
                tabsViewModel = tabsViewModel,
                topBarState = topBarState
            )
        }
        //スレッド画面
        composable<AppRoute.Thread> { backStackEntry ->
            val threadRoute: AppRoute.Thread = backStackEntry.toRoute()
            ThreadScaffold(
                threadRoute = threadRoute,
                navController = navController,
                tabsViewModel = tabsViewModel,
                openDrawer = openDrawer,
                topBarState = topBarState
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
        //画像ビューア
        composable<AppRoute.ImageViewer> { backStackEntry ->
            val imageViewerRoute: AppRoute.ImageViewer = backStackEntry.toRoute()
            // URLデコード処理
            val decodedUrl = URLDecoder.decode(imageViewerRoute.imageUrl, StandardCharsets.UTF_8.toString())
            ImageViewerScreen(
                imageUrl = decodedUrl,
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
    data object Tabs : AppRoute()

    @Serializable
    data class ImageViewer(val imageUrl: String) : AppRoute()

    data object RouteName {
        const val BOOKMARK_LIST = "BookmarkList"
        const val BBS_SERVICE_GROUP = "BbsServiceGroup"
        const val SERVICE_LIST = "ServiceList"
        const val BOARD_CATEGORY_LIST = "BoardCategoryList"
        const val BOARD_LIST_BY_CATEGORY = "BoardListByCategory"
        const val BOARD = "Board"
        const val THREAD = "Thread"
        const val SETTINGS = "Settings"
        const val TABS = "Tabs"
        const val HISTORY_LIST = "HistoryList"
    }
}
