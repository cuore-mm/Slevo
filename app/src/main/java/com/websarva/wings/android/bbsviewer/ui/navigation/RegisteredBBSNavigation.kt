package com.websarva.wings.android.bbsviewer.ui.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.websarva.wings.android.bbsviewer.ui.bbslist.service.AddBbsDialog
import com.websarva.wings.android.bbsviewer.ui.bbslist.service.BBSListScreen
import com.websarva.wings.android.bbsviewer.ui.bbslist.service.BbsServiceViewModel
import com.websarva.wings.android.bbsviewer.ui.bbslist.category.BbsCategoryListScreen
import com.websarva.wings.android.bbsviewer.ui.bbslist.board.CategorisedBoardListScreen
import com.websarva.wings.android.bbsviewer.ui.topbar.AppBarType
import com.websarva.wings.android.bbsviewer.ui.topbar.TopAppBarViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.websarva.wings.android.bbsviewer.ui.bbslist.board.BbsBoardViewModel
import com.websarva.wings.android.bbsviewer.ui.bbslist.category.BbsCategoryViewModel

fun NavGraphBuilder.addRegisteredBBSNavigation(
    navController: NavHostController,
    topAppBarViewModel: TopAppBarViewModel
) {
    navigation<AppRoute.RegisteredBBS>(
        startDestination = AppRoute.BBSList
    ) {
        //掲示板一覧
        composable<AppRoute.BBSList> {
            val viewModel: BbsServiceViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            topAppBarViewModel.setTopAppBar(AppBarType.BBSList)
                BBSListScreen(
                    uiState = uiState,
                    onClick = { service ->
                        navController.navigate(
                            AppRoute.BoardCategoryList(
                                serviceId = service.domain,
                                serviceName = service.name
                            )
                        ) {
                            launchSingleTop = true
                        }
                    },
                    onLongClick = { domain ->
                        viewModel.toggleSelect(domain)
                    },
                )

            if (uiState.addBBSDialog) {
                AddBbsDialog(
                    onDismissRequest = { viewModel.toggleAddBBSDialog(false) },
                    enteredUrl = uiState.enteredUrl,
                    onUrlChange = { viewModel.updateEnteredUrl(it) },
                    onCancel = { viewModel.toggleAddBBSDialog(false) },
                    onAdd = {
                        viewModel.addService(uiState.enteredUrl)
                        // ダイアログ閉じ＆入力クリア
                        viewModel.toggleAddBBSDialog(false)
                        viewModel.updateEnteredUrl("")
                    }
                )
            }
        }
        //カテゴリ一覧
        composable<AppRoute.BoardCategoryList> {
            val bcl: AppRoute.BoardCategoryList = it.toRoute()

            val viewModel: BbsCategoryViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            topAppBarViewModel.setTopAppBar(
                type = AppBarType.Small
            )
            LaunchedEffect(bcl.serviceId) {
                viewModel.loadCategoryInfo(bcl.serviceId)
            }
            BbsCategoryListScreen(
                uiState = uiState,
                onCategoryClick = { categoryName ->
                    navController.navigate(
                        AppRoute.CategorisedBoardList(
                            serviceId = bcl.serviceId,
                            categoryName = categoryName
                        )
                    ) {
                        launchSingleTop = true
                    }
                }
            )
        }
        //カテゴリ -> 板一覧
        composable<AppRoute.CategorisedBoardList> {
            val cbl: AppRoute.CategorisedBoardList = it.toRoute()
            val viewModel: BbsBoardViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            topAppBarViewModel.setTopAppBar(
                type = AppBarType.Small
            )
            viewModel.loadBoardInfo(
                serviceId = cbl.serviceId,
                categoryName = cbl.categoryName
            )
            CategorisedBoardListScreen(
                boards = uiState.boards,
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
