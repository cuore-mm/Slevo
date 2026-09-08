package com.websarva.wings.android.slevo.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.threadKey
import com.websarva.wings.android.slevo.ui.bottombar.BbsSelectBottomBar
import com.websarva.wings.android.slevo.ui.common.SlevoTopAppBar
import com.websarva.wings.android.slevo.ui.common.mergeScaffoldPaddingValues
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.navigateToThreadScreen
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import kotlinx.coroutines.launch

/**
 * 履歴一覧と選択モード用 BottomBar を構成する。
 *
 * 画面 Scaffold と選択用 chrome の占有領域は、履歴 LazyColumn の content padding に集約する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryListScaffold(
    navController: NavHostController,
    topBarState: TopAppBarState,
    appChromePadding: PaddingValues,
    tabSessionStore: TabSessionStore,
) {
    val viewModel: HistoryViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

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
                    modifier = Modifier,
                    onDelete = { viewModel.deleteSelectedHistories() },
                    onOpen = {}
                )
            }
        },
    ) { innerPadding ->
        val contentPadding = mergeScaffoldPaddingValues(innerPadding, appChromePadding)
        HistoryListScreen(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            histories = uiState.histories,
            selectedThreadIds = uiState.selectedThreadIds,
            isSelectionMode = isSelectionMode,
            onOpenThread = { history ->
                coroutineScope.launch {
                    val route = tabSessionStore.normalizeThreadRouteForNavigation(
                        AppRoute.Thread(
                            threadKey = history.history.threadId.threadKey,
                            boardUrl = history.history.boardUrl,
                            boardName = history.history.boardName,
                            boardId = history.history.boardId,
                            threadTitle = history.history.title,
                            resCount = history.history.resCount
                        )
                    )
                    val index = tabSessionStore.registerAndSelectThreadRoute(route)
                    if (index >= 0) navController.navigateToThreadScreen(route)
                }
            },
            onToggleSelection = { viewModel.toggleSelection(it) },
            onStartSelection = { viewModel.startSelection(it) }
        )
    }
}
