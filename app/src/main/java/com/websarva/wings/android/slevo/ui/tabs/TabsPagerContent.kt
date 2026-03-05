package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

/**
 * タブ一覧のページャーを提供し、板/スレ一覧を切り替えて表示する。
 */
@Composable
fun TabsPagerContent(
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    tabsViewModel: TabsViewModel,
    navController: NavHostController,
    closeDrawer: () -> Unit,
    listContentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val uiState by tabsViewModel.uiState.collectAsState()

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
    ) { page ->
        when (page) {
            0 -> OpenBoardsList(
                openTabs = uiState.openBoardTabs,
                onCloseClick = { tabsViewModel.closeBoardTab(it) },
                navController = navController,
                closeDrawer = closeDrawer,
                contentPadding = listContentPadding,
                tabsViewModel = tabsViewModel,
            )

            else -> OpenThreadsList(
                openTabs = uiState.openThreadTabs,
                onCloseClick = { tabsViewModel.closeThreadTab(it) },
                navController = navController,
                closeDrawer = closeDrawer,
                contentPadding = listContentPadding,
                newResCounts = uiState.newResCounts,
                onItemClick = { tabsViewModel.clearNewResCount(it.id) },
                tabsViewModel = tabsViewModel,
            )
        }
    }
}
