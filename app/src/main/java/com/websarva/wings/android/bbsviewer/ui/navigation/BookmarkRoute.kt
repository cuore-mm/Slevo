package com.websarva.wings.android.bbsviewer.ui.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.ui.bookmark.BookmarkScreen
import com.websarva.wings.android.bbsviewer.ui.bookmark.BookmarkViewModel
import com.websarva.wings.android.bbsviewer.ui.topbar.AppBarType
import com.websarva.wings.android.bbsviewer.ui.topbar.TopAppBarViewModel

fun NavGraphBuilder.addBookmarkRoute(
    topAppBarViewModel: TopAppBarViewModel,
    bookmarkViewModel: BookmarkViewModel,
    navController: NavHostController
) {
    composable<AppRoute.Bookmark> {
        val uiState by bookmarkViewModel.uiState.collectAsState()
        topAppBarViewModel.setTopAppBar(
            title = stringResource(R.string.bookmark),
            type = AppBarType.Home
        )
        BookmarkScreen(
            bookmarks = uiState.bookmarks ?: emptyList(),
            onItemClick = { bookmark ->
                navController.navigate(
                    AppRoute.Thread(
                        datUrl = bookmark.threadUrl,
                        boardName = bookmark.boardName,
                        boardUrl = "",
                        threadKey = ""
                    )
                ) {
                    launchSingleTop = true
                }
            }
        )
    }
}
