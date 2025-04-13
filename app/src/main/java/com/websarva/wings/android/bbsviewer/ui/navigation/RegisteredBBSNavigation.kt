package com.websarva.wings.android.bbsviewer.ui.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.ui.bbslist.BBSListScreen
import com.websarva.wings.android.bbsviewer.ui.bbslist.BBSListViewModel
import com.websarva.wings.android.bbsviewer.ui.bbslist.BoardCategoryList
import com.websarva.wings.android.bbsviewer.ui.bbslist.CategorisedBoardListScreen
import com.websarva.wings.android.bbsviewer.ui.topbar.AppBarType
import com.websarva.wings.android.bbsviewer.ui.topbar.TopAppBarViewModel

fun NavGraphBuilder.addRegisteredBBSNavigation(
    navController: NavHostController,
    topAppBarViewModel: TopAppBarViewModel,
    bbsListViewModel: BBSListViewModel
) {
    navigation<AppRoute.RegisteredBBS>(
        startDestination = AppRoute.BBSList
    ) {
        //掲示板一覧
        composable<AppRoute.BBSList> {
            topAppBarViewModel.setTopAppBar(
                title = stringResource(R.string.BBSList),
                type = AppBarType.Home
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
                title = boardCategoryList.appBarTitle,
                type = AppBarType.Small
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
                type = AppBarType.Small
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
}
