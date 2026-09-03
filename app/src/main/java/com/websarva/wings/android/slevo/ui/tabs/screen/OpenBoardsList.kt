package com.websarva.wings.android.slevo.ui.tabs.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.showBoardScreenForTabSelection
import com.websarva.wings.android.slevo.ui.tabs.component.RemovableTabList
import com.websarva.wings.android.slevo.ui.tabs.component.TabListCard
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.theme.BookmarkColor
import com.websarva.wings.android.slevo.ui.theme.bookmarkColor
import com.websarva.wings.android.slevo.ui.util.parseServiceName
import kotlinx.coroutines.launch

/**
 * 開いている板タブの一覧をカード表示し、選択されたタブへ遷移する。
 */
@Composable
fun OpenBoardsList(
    modifier: Modifier = Modifier,
    openTabs: List<BoardTabInfo>,
    onCloseClick: (BoardTabInfo) -> Unit = {},
    onSwipeDelete: (BoardTabInfo) -> Unit = onCloseClick,
    navController: NavHostController,
    closeDrawer: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    listState: LazyListState = rememberLazyListState(),
    selectedBoardTab: BoardTabInfo? = null,
    isSelectionMode: Boolean = false,
    selectedBoardTabKeys: Set<String> = emptySet(),
    onBoardTabSelectionToggle: (String) -> Unit = {},
    removingKeys: Set<String> = emptySet(),
    onBoardTabLongPressed: (BoardTabInfo, IntRect) -> Unit = { _, _ -> },
    onBoardTabLongPressMoved: (Offset) -> Unit = {},
    onBoardTabLongPressReleased: () -> Unit = {},
    tabSessionStore: TabSessionStore? = null,
    isInLongPressSelectionMode: Boolean = false,
    isReorderEnabled: Boolean = false,
    onReorderStarted: (BoardTabInfo) -> Unit = {},
    onReorderMoved: (BoardTabInfo, BoardTabInfo) -> Unit = { _, _ -> },
    onReorderFinished: (BoardTabInfo) -> Unit = {},
    onReorderCancelled: (BoardTabInfo) -> Unit = {},
    onReorderAccessibilityMove: (BoardTabInfo, Int) -> Boolean = { _, _ -> false },
    currentScreenRoute: AppRoute? = null,
) {
    val coroutineScope = rememberCoroutineScope()

    // --- List ---
    RemovableTabList(
        modifier = modifier,
        tabItems = openTabs,
        keyOf = { it.boardUrl },
        contentPadding = contentPadding,
        listState = listState,
        removingKeys = removingKeys,
        onRemoveConfirmed = { onCloseClick(it) },
        userScrollEnabled = true,
        reorderEnabled = isReorderEnabled,
        onReorderStarted = onReorderStarted,
        onReorderMoved = onReorderMoved,
        onReorderFinished = onReorderFinished,
        onReorderCancelled = onReorderCancelled,
    ) { tab, isRemoving, requestRemove, isDragging, reorderHandle, reorderFinished, reorderCancelled ->
        OpenBoardCard(
            tab = tab,
            isSelected = selectedBoardTab?.boardUrl == tab.boardUrl,
            isSelectionMode = isSelectionMode,
            isSelectedForSelectionMode = tab.boardUrl in selectedBoardTabKeys,
            onSelectionToggle = { onBoardTabSelectionToggle(tab.boardUrl) },
            onClick = {
                if (isRemoving) return@OpenBoardCard
                if (isSelectionMode) {
                    onBoardTabSelectionToggle(tab.boardUrl)
                    return@OpenBoardCard
                }
                closeDrawer()
                val route = AppRoute.Board(
                    boardId = tab.boardId,
                    boardName = tab.boardName,
                    boardUrl = tab.boardUrl
                )
                coroutineScope.launch {
                    val normalizedRoute =
                        tabSessionStore?.normalizeBoardRouteForNavigation(route) ?: route
                    tabSessionStore?.registerAndSelectBoardRoute(normalizedRoute)
                    navController.showBoardScreenForTabSelection(
                        currentScreenRoute = currentScreenRoute,
                        route = normalizedRoute,
                    )
                }
            },
            onLongPress = { bounds ->
                if (isRemoving) return@OpenBoardCard
                onBoardTabLongPressed(tab, bounds)
            },
            onLongPressMoved = onBoardTabLongPressMoved,
            onLongPressReleased = onBoardTabLongPressReleased,
            reorderHandle = reorderHandle.takeIf { isReorderEnabled },
            onReorderFinished = reorderFinished,
            onReorderCancelled = reorderCancelled,
            onMoveUp = if (isReorderEnabled) {
                { onReorderAccessibilityMove(tab, -1) }
            } else null,
            onMoveDown = if (isReorderEnabled) {
                { onReorderAccessibilityMove(tab, 1) }
            } else null,
            isDragging = isDragging,
            isSwipeDeleteEnabled = !isInLongPressSelectionMode && !isSelectionMode && !isRemoving,
            onSwipeDelete = {
                if (isRemoving) return@OpenBoardCard
                onSwipeDelete(tab)
            },
            onCloseClick = {
                if (isRemoving) return@OpenBoardCard
                requestRemove()
            },
            isRemoving = isRemoving,
        )
    }
}

/**
 * 板タブをカード表示する。
 */
@Composable
private fun OpenBoardCard(
    tab: BoardTabInfo,
    isSelected: Boolean,
    isSelectionMode: Boolean = false,
    isSelectedForSelectionMode: Boolean = false,
    onSelectionToggle: () -> Unit = {},
    onClick: () -> Unit,
    onLongPress: (IntRect) -> Unit,
    onLongPressMoved: (Offset) -> Unit = {},
    onLongPressReleased: () -> Unit = {},
    onCloseClick: () -> Unit,
    onSwipeDelete: (() -> Unit)? = null,
    isSwipeDeleteEnabled: Boolean = true,
    isRemoving: Boolean = false,
    isDragging: Boolean = false,
    reorderHandle: ((sh.calvin.reorderable.DragGestureDetector) -> Modifier)? = null,
    onReorderFinished: () -> Unit = {},
    onReorderCancelled: () -> Unit = {},
    onMoveUp: (() -> Boolean)? = null,
    onMoveDown: (() -> Boolean)? = null,
) {
    // --- Card highlight ---
    val color = tab.bookmarkColorName?.let { bookmarkColor(it) }
    val serviceName = tab.serviceName.ifBlank { parseServiceName(tab.boardUrl) }

    TabListCard(
        modifier = Modifier.padding(horizontal = 12.dp),
        bookmarkColor = color,
        onClick = onClick,
        onLongPress = onLongPress,
        onLongPressMoved = onLongPressMoved,
        onLongPressReleased = onLongPressReleased,
        isHiddenForSelection = isSelected,
        isSelectionMode = isSelectionMode,
        isSelectedForSelectionMode = isSelectedForSelectionMode,
        onSelectionToggle = onSelectionToggle,
        isPinned = tab.isPinned,
        isRemoving = isRemoving,
        headerTitle = serviceName,
        bodyTitle = tab.boardName,
        bodyMaxLines = 1,
        onCloseClick = {
            // タブクローズ操作は一覧遷移より優先して処理する。
            onCloseClick()
        },
        onSwipeDelete = onSwipeDelete,
        isSwipeDeleteEnabled = isSwipeDeleteEnabled,
        reorderHandle = reorderHandle,
        onReorderFinished = onReorderFinished,
        onReorderCancelled = onReorderCancelled,
        isDragging = isDragging,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
    )
}

@Preview(showBackground = true)
@Composable
fun OpenBoardsListPreview() {
    val sampleBoards = listOf(
        BoardTabInfo(
            1,
            "板1",
            "https://example.com/board1",
            "example.com",
            bookmarkColorName = BookmarkColor.RED.value
        ),
        BoardTabInfo(
            2,
            "板2",
            "https://example.com/board2",
            "example.com",
            bookmarkColorName = BookmarkColor.GREEN.value
        ),
        BoardTabInfo(3, "板3", "https://example.com/board3", "example.com")
    )
    OpenBoardsList(
        openTabs = sampleBoards,
        onCloseClick = {},
        navController = rememberNavController(),
        closeDrawer = {}
    )
}
