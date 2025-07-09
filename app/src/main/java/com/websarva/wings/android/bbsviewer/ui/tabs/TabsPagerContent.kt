package com.websarva.wings.android.bbsviewer.ui.tabs

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.R
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TabsPagerContent(
    modifier: Modifier = Modifier,
    tabsViewModel: TabsViewModel,
    navController: NavHostController,
    closeDrawer: () -> Unit
) {
    val openThreadTabs by tabsViewModel.openThreadTabs.collectAsState()
    val openBoardTabs by tabsViewModel.openBoardTabs.collectAsState()

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()

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
                    openTabs = openBoardTabs,
                    onCloseClick = { tabsViewModel.closeBoardTab(it) },
                    navController = navController,
                    closeDrawer = closeDrawer
                )
                else -> OpenThreadsList(
                    openTabs = openThreadTabs,
                    onCloseClick = { tabsViewModel.closeThreadTab(it) },
                    navController = navController,
                    closeDrawer = closeDrawer
                )
            }
        }
    }
}
