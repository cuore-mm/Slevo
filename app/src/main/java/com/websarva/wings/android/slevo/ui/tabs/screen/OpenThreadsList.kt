package com.websarva.wings.android.slevo.ui.tabs.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.navigateToThread
import com.websarva.wings.android.slevo.ui.tabs.component.RemovableTabList
import com.websarva.wings.android.slevo.ui.tabs.component.TabHeaderTrailingContent
import com.websarva.wings.android.slevo.ui.tabs.component.TabListCard
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.theme.BookmarkColor
import com.websarva.wings.android.slevo.ui.theme.bookmarkColor
import kotlinx.coroutines.launch

/**
 * 開いているスレッドタブの一覧をカード表示し、選択されたタブへ遷移する。
 */
@Composable
fun OpenThreadsList(
    modifier: Modifier = Modifier,
    openTabs: List<ThreadTabInfo>,
    onCloseClick: (ThreadTabInfo) -> Unit = {},
    navController: NavHostController,
    closeDrawer: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    newResCounts: Map<String, Int> = emptyMap(),
    selectedThreadTab: ThreadTabInfo? = null,
    pendingCloseThreadTab: ThreadTabInfo? = null,
    onCloseRequestConsumed: () -> Unit = {},
    onThreadTabLongPressed: (ThreadTabInfo, IntRect) -> Unit = { _, _ -> },
    onClearNewResCount: (ThreadId) -> Unit = {},
    tabsViewModel: TabsViewModel? = null,
    isInLongPressSelectionMode: Boolean = false,
) {
    val coroutineScope = rememberCoroutineScope()

    // --- List ---
    RemovableTabList(
        modifier = modifier,
        tabItems = openTabs,
        keyOf = { it.id.value },
        contentPadding = contentPadding,
        externalRemoveKey = pendingCloseThreadTab?.id?.value,
        onExternalRemoveConsumed = onCloseRequestConsumed,
        onRemoveConfirmed = { onCloseClick(it) },
    ) { tab, isRemoving, requestRemove ->
        OpenThreadCard(
            tab = tab,
            newResCount = newResCounts[tab.id.value] ?: tab.newResCount,
            isSelected = selectedThreadTab?.id == tab.id,
            onClick = {
                if (isRemoving) return@OpenThreadCard
                closeDrawer()
                onClearNewResCount(tab.id)
                val route = AppRoute.Thread(
                    threadKey = tab.threadKey,
                    boardUrl = tab.boardUrl,
                    boardName = tab.boardName,
                    boardId = tab.boardId,
                    threadTitle = tab.title,
                    resCount = tab.resCount
                )
                coroutineScope.launch {
                    val normalizedRoute =
                        tabsViewModel?.normalizeThreadRouteForNavigation(route) ?: route
                    navController.navigateToThread(
                        route = normalizedRoute,
                        tabsViewModel = tabsViewModel,
                    ) {
                        restoreState = true
                    }
                }
            },
            onLongPress = { bounds ->
                if (isRemoving) return@OpenThreadCard
                onThreadTabLongPressed(tab, bounds)
            },
            isSwipeDeleteEnabled = !isInLongPressSelectionMode && !isRemoving,
            onSwipeDelete = {
                if (isRemoving) return@OpenThreadCard
                requestRemove()
            },
            onCloseClick = {
                if (isRemoving) return@OpenThreadCard
                requestRemove()
            },
        )
    }
}

/**
 * スレッドタブをカード表示する。
 */
@Composable
private fun OpenThreadCard(
    tab: ThreadTabInfo,
    newResCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: (IntRect) -> Unit,
    onCloseClick: () -> Unit,
    onSwipeDelete: (() -> Unit)? = null,
    isSwipeDeleteEnabled: Boolean = true,
) {
    // --- Card highlight ---
    val color = tab.bookmarkColorName?.let { bookmarkColor(it) }

    TabListCard(
        modifier = Modifier.padding(horizontal = 12.dp),
        bookmarkColor = color,
        onClick = onClick,
        onLongPress = onLongPress,
        isHiddenForSelection = isSelected,
        isPinned = tab.isPinned,
        headerTitle = tab.boardName,
        headerTrailingContent = TabHeaderTrailingContent.ThreadResCount(
            resCount = tab.resCount,
            newResCount = newResCount,
        ),
        bodyTitle = tab.title,
        bodyMaxLines = 2,
        onCloseClick = {
            // タブクローズ操作は一覧遷移より優先して処理する。
            onCloseClick()
        },
        onSwipeDelete = onSwipeDelete,
        isSwipeDeleteEnabled = isSwipeDeleteEnabled,
    )
}

@Preview(showBackground = true)
@Composable
fun OpenThreadsListPreview() {
    val sampleTabs = listOf(
        ThreadTabInfo(
            ThreadId.of("example.com", "board1", "1"),
            "スレッド1",
            "板1",
            "https://example.com/board1",
            1,
            100,
            bookmarkColorName = BookmarkColor.RED.value
        ),
        ThreadTabInfo(
            ThreadId.of("example.com", "board2", "2"),
            "スレッド2",
            "板2",
            "https://example.com/board2",
            2,
            200,
            bookmarkColorName = BookmarkColor.GREEN.value
        ),
        ThreadTabInfo(
            ThreadId.of("example.com", "board3", "3"),
            "スレッド3",
            "板3",
            "https://example.com/board3",
            3,
            300
        )
    )
    OpenThreadsList(
        openTabs = sampleTabs,
        onCloseClick = {},
        navController = rememberNavController(),
        closeDrawer = {},
        contentPadding = PaddingValues(0.dp),
        newResCounts = emptyMap(),
    )
}
