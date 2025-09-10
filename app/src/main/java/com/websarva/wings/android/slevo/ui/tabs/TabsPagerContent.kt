package com.websarva.wings.android.slevo.ui.tabs

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TabsPagerContent(
    modifier: Modifier = Modifier,
    tabListViewModel: TabListViewModel,
    navController: NavHostController,
    closeDrawer: () -> Unit,
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {}
) {
    val uiState by tabListViewModel.uiState.collectAsState()

    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 2 })
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onPageChanged(it) }
    }

    Column(modifier = modifier) {
        TabRow(selectedTabIndex = pagerState.currentPage) {
            listOf(
                stringResource(R.string.board),
                stringResource(R.string.thread)
            ).forEachIndexed { index, text ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(text) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> OpenBoardsList(
                    openTabs = uiState.openBoardTabs,
                    onCloseClick = { tabListViewModel.closeBoardTab(it) },
                    navController = navController,
                    closeDrawer = closeDrawer
                )
                else -> OpenThreadsList(
                    openTabs = uiState.openThreadTabs,
                    onCloseClick = { tabListViewModel.closeThreadTab(it) },
                    navController = navController,
                    closeDrawer = closeDrawer,
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { tabListViewModel.refreshOpenThreads() },
                    newResCounts = uiState.newResCounts,
                    onItemClick = { tabListViewModel.clearNewResCount(it.key, it.boardUrl) }
                )
            }
        }
    }
}
