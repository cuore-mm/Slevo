package com.websarva.wings.android.slevo.ui.tabs.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo

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
    pendingCloseBoardTab: BoardTabInfo?,
    pendingCloseThreadTab: ThreadTabInfo?,
    onCloseRequestConsumed: () -> Unit,
    onCloseBoardTab: (BoardTabInfo) -> Unit,
    onCloseThreadTab: (ThreadTabInfo) -> Unit,
    onBoardTabLongPressed: (BoardTabInfo, IntRect) -> Unit,
    onThreadTabLongPressed: (ThreadTabInfo, IntRect) -> Unit,
    onClearNewResCount: (ThreadId) -> Unit,
    isInLongPressSelectionMode: Boolean = false,
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false,
    ) { page ->
        when (page) {
            0 -> OpenBoardsList(
                openTabs = openBoardTabs,
                onCloseClick = onCloseBoardTab,
                navController = navController,
                closeDrawer = closeDrawer,
                contentPadding = listContentPadding,
                selectedBoardTab = selectedBoardTab,
                pendingCloseBoardTab = pendingCloseBoardTab,
                onCloseRequestConsumed = onCloseRequestConsumed,
                onBoardTabLongPressed = onBoardTabLongPressed,
                tabsViewModel = tabsViewModel,
                isInLongPressSelectionMode = isInLongPressSelectionMode,
            )

            else -> OpenThreadsList(
                openTabs = openThreadTabs,
                onCloseClick = onCloseThreadTab,
                navController = navController,
                closeDrawer = closeDrawer,
                contentPadding = listContentPadding,
                newResCounts = newResCounts,
                selectedThreadTab = selectedThreadTab,
                pendingCloseThreadTab = pendingCloseThreadTab,
                onCloseRequestConsumed = onCloseRequestConsumed,
                onThreadTabLongPressed = onThreadTabLongPressed,
                onClearNewResCount = onClearNewResCount,
                tabsViewModel = tabsViewModel,
                isInLongPressSelectionMode = isInLongPressSelectionMode,
            )
        }
    }
}
