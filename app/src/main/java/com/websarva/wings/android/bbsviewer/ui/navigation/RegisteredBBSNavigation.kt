package com.websarva.wings.android.bbsviewer.ui.navigation

import androidx.activity.compose.BackHandler
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
import com.websarva.wings.android.bbsviewer.ui.bbslist.service.DeleteBbsDialog
import com.websarva.wings.android.bbsviewer.ui.util.isInRoute

fun NavGraphBuilder.addRegisteredBBSNavigation(
    navController: NavHostController,
    topAppBarViewModel: TopAppBarViewModel,
) {
    navigation<AppRoute.RegisteredBBS>(
        startDestination = AppRoute.BBSList
    ) {
        //掲示板一覧
        composable<AppRoute.BBSList>(
            exitTransition = {
                if (targetState.destination.isInRoute(
                        AppRoute.RouteName.BOARD_CATEGORY_LIST,
                        AppRoute.RouteName.CATEGORISED_BOARD_LIST
                    )
                ) {
                    defaultExitTransition()
                } else {
                    null
                }
            },
            popEnterTransition = {
                if (initialState.destination.isInRoute(
                        AppRoute.RouteName.BOARD_CATEGORY_LIST,
                        AppRoute.RouteName.CATEGORISED_BOARD_LIST
                    )
                ) {
                    defaultPopEnterTransition()
                } else {
                    null
                }
            }
        ) {
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
                    viewModel.toggleSelectMode(true)
                    viewModel.toggleSelect(domain)
                },
            )

            if (uiState.showAddBBSDialog) {
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

            if (uiState.showDeleteBBSDialog) {
                DeleteBbsDialog(
                    onDismissRequest = { viewModel.toggleDeleteBBSDialog(false) },
                    onDelete = {
                        viewModel.removeService()
                        viewModel.toggleDeleteBBSDialog(false)
                    },
                    selectedCount = uiState.selected.size
                )
            }

            // selectMode が true の間は「戻る」をキャッチして解除だけ行う
            BackHandler(enabled = uiState.selectMode) {
                viewModel.toggleSelectMode(false)
                // 必要なら選択リストもクリア
            }
        }
        //カテゴリ一覧
        composable<AppRoute.BoardCategoryList>(
            enterTransition = { defaultEnterTransition() },
            exitTransition = { defaultExitTransition() },
            popEnterTransition = { defaultPopEnterTransition() },
            popExitTransition = { defaultPopExitTransition() }
        ) {
            val bcl: AppRoute.BoardCategoryList = it.toRoute()

            val viewModel: BbsCategoryViewModel = hiltViewModel(it)
            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(bcl.serviceId) {
                viewModel.loadCategoryInfo(bcl.serviceId)
            }
            BbsCategoryListScreen(
                uiState = uiState,
                onCategoryClick = { categoryName ->
                    navController.navigate(
                        AppRoute.CategorisedBoardList(
                            serviceId = bcl.serviceId,
                            serviceName = bcl.serviceName,
                            categoryName = categoryName
                        )
                    ) {
                        launchSingleTop = true
                    }
                }
            )
        }
        //カテゴリ -> 板一覧
        composable<AppRoute.CategorisedBoardList>(
            enterTransition = { defaultEnterTransition() },
            exitTransition = { defaultExitTransition() },
            popEnterTransition = { defaultPopEnterTransition() },
            popExitTransition = { defaultPopExitTransition() }
        ) {
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
