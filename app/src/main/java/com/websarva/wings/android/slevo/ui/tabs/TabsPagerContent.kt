package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
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
    openBoardTabs: List<BoardTabInfo>,
    openThreadTabs: List<ThreadTabInfo>,
    newResCounts: Map<String, Int>,
    selectedBoardTab: BoardTabInfo?,
    selectedThreadTab: ThreadTabInfo?,
    onCloseBoardTab: (BoardTabInfo) -> Unit,
    onCloseThreadTab: (ThreadTabInfo) -> Unit,
    onBoardTabLongPressed: (BoardTabInfo, androidx.compose.ui.unit.IntRect) -> Unit,
    onThreadTabLongPressed: (ThreadTabInfo, androidx.compose.ui.unit.IntRect) -> Unit,
    onClearNewResCount: (com.websarva.wings.android.slevo.data.model.ThreadId) -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
    ) { page ->
        when (page) {
            0 -> OpenBoardsList(
                openTabs = openBoardTabs,
                onCloseClick = onCloseBoardTab,
                navController = navController,
                closeDrawer = closeDrawer,
                contentPadding = listContentPadding,
                selectedBoardTab = selectedBoardTab,
                onBoardTabLongPressed = onBoardTabLongPressed,
                tabsViewModel = tabsViewModel,
            )

            else -> OpenThreadsList(
                openTabs = openThreadTabs,
                onCloseClick = onCloseThreadTab,
                navController = navController,
                closeDrawer = closeDrawer,
                contentPadding = listContentPadding,
                newResCounts = newResCounts,
                selectedThreadTab = selectedThreadTab,
                onThreadTabLongPressed = onThreadTabLongPressed,
                onClearNewResCount = onClearNewResCount,
                tabsViewModel = tabsViewModel,
            )
        }
    }
}