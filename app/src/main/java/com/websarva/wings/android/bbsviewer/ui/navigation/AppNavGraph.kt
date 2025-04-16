package com.websarva.wings.android.bbsviewer.ui.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.websarva.wings.android.bbsviewer.ui.topbar.TopAppBarViewModel
import com.websarva.wings.android.bbsviewer.ui.bbslist.BBSListViewModel
import com.websarva.wings.android.bbsviewer.ui.bookmark.BookmarkViewModel
import kotlinx.serialization.Serializable

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    bbsListViewModel: BBSListViewModel,
    topAppBarViewModel: TopAppBarViewModel,
    bookmarkViewModel: BookmarkViewModel,
) {

    NavHost(
        navController = navController,
        startDestination = AppRoute.Bookmark,
        modifier = modifier,
        // 新しい画面が右からスライドイン＋フェードイン
        enterTransition = {
            slideInHorizontally(
                // 画面全体の幅分右から開始
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            ) + fadeIn(animationSpec = tween(durationMillis = 300))
        },
        // 古い画面が左へスライドアウト＋フェードアウト
        exitTransition = {
            slideOutHorizontally(
                // 画面全体の幅分左へ退場
                targetOffsetX = { fullWidth -> -fullWidth },
                animationSpec = tween(durationMillis = 300)
            ) + fadeOut(animationSpec = tween(durationMillis = 300))
        },
        // 戻る時のアニメーション（pop）も設定するなら
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth },
                animationSpec = tween(durationMillis = 300)
            ) + fadeIn(animationSpec = tween(durationMillis = 300))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            ) + fadeOut(animationSpec = tween(durationMillis = 300))
        }
    ) {
        //お気に入り
        addBookmarkRoute(
            topAppBarViewModel = topAppBarViewModel,
            bookmarkViewModel = bookmarkViewModel,
            navController = navController
        )
        //掲示板一覧
        addRegisteredBBSNavigation(
            navController = navController,
            topAppBarViewModel = topAppBarViewModel,
            bbsListViewModel = bbsListViewModel
        )
        //スレッド一覧
        addThreadListRoute(
            navController = navController,
            topAppBarViewModel = topAppBarViewModel
        )
        //スレッド画面
        addThreadRoute(
            topAppBarViewModel = topAppBarViewModel
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
    data class BoardCategoryList(val appBarTitle: String) : AppRoute()

    @Serializable
    data class CategorisedBoardList(val appBarTitle: String) : AppRoute()

    @Serializable
    data class ThreadList(val boardName: String, val boardUrl: String) : AppRoute()

    @Serializable
    data class Thread(
        val datUrl: String, val boardName: String, val boardUrl: String
    ) : AppRoute()

    data object RouteName {
        const val BOOKMARK = "Bookmark"
        const val REGISTERED_BBS = "RegisteredBBS"
        const val BBS_LIST = "BBSList"
        const val BOARD_CATEGORY_LIST = "BoardCategoryList"
        const val CATEGORISED_BOARD_LIST = "CategorisedBoardList"
        const val THREAD_LIST = "ThreadList"
        const val THREAD = "Thread"
    }
}
