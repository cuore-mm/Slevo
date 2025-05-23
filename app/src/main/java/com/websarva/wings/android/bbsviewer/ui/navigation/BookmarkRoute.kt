package com.websarva.wings.android.bbsviewer.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.websarva.wings.android.bbsviewer.ui.bookmark.BookmarkScreen
import com.websarva.wings.android.bbsviewer.ui.bookmark.BookmarkViewModel
import com.websarva.wings.android.bbsviewer.ui.topbar.BbsServiceListTopBarScreen

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.addBookmarkRoute(
    modifier: Modifier = Modifier,
    bookmarkViewModel: BookmarkViewModel,
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior
) {
    composable<AppRoute.Bookmark>{
        val uiState by bookmarkViewModel.uiState.collectAsState()

        Scaffold (
            topBar = {
                BbsServiceListTopBarScreen(
                    scrollBehavior = scrollBehavior,
                    onNavigationClick = { },
                    onAddClick = { },
                    onSearchClick = { }
                )
            },
        ){ innerPadding ->

            BookmarkScreen(
                modifier = modifier.padding(innerPadding),
                scrollBehavior = scrollBehavior,
                boardGroups = uiState.boardList,
                onBoardClick = { board ->
                    navController.navigate(
                        AppRoute.Board(
                            boardId = board.boardId,
                            boardName = board.name,
                            boardUrl = board.url
                        )
                    ) {
                        launchSingleTop = true
                    }
                }
            )
        }

    }
}
