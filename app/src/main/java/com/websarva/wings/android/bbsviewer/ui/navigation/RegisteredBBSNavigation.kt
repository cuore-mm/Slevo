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
import androidx.core.net.toUri
import com.websarva.wings.android.bbsviewer.ui.bbslist.board.BbsBoardViewModel
import com.websarva.wings.android.bbsviewer.ui.bbslist.category.BbsCategoryViewModel

fun NavGraphBuilder.addRegisteredBBSNavigation(
    navController: NavHostController,
    topAppBarViewModel: TopAppBarViewModel,
    bbsServiceViewModel: BbsServiceViewModel,
    bbsCategoryViewModel: BbsCategoryViewModel,
    bbsBoardViewModel: BbsBoardViewModel
) {
    navigation<AppRoute.RegisteredBBS>(
        startDestination = AppRoute.BBSList
    ) {
        //掲示板一覧
        composable<AppRoute.BBSList> {
            val uiState by bbsServiceViewModel.uiState.collectAsState()
            topAppBarViewModel.setTopAppBar(
                type = AppBarType.BBSList
            )
                BBSListScreen(
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
                    onRemove = {},
                    onMove = { from, to -> },
                )

            if (uiState.addBBSDialog) {
                AddBbsDialog(
                    onDismissRequest = { bbsServiceViewModel.toggleAddBBSDialog(false) },
                    enteredUrl = uiState.enteredUrl,
                    onUrlChange = { bbsServiceViewModel.updateEnteredUrl(it) },
                    onCancel = { bbsServiceViewModel.toggleAddBBSDialog(false) },
                    onAdd = {
                        val url = uiState.enteredUrl.trim()
                        val uri = url.toUri()
                        val host = uri.host ?: return@AddBbsDialog  // 例: "menu.5ch.net"
                        // ドメイン部分だけ（例 "5ch.net"）にしたければ…
                        val parts = host.split('.')
                        val display =
                            if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host

                        bbsServiceViewModel.addService(
                            serviceId = host,
                            displayName = display,
                            menuUrl = url,
                            boardUrl = null
                        )
                        // ダイアログ閉じ＆入力クリア
                        bbsServiceViewModel.toggleAddBBSDialog(false)
                        bbsServiceViewModel.updateEnteredUrl("")
                    }
                )
            }
        }
        //カテゴリ一覧
        composable<AppRoute.BoardCategoryList> {

            val bcl: AppRoute.BoardCategoryList = it.toRoute()
            topAppBarViewModel.setTopAppBar(
                type = AppBarType.Small
            )
            val uiState by bbsCategoryViewModel.uiState.collectAsState()
            LaunchedEffect(bcl.serviceId) {
                bbsCategoryViewModel.loadCategoryInfo(bcl.serviceId)
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
            val uiState by bbsBoardViewModel.uiState.collectAsState()

            topAppBarViewModel.setTopAppBar(
                type = AppBarType.Small
            )
            bbsBoardViewModel.loadBoardInfo(
                serviceId = cbl.serviceId,
                categoryName = cbl.categoryName
            )
            CategorisedBoardListScreen(
                boards = uiState.boards,
//                onBoardClick = { board ->
//                    navController.navigate(
//                        AppRoute.ThreadList(
//                            boardName = board.name,
//                            boardUrl = board.url
//                        )
//                    ) {
//                        popUpTo(
//                            AppRoute.ThreadList(
//                                boardName = board.name,
//                                boardUrl = board.url
//                            )
//                        ) {
//                            inclusive = false
//                            saveState = true
//                        }
//                        launchSingleTop = true
//                        restoreState = true
//                    }
//                }
            )
        }
    }
}
