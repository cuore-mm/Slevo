package com.websarva.wings.android.slevo.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.websarva.wings.android.slevo.ui.bbslist.BbsListTopBarScreen
import com.websarva.wings.android.slevo.ui.bbslist.board.BbsBoardViewModel
import com.websarva.wings.android.slevo.ui.bbslist.board.CategorisedBoardListScreen
import com.websarva.wings.android.slevo.ui.bbslist.category.BoardCategoryListViewModel
import com.websarva.wings.android.slevo.ui.bbslist.category.BoaredCategoryListScreen
import com.websarva.wings.android.slevo.ui.bbslist.service.AddBbsDialog
import com.websarva.wings.android.slevo.ui.bbslist.service.DeleteBbsDialog
import com.websarva.wings.android.slevo.ui.bbslist.service.ServiceListScreen
import com.websarva.wings.android.slevo.ui.bbslist.service.ServiceListTopBarScreen
import com.websarva.wings.android.slevo.ui.bbslist.service.ServiceListViewModel
import com.websarva.wings.android.slevo.ui.common.SelectedTopBarScreen
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.util.isInRoute

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.addRegisteredBBSNavigation(
    parentPadding: PaddingValues,
    navController: NavHostController,
    openDrawer: () -> Unit,
    tabsViewModel: TabsViewModel,
) {
    navigation<AppRoute.BbsServiceGroup>(
        startDestination = AppRoute.ServiceList,
        enterTransition = {
            if (initialState.destination.isInRoute(
                    AppRoute.RouteName.TABS,
                    AppRoute.RouteName.BOOKMARK_LIST
                )
            ) {
                EnterTransition.None
            } else {
                defaultEnterTransition()
            }
        },
        exitTransition = {
            if (targetState.destination.isInRoute(
                    AppRoute.RouteName.TABS,
                    AppRoute.RouteName.BOOKMARK_LIST
                )
            ) {
                ExitTransition.None
            } else {
                defaultExitTransition()
            }
        },
        popEnterTransition = {
            if (initialState.destination.isInRoute(
                    AppRoute.RouteName.TABS,
                    AppRoute.RouteName.BOOKMARK_LIST
                )
            ) {
                EnterTransition.None
            } else {
                defaultPopEnterTransition()
            }
        },
        popExitTransition = {
            if (targetState.destination.isInRoute(
                    AppRoute.RouteName.TABS,
                    AppRoute.RouteName.BOOKMARK_LIST
                )
            ) {
                ExitTransition.None
            } else {
                defaultPopExitTransition()
            }
        }
    ) {
        //掲示板一覧
        composable<AppRoute.ServiceList>(
            exitTransition = {
                if (targetState.destination.isInRoute(
                        AppRoute.RouteName.BOARD_CATEGORY_LIST,
                        AppRoute.RouteName.BOARD_LIST_BY_CATEGORY
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
                        AppRoute.RouteName.BOARD_LIST_BY_CATEGORY
                    )
                ) {
                    defaultPopEnterTransition()
                } else {
                    null
                }
            }
        ) {
            val viewModel: ServiceListViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            Scaffold(
                topBar = {
                    Box {
                        // 通常モードの AppBar
                        AnimatedVisibility(
                            visible = !uiState.selectMode,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            ServiceListTopBarScreen(
                                onNavigationClick = openDrawer,
                                onAddClick = { viewModel.toggleAddDialog(true) },
                                onSearchClick = {}
                            )
                        }
                        // 編集モードの AppBar（上からスライドダウン）
                        AnimatedVisibility(
                            visible = uiState.selectMode,
                            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                        ) {
                            SelectedTopBarScreen(
                                onBack = { viewModel.toggleSelectMode(false) },
                                selectedCount = uiState.selected.size
                            )
                        }
                    }
                },
            ) { innerPadding ->

                ServiceListScreen(
                    modifier = Modifier.padding(
                        // 左右と下は親のpadding、上は子のpaddingを使用
                        start = parentPadding.calculateStartPadding(LayoutDirection.Ltr),
                        top = innerPadding.calculateTopPadding(),
                        end = parentPadding.calculateEndPadding(LayoutDirection.Ltr),
                        bottom = parentPadding.calculateBottomPadding()
                    ),
                    uiState = uiState,
                    onClick = { service ->
                        if (service.menuUrl != null) {
                            navController.navigate(
                                AppRoute.BoardCategoryList(
                                    serviceId = service.serviceId,
                                    serviceName = service.name
                                )
                            ) {
                                launchSingleTop = true
                            }
                        } else {
                            navController.navigate(
                                AppRoute.BoardListByCategory(
                                    serviceId = service.serviceId,
                                    categoryId = 0L,
                                    serviceName = service.name,
                                    categoryName = ""
                                )
                            ) {
                                launchSingleTop = true
                            }
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
        }
        //カテゴリ一覧
        composable<AppRoute.BoardCategoryList>(
            enterTransition = { defaultEnterTransition() },
            exitTransition = { defaultExitTransition() },
            popEnterTransition = { defaultPopEnterTransition() },
            popExitTransition = { defaultPopExitTransition() }
        ) {
            val viewModel: BoardCategoryListViewModel = hiltViewModel(it)
            val uiState by viewModel.uiState.collectAsState()

            Scaffold(
                topBar = {
                    BbsListTopBarScreen(
                        title = uiState.serviceName,
                        onNavigationClick = openDrawer,
                        onSearchClick = {}
                    )
                },
            ) { innerPadding ->
                BoaredCategoryListScreen(
                    modifier = Modifier.padding(
                        // 左右と下は親のpadding、上は子のpaddingを使用
                        start = parentPadding.calculateStartPadding(LayoutDirection.Ltr),
                        top = innerPadding.calculateTopPadding(),
                        end = parentPadding.calculateEndPadding(LayoutDirection.Ltr),
                        bottom = parentPadding.calculateBottomPadding()
                    ),
                    uiState = uiState,
                    onCategoryClick = { category ->
                        navController.navigate(
                            AppRoute.BoardListByCategory(
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
        }
        //カテゴリ -> 板一覧
        composable<AppRoute.BoardListByCategory>(
            enterTransition = { defaultEnterTransition() },
            exitTransition = { defaultExitTransition() },
            popEnterTransition = { defaultPopEnterTransition() },
            popExitTransition = { defaultPopExitTransition() }
        ) {
            val viewModel: BbsBoardViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            Scaffold(
                topBar = {
                    BbsListTopBarScreen(
                        title = if (uiState.categoryName.isNotBlank()) {
                            "${uiState.serviceName} > ${uiState.categoryName}"
                        } else {
                            uiState.serviceName
                        },
                        onNavigationClick = openDrawer,
                        onSearchClick = {},
                    )
                },
            ) { innerPadding ->
                CategorisedBoardListScreen(
                    modifier = Modifier.padding(
                        // 左右と下は親のpadding、上は子のpaddingを使用
                        start = parentPadding.calculateStartPadding(LayoutDirection.Ltr),
                        top = innerPadding.calculateTopPadding(),
                        end = parentPadding.calculateEndPadding(LayoutDirection.Ltr),
                        bottom = parentPadding.calculateBottomPadding()
                    ),
                    boards = uiState.boards,
                    onBoardClick = { board ->
                        val route = AppRoute.Board(
                            boardId = board.boardId,
                            boardName = board.name,
                            boardUrl = board.url
                        )
                        tabsViewModel.ensureBoardTab(route).let { index ->
                            if (index >= 0) {
                                tabsViewModel.setBoardCurrentPage(index)
                            }
                        }
                        navController.navigate(route) {
                            popUpTo(route) {
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
}
