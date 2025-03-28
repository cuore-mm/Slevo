package com.websarva.wings.android.bbsviewer.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.ui.appbar.TopAppBarScreen
import com.websarva.wings.android.bbsviewer.ui.appbar.TopAppBarViewModel
import com.websarva.wings.android.bbsviewer.ui.bbslist.BBSListScreen
import com.websarva.wings.android.bbsviewer.ui.bbslist.BBSListViewModel
import com.websarva.wings.android.bbsviewer.ui.bbslist.CategorisedBoardListScreen
import com.websarva.wings.android.bbsviewer.ui.bbslist.BoardCategoryList
import com.websarva.wings.android.bbsviewer.ui.bookmark.ThreadFetcherScreen
import com.websarva.wings.android.bbsviewer.ui.bookmark.ThreadViewModel
import kotlinx.serialization.Serializable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    threadViewModel: ThreadViewModel,
    bbsListViewModel: BBSListViewModel,
    topAppBarViewModel: TopAppBarViewModel
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    // 画面遷移が発生するたびに呼ばれ、スクロール位置をリセットする
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(navBackStackEntry) {
        // 表示をリセット
        scrollBehavior.state.heightOffset = 0f
        scrollBehavior.state.contentOffset = 0f
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBarScreen(
                viewModel = topAppBarViewModel,
                navController = navController,
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
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
    ) { innerPadding ->
        NavigationSetUp(
            modifier = Modifier.padding(innerPadding),
            navController = navController,
            threadViewModel = threadViewModel,
            bbsListViewModel = bbsListViewModel,
            topAppBarViewModel = topAppBarViewModel
        )
    }
}

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
                    isCenter = false,
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
                    isCenter = false,
                    title = categorisedBoardList.appBarTitle,
                )
                val bbsListUiState by bbsListViewModel.uiState.collectAsState()
                CategorisedBoardListScreen(
                    boards = bbsListUiState.boards ?: emptyList(),
                    onBoardClick = {}
                )
            }
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
}

@Composable
fun HomeBottomNavigationBar(
    navController: NavHostController,
    onClick: (AppRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val topLevelRoutes = listOf(
        TopLevelRoute(
            route = AppRoute.Bookmark,
            name = stringResource(R.string.bookmark),
            icon = Icons.Default.Favorite,
            parentRoute = AppRoute.Bookmark
        ),
        TopLevelRoute(
            route = AppRoute.BBSList,
            name = stringResource(R.string.boardList),
            icon = Icons.AutoMirrored.Filled.List,
            parentRoute = AppRoute.RegisteredBBS
        )
    )
    NavigationBar(modifier = modifier) {
        topLevelRoutes.forEach { topLevelRoute ->
            NavigationBarItem(
                icon = { Icon(topLevelRoute.icon, contentDescription = topLevelRoute.name) },
                label = { Text(topLevelRoute.name) },
                selected = currentDestination
                    ?.hierarchy
                    ?.any { it.route == topLevelRoute.parentRoute::class.qualifiedName } == true,
                onClick = { onClick(topLevelRoute.route) }
            )
        }
    }
}

private data class TopLevelRoute(
    val route: AppRoute,
    val name: String,
    val icon: ImageVector,
    val parentRoute: AppRoute
)
