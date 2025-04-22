package com.websarva.wings.android.bbsviewer.ui.topbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.ui.bbslist.service.BbsServiceViewModel
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderTopBar(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    topAppBarViewModel: TopAppBarViewModel,
    navBackStackEntry: NavBackStackEntry?,
    bbsServiceViewModel: BbsServiceViewModel
) {
    val topAppBarUiState by topAppBarViewModel.uiState.collectAsState()

    when (topAppBarUiState.type) {
        AppBarType.Home -> HomeTopAppBarScreen(title = topAppBarUiState.title)
        AppBarType.HomeWithScroll -> HomeTopAppBarScreen(
            title = topAppBarUiState.title,
            scrollBehavior = scrollBehavior
        )

        AppBarType.Small -> SmallTopAppBarScreen(
            title = topAppBarUiState.title,
            onNavigateUp = { navController.navigateUp() },
            scrollBehavior = scrollBehavior
        )

        AppBarType.BBSList -> {
            val uiState by bbsServiceViewModel.uiState.collectAsState()
            Box {
                // 通常モードの AppBar
                AnimatedVisibility(
                    visible = !uiState.editMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    BBSListTopBarScreen(
                        onNavigationClick = {},
                        onEditClick       = { bbsServiceViewModel.toggleEditMode(true) },
                        onSearchClick     = {}
                    )
                }
                // 編集モードの AppBar（上からスライドダウン）
                AnimatedVisibility(
                    visible = uiState.editMode,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    EditableBBSListTopBarScreen(
                        onBack     = { bbsServiceViewModel.toggleEditMode(false) },
                        onAddBoard = { bbsServiceViewModel.toggleAddBBSDialog(true) }
                    )
                }
            }
        }

        AppBarType.Thread -> {
            val threadViewModel: ThreadViewModel? =
                navBackStackEntry?.let { hiltViewModel<ThreadViewModel>(it) }
            // threadViewModel が null でない場合にだけ処理を実施
            threadViewModel?.let { viewModel ->
                val uiState by viewModel.uiState.collectAsState()
                ThreadTopBar(
                    scrollBehavior = scrollBehavior,
                    onFavoriteClick = { viewModel.bookmarkThread() },
                    uiState = uiState
                )
            }
        }

        else -> {}
    }
}
