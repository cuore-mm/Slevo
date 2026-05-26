package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.navigateToBoard
import com.websarva.wings.android.slevo.ui.theme.BookmarkColor
import com.websarva.wings.android.slevo.ui.theme.bookmarkColor
import kotlinx.coroutines.launch

/**
 * 開いている板タブの一覧をカード表示し、選択されたタブへ遷移する。
 */
@Composable
fun OpenBoardsList(
    modifier: Modifier = Modifier,
    openTabs: List<BoardTabInfo>,
    onCloseClick: (BoardTabInfo) -> Unit = {},
    navController: NavHostController,
    closeDrawer: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    tabsViewModel: TabsViewModel? = null,
    exitingBoardTab: BoardTabInfo? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val uiState by tabsViewModel?.uiState?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(TabsUiState()) }

    // --- List ---
    RemovableTabList(
        modifier = modifier,
        tabItems = openTabs,
        keyOf = { it.boardUrl },
        contentPadding = contentPadding,
        onRemoveConfirmed = { onCloseClick(it) },
    ) { tab, isRemoving, requestRemove ->
        OpenBoardCard(
            tab = tab,
            isSelected = uiState.selectedBoardTab?.boardUrl == tab.boardUrl
                || exitingBoardTab?.boardUrl == tab.boardUrl,
            onClick = {
                if (isRemoving) return@OpenBoardCard
                closeDrawer()
                val route = AppRoute.Board(
                    boardId = tab.boardId,
                    boardName = tab.boardName,
                    boardUrl = tab.boardUrl
                )
                coroutineScope.launch {
                    val normalizedRoute = tabsViewModel?.normalizeBoardRouteForNavigation(route) ?: route
                    navController.navigateToBoard(
                        route = normalizedRoute,
                        tabsViewModel = tabsViewModel,
                    ) {
                        restoreState = true
                    }
                }
            },
            onLongPress = { bounds ->
                if (isRemoving) return@OpenBoardCard
                tabsViewModel?.onBoardTabLongPressed(tab, bounds)
            },
            onCloseClick = {
                if (isRemoving) return@OpenBoardCard
                requestRemove()
            },
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
    onClick: () -> Unit,
    onLongPress: (IntRect) -> Unit,
    onCloseClick: () -> Unit,
) {
    // --- Card highlight ---
    val color = tab.bookmarkColorName?.let { bookmarkColor(it) }
    val serviceName = tab.serviceName.ifBlank { extractServiceName(tab.boardUrl) }

    TabListCard(
        modifier = Modifier.padding(horizontal = 12.dp),
        bookmarkColor = color,
        onClick = onClick,
        onLongPress = onLongPress,
        isHiddenForSelection = isSelected,
        isPinned = tab.isPinned,
        headerTitle = serviceName,
        bodyTitle = tab.boardName,
        bodyMaxLines = 1,
        onCloseClick = {
            // タブクローズ操作は一覧遷移より優先して処理する。
            onCloseClick()
        },
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
