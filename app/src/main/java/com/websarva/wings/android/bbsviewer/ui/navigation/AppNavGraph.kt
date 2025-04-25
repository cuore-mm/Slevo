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
import com.websarva.wings.android.bbsviewer.ui.bookmark.BookmarkViewModel
import kotlinx.serialization.Serializable

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    topAppBarViewModel: TopAppBarViewModel,
    bookmarkViewModel: BookmarkViewModel,
) {
    val TIME= 300
    NavHost(
        navController = navController,
        startDestination = AppRoute.Bookmark,
        modifier = modifier,
        // 新しい画面が右からスライドイン＋フェードイン
        enterTransition = {
            slideInHorizontally(
                // 画面全体の幅分右から開始
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = TIME)
            ) + fadeIn(animationSpec = tween(durationMillis = TIME))
        },
        // 古い画面が左へスライドアウト＋フェードアウト
        exitTransition = {
            slideOutHorizontally(
                // 画面全体の幅分左へ退場
                targetOffsetX = { fullWidth -> -fullWidth },
                animationSpec = tween(durationMillis = TIME)
            ) + fadeOut(animationSpec = tween(durationMillis = TIME))
        },
        // 戻る時のアニメーション（pop）も設定するなら
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth },
                animationSpec = tween(durationMillis = TIME)
            ) + fadeIn(animationSpec = tween(durationMillis = TIME))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = TIME)
            ) + fadeOut(animationSpec = tween(durationMillis = TIME))
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
            topAppBarViewModel = topAppBarViewModel
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
    data class BoardCategoryList(val serviceId: String,val serviceName:String) : AppRoute()

    @Serializable
    data class CategorisedBoardList(val serviceId: String,val categoryName:String) : AppRoute()

    @Serializable
    data class ThreadList(val boardName: String, val boardUrl: String) : AppRoute()

    @Serializable
    data class Thread(
        val threadKey: String, val datUrl: String, val boardName: String, val boardUrl: String
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
