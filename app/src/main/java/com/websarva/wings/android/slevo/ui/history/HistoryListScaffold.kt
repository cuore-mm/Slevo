package com.websarva.wings.android.slevo.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.threadKey
import com.websarva.wings.android.slevo.ui.bottombar.BbsSelectBottomBar
import com.websarva.wings.android.slevo.ui.common.SlevoTopAppBar
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.navigateToThread
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryListScaffold(
    navController: NavHostController,
    topBarState: TopAppBarState,
    parentPadding: PaddingValues,
    tabsViewModel: TabsViewModel,
) {
    val viewModel: HistoryViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topBarState)

    val isSelectionMode = uiState.selectedThreadIds.isNotEmpty()

    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    Scaffold(
        topBar = {
            SlevoTopAppBar(
                title = stringResource(R.string.history),
                onNavigateUp = {
                    if (isSelectionMode) {
                        viewModel.clearSelection()
                    } else {
                        navController.popBackStack()
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            if (isSelectionMode) {
                BbsSelectBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    onDelete = { viewModel.deleteSelectedHistories() },
                    onOpen = {}
                )
            }
        },
    ) { innerPadding ->
        HistoryListScreen(
            modifier = Modifier.padding(innerPadding),
            histories = uiState.histories,
            selectedThreadIds = uiState.selectedThreadIds,
            isSelectionMode = isSelectionMode,
            onOpenThread = { history ->
                val route = AppRoute.Thread(
                    threadKey = history.history.threadId.threadKey,
                    boardUrl = history.history.boardUrl,
                    boardName = history.history.boardName,
                    boardId = history.history.boardId,
                    threadTitle = history.history.title,
                    resCount = history.history.resCount
                )
                navController.navigateToThread(
                    route = route,
                    tabsViewModel = tabsViewModel,
                )
            },
            onToggleSelection = { viewModel.toggleSelection(it) },
            onStartSelection = { viewModel.startSelection(it) }
        )
    }
}
