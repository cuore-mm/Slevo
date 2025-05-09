package com.websarva.wings.android.bbsviewer.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.websarva.wings.android.bbsviewer.ui.bbslist.service.AddBbsDialog
import com.websarva.wings.android.bbsviewer.ui.bbslist.service.BBSListScreen
import com.websarva.wings.android.bbsviewer.ui.bbslist.service.BbsServiceViewModel
import com.websarva.wings.android.bbsviewer.ui.bbslist.category.BbsCategoryListScreen
import com.websarva.wings.android.bbsviewer.ui.bbslist.board.CategorisedBoardListScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.websarva.wings.android.bbsviewer.ui.bbslist.board.BbsBoardViewModel
import com.websarva.wings.android.bbsviewer.ui.bbslist.category.BbsCategoryViewModel
import com.websarva.wings.android.bbsviewer.ui.bbslist.service.DeleteBbsDialog
import com.websarva.wings.android.bbsviewer.ui.util.isInRoute

fun NavGraphBuilder.addRegisteredBBSNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController,
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

            BBSListScreen(
                modifier = modifier,
                uiState = uiState,
                onClick = { service ->
                    navController.navigate(
                        AppRoute.BoardCategoryList(
                            serviceId = service.serviceId,
                            serviceName = service.name
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
                onLongClick = { serviceId ->
                    viewModel.toggleSelectMode(true)
                    viewModel.toggleSelect(serviceId)
                },
            )

            if (uiState.showAddDialog) {
                AddBbsDialog(
                    onDismissRequest = { viewModel.toggleAddDialog(false) },
                    enteredUrl = uiState.enteredUrl,
                    onUrlChange = { viewModel.updateEnteredUrl(it) },
                    onCancel = { viewModel.toggleAddDialog(false) },
                    onAdd = {
                        viewModel.addOrUpdateService(uiState.enteredUrl)
                        // ダイアログ閉じ＆入力クリア
                        viewModel.toggleAddDialog(false)
                        viewModel.updateEnteredUrl("")
                    }
                )
            }

            if (uiState.showDeleteDialog) {
                DeleteBbsDialog(
                    onDismissRequest = { viewModel.toggleDeleteDialog(false) },
                    onDelete = {
                        viewModel.removeSelectedServices()
                        viewModel.toggleDeleteDialog(false)
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
            val viewModel: BbsCategoryViewModel = hiltViewModel(it)
            val uiState by viewModel.uiState.collectAsState()

            BbsCategoryListScreen(
                modifier = modifier,
                uiState = uiState,
                onCategoryClick = { category ->
                    navController.navigate(
                        AppRoute.CategorisedBoardList(
                            serviceId = uiState.serviceId,
                            categoryId = category.categoryId,
                            serviceName = uiState.serviceName,
                            categoryName = category.name,
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
            val viewModel: BbsBoardViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            CategorisedBoardListScreen(
                modifier = modifier,
                boards = uiState.boards,
                onBoardClick = { board ->
                    navController.navigate(
                        AppRoute.Board(
                            boardId = board.boardId,
                            boardName = board.name,
                            boardUrl = board.url
                        )
                    ) {
                        popUpTo(
                            AppRoute.Board(
                                boardId = board.boardId,
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
