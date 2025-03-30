package com.websarva.wings.android.bbsviewer.ui

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.ui.appbar.HomeTopAppBarScreen
import com.websarva.wings.android.bbsviewer.ui.appbar.SmallTopAppBarScreen
import com.websarva.wings.android.bbsviewer.ui.appbar.TopAppBarViewModel
import com.websarva.wings.android.bbsviewer.ui.bbslist.BBSListScreen
import com.websarva.wings.android.bbsviewer.ui.bbslist.BBSListViewModel
import com.websarva.wings.android.bbsviewer.ui.bbslist.CategorisedBoardListScreen
import com.websarva.wings.android.bbsviewer.ui.bbslist.BoardCategoryList
import com.websarva.wings.android.bbsviewer.ui.bookmark.ThreadFetcherScreen
import com.websarva.wings.android.bbsviewer.ui.bookmark.ThreadViewModel
import com.websarva.wings.android.bbsviewer.ui.bottombar.BoardAppBar
import com.websarva.wings.android.bbsviewer.ui.bottombar.HomeBottomNavigationBar
import com.websarva.wings.android.bbsviewer.ui.threadlist.ThreadListScreen
import com.websarva.wings.android.bbsviewer.ui.threadlist.ThreadListViewModel
import kotlinx.serialization.Serializable

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    threadViewModel: ThreadViewModel,
    bbsListViewModel: BBSListViewModel,
    topAppBarViewModel: TopAppBarViewModel,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    // 画面遷移が発生するたびに呼ばれ、スクロール位置をリセットする
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(navBackStackEntry) {
        // 表示をリセット
        scrollBehavior.state.heightOffset = 0f
        scrollBehavior.state.contentOffset = 0f
    }
    val currentDestination = navBackStackEntry?.destination

    fun checkCurrentRoute(routeNames: List<String>): Boolean {
        return currentDestination?.hierarchy?.any { destination ->
            destination.route?.let { route ->
                routeNames.any { route.contains(it) }
            } ?: false
        } ?: false
    }


    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            val topAppBarUiState by topAppBarViewModel.uiState.collectAsState()
            when {
                checkCurrentRoute(
                    listOf(
                        AppRoute.RouteName.BOOKMARK,
                        AppRoute.RouteName.BBS_LIST,
                    )
                ) -> {
                    HomeTopAppBarScreen(
                        title = topAppBarUiState.title
                    )
                }

                checkCurrentRoute(
                    listOf(AppRoute.RouteName.THREAD_LIST)
                ) -> {
                    HomeTopAppBarScreen(
                        title = topAppBarUiState.title,
                        scrollBehavior = scrollBehavior
                    )
                }

                checkCurrentRoute(
                    listOf(
                        AppRoute.RouteName.BOARD_CATEGORY_LIST,
                        AppRoute.RouteName.CATEGORISED_BOARD_LIST,
                    )
                ) -> {
                    SmallTopAppBarScreen(
                        title = topAppBarUiState.title,
                        onNavigateUp = { navController.navigateUp() },
                        scrollBehavior = scrollBehavior
                    )
                }

                else -> {}
            }
        },
        bottomBar = {
            when {
                checkCurrentRoute(
                    listOf(
                        AppRoute.RouteName.BOOKMARK,
                        AppRoute.RouteName.REGISTERED_BBS
                    )
                ) -> {
                    HomeBottomNavigationBar(
                        navController = navController,
                        onClick = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                checkCurrentRoute(
                    listOf(
                        AppRoute.RouteName.THREAD_LIST
                    )
                ) -> {
                    BoardAppBar()
                }

                else -> {}
            }
        }
    ) { innerPadding ->
        NavigationSetUp(
            modifier = Modifier.padding(innerPadding),
            navController = navController,
            threadViewModel = threadViewModel,
            bbsListViewModel = bbsListViewModel,
            topAppBarViewModel = topAppBarViewModel,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun NavigationSetUp(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    threadViewModel: ThreadViewModel,
    bbsListViewModel: BBSListViewModel,
    topAppBarViewModel: TopAppBarViewModel,
) {
    NavHost(
        navController = navController,
        startDestination = AppRoute.Bookmark,
        modifier = modifier
    ) {
        //お気に入り
        composable<AppRoute.Bookmark> {
            topAppBarViewModel.setTopAppBar(
                title = stringResource(R.string.bookmark)
            )
            ThreadFetcherScreen(viewModel = threadViewModel)
        }
        //掲示板一覧
        navigation<AppRoute.RegisteredBBS>(
            startDestination = AppRoute.BBSList
        ) {
            //掲示板一覧
            composable<AppRoute.BBSList> {
                topAppBarViewModel.setTopAppBar(
                    title = stringResource(R.string.BBSList)
                )
                BBSListScreen(
                    onClick = {
                        bbsListViewModel.loadBBSMenu()
                        navController.navigate(AppRoute.BoardCategoryList(appBarTitle = it)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            //カテゴリ一覧
            composable<AppRoute.BoardCategoryList> {
                val boardCategoryList: AppRoute.BoardCategoryList = it.toRoute()
                topAppBarViewModel.setTopAppBar(
                    title = boardCategoryList.appBarTitle
                )
                val bbsListUiState by bbsListViewModel.uiState.collectAsState()
                BoardCategoryList(
                    categories = bbsListUiState.categories ?: emptyList(),
                    onCategoryClick = { category ->
                        bbsListViewModel.updateBoards(category)
                        navController.navigate(
                            AppRoute.CategorisedBoardList(
                                appBarTitle = topAppBarViewModel.addTitle(category.name)
                            )
                        ) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            //カテゴリ -> 板一覧
            composable<AppRoute.CategorisedBoardList> {
                val categorisedBoardList: AppRoute.CategorisedBoardList = it.toRoute()
                topAppBarViewModel.setTopAppBar(
                    title = categorisedBoardList.appBarTitle,
                )
                val bbsListUiState by bbsListViewModel.uiState.collectAsState()
                CategorisedBoardListScreen(
                    boards = bbsListUiState.boards ?: emptyList(),
                    onBoardClick = { board ->

                        navController.navigate(
                            AppRoute.ThreadList(
                                boardName = board.name,
                                boardUrl = board.url
                            )
                        ) {
                            popUpTo(
                                AppRoute.ThreadList(
                                    boardName = board.name,
                                    boardUrl = board.url
                                )
                            ) {
                                inclusive = false
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
        //スレッド一覧
        composable<AppRoute.ThreadList> {
            val viewModel: ThreadListViewModel = hiltViewModel()
            val threadListUiState by viewModel.uiState.collectAsState()
            val threadList: AppRoute.ThreadList = it.toRoute()
            topAppBarViewModel.setTopAppBar(
                title = threadList.boardName,
            )
            if (threadListUiState.threads == null) {
                viewModel.loadThreads(threadList.boardUrl)
            }
            val currentDestination = navController.currentBackStackEntry?.destination
            Log.d("ThreadListScreen", "ThreadListScreen: ${currentDestination?.route}")
            ThreadListScreen(
                threads = threadListUiState.threads ?: emptyList(),
                onClick = {},
                isRefreshing = threadListUiState.isLoading,
                onRefresh = { viewModel.loadThreads(threadList.boardUrl) }
            )
        }
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

    data object RouteName {
        const val BOOKMARK = "Bookmark"
        const val REGISTERED_BBS = "RegisteredBBS"
        const val BBS_LIST = "BBSList"
        const val BOARD_CATEGORY_LIST = "BoardCategoryList"
        const val CATEGORISED_BOARD_LIST = "CategorisedBoardList"
        const val THREAD_LIST = "ThreadList"
    }
}
