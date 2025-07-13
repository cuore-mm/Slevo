package com.websarva.wings.android.bbsviewer.ui.history

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.hilt.navigation.compose.hiltViewModel
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute
import com.websarva.wings.android.bbsviewer.ui.topbar.HomeTopAppBarScreen

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun HistoryListScaffold(
    navController: NavHostController,
    topBarState: TopAppBarState,
    parentPadding: PaddingValues,
) {
    val viewModel: HistoryViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topBarState)

    Scaffold(
        topBar = {
            HomeTopAppBarScreen(
                title = stringResource(R.string.history),
                scrollBehavior = scrollBehavior
            )
        },
        modifier = Modifier
    ) { innerPadding ->
        HistoryListScreen(
            modifier = Modifier.padding(
                start = parentPadding.calculateStartPadding(LayoutDirection.Ltr),
                top = innerPadding.calculateTopPadding(),
                end = parentPadding.calculateEndPadding(LayoutDirection.Ltr),
                bottom = parentPadding.calculateBottomPadding()
            ),
            histories = uiState.histories,
            onThreadClick = { history ->
                navController.navigate(
                    AppRoute.Thread(
                        threadKey = history.history.threadKey,
                        boardUrl = history.history.boardUrl,
                        boardName = history.history.boardName,
                        boardId = history.history.boardId,
                        threadTitle = history.history.title,
                        resCount = history.history.resCount
                    )
                ) { launchSingleTop = true }
            }
        )
    }
}
