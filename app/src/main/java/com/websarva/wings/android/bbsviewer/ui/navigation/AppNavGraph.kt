package com.websarva.wings.android.bbsviewer.ui.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.websarva.wings.android.bbsviewer.ui.topbar.TopAppBarViewModel
import com.websarva.wings.android.bbsviewer.ui.bookmark.BookmarkViewModel
import com.websarva.wings.android.bbsviewer.ui.settings.SettingsViewModel
import kotlinx.serialization.Serializable

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    topAppBarViewModel: TopAppBarViewModel,
    bookmarkViewModel: BookmarkViewModel,
    settingsViewModel: SettingsViewModel
) {
    NavHost(
        navController = navController,
        startDestination = AppRoute.Bookmark,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        //お気に入り
        addBookmarkRoute(
            modifier = modifier,
            topAppBarViewModel = topAppBarViewModel,
            bookmarkViewModel = bookmarkViewModel,
            navController = navController
        )
        //掲示板一覧
        addRegisteredBBSNavigation(
            modifier = modifier,
            navController = navController,
        )
        //スレッド一覧
        addBoardRoute(
            navController = navController,
        )
        //スレッド画面
        addThreadRoute(
            topAppBarViewModel = topAppBarViewModel
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
    data object RegisteredBBS : AppRoute()

    @Serializable
    data object BBSList : AppRoute()

    @Serializable
    data class BoardCategoryList(val serviceId: Long, val serviceName: String) : AppRoute()

    @Serializable
    data class CategorisedBoardList(
        val serviceId: Long,
        val categoryId: Long,
        val serviceName: String,
        val categoryName: String
    ) : AppRoute()

    @Serializable
    data class Board(val boardId: Long, val boardName: String, val boardUrl: String) : AppRoute()

    @Serializable
    data class Thread(
        val threadKey: String, val datUrl: String, val boardName: String, val boardUrl: String
    ) : AppRoute()

    @Serializable
    data object Settings : AppRoute()

    data object RouteName {
        const val BOOKMARK = "Bookmark"
        const val REGISTERED_BBS = "RegisteredBBS"
        const val BBS_LIST = "BBSList"
        const val BOARD_CATEGORY_LIST = "BoardCategoryList"
        const val CATEGORISED_BOARD_LIST = "CategorisedBoardList"
        const val BOARD = "Board"
        const val THREAD = "Thread"
        const val SETTINGS = "Settings"
    }
}
