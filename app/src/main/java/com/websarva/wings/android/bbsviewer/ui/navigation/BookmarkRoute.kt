package com.websarva.wings.android.bbsviewer.ui.navigation

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.ui.bookmark.ThreadFetcherScreen
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadViewModel
import com.websarva.wings.android.bbsviewer.ui.topbar.AppBarType
import com.websarva.wings.android.bbsviewer.ui.topbar.TopAppBarViewModel

fun NavGraphBuilder.addBookmarkRoute(
    topAppBarViewModel: TopAppBarViewModel,
    threadViewModel: ThreadViewModel
) {
    composable<AppRoute.Bookmark> {
        topAppBarViewModel.setTopAppBar(
            title = stringResource(R.string.bookmark),
            type = AppBarType.Home
        )
        ThreadFetcherScreen(viewModel = threadViewModel)
    }
}
