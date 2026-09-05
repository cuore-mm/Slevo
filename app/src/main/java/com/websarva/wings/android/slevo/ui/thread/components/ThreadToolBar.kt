package com.websarva.wings.android.slevo.ui.thread.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.ui.common.TabDestinationButton
import com.websarva.wings.android.slevo.ui.common.TabTitleCard
import com.websarva.wings.android.slevo.ui.common.TabToolBar
import com.websarva.wings.android.slevo.ui.common.TabToolBarAction
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkStatusState
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.thread.state.ThreadUiState

/**
 * スレッドToolbarに表示するPager連動タイトルカードを構成する。
 *
 * スレッド固有のタイトル表示設定とカード操作をこのToolbarファイルに集約し、Pagerの移動計算は呼び出し元へ委譲する。
 */
@Composable
fun ThreadTabTitleCard(
    tab: ThreadTabInfo,
    uiState: ThreadUiState,
    actionProgress: Float,
    modifier: Modifier = Modifier,
    onTitleClick: (ThreadTabInfo) -> Unit,
    onBookmarkClick: (ThreadTabInfo) -> Unit,
    onRefreshClick: (ThreadTabInfo) -> Unit,
) {
    TabTitleCard(
        modifier = modifier.fillMaxHeight(),
        title = uiState.threadInfo.title,
        bookmarkState = uiState.bookmarkStatusState,
        onTitleClick = { onTitleClick(tab) },
        onBookmarkClick = { onBookmarkClick(tab) },
        onRefreshClick = { onRefreshClick(tab) },
        titleStyle = MaterialTheme.typography.titleSmall,
        titleTextAlign = TextAlign.Start,
        titleFontWeight = FontWeight.Bold,
        titleMaxLines = 2,
        actionsProgress = actionProgress,
        isLoading = uiState.isLoading,
        loadProgress = uiState.loadProgress,
    )
}

/**
 * スレッド画面のソート、検索、投稿、タブ操作を共通TabToolBarへ渡す。
 *
 * Pager連動中のタイトルカードは必須のtitleContentとして受け取り、「板」ボタンをカード外へ固定する。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ThreadToolBar(
    modifier: Modifier = Modifier,
    uiState: ThreadUiState,
    isTreeSort: Boolean,
    onSortClick: () -> Unit,
    onPostClick: () -> Unit,
    onTabListClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMoreClick: () -> Unit,
    onAutoScrollClick: () -> Unit,
    actionsProgress: Float = 1f,
    canOpenBoard: Boolean,
    onOpenBoardClick: () -> Unit,
    titleContent: @Composable (Modifier) -> Unit,
) {
    // --- Actions ---
    val sortIcon = if (isTreeSort) Icons.Filled.AccountTree else Icons.Filled.FormatListNumbered
    val sortContentDescription = if (isTreeSort) R.string.tree_order else R.string.number_order
    val autoScrollIcon = if (uiState.isAutoScroll) Icons.Filled.Pause else Icons.Filled.PlayArrow
    val autoScrollContentDescription =
        if (uiState.isAutoScroll) R.string.stop_auto_scroll else R.string.start_auto_scroll

    // --- Title and destination slot ---
    val titleWithDestination: @Composable (Modifier) -> Unit = { cardModifier ->
        Row(
            modifier = cardModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabDestinationButton(
                modifier = Modifier.fillMaxHeight(),
                labelRes = R.string.open_board_screen,
                contentDescriptionRes = R.string.open_board_screen_description,
                enabled = canOpenBoard,
                onClick = onOpenBoardClick,
            )
            titleContent(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }

    val actions = listOf(
        TabToolBarAction(
            icon = sortIcon,
            contentDescriptionRes = sortContentDescription,
            onClick = onSortClick,
        ),
        TabToolBarAction(
            icon = Icons.Filled.Search,
            contentDescriptionRes = R.string.search,
            onClick = onSearchClick,
        ),
        TabToolBarAction(
            icon = Icons.Filled.CropSquare,
            contentDescriptionRes = R.string.open_tablist,
            onClick = onTabListClick,
        ),
        TabToolBarAction(
            icon = Icons.Filled.Create,
            contentDescriptionRes = R.string.post,
            onClick = onPostClick,
        ),
        TabToolBarAction(
            icon = autoScrollIcon,
            contentDescriptionRes = autoScrollContentDescription,
            onClick = onAutoScrollClick,
        ),
        TabToolBarAction(
            icon = Icons.Filled.Menu,
            contentDescriptionRes = R.string.other_options,
            onClick = onMoreClick,
        ),
    )

    TabToolBar(
        modifier = modifier,
        actions = actions,
        onTabListClick = onTabListClick,
        onPostClick = onPostClick,
        tabIconContentDescriptionRes = R.string.open_tablist,
        postIconContentDescriptionRes = R.string.post,
        actionsProgress = actionsProgress,
        titleContent = titleWithDestination,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun ThreadToolBarPreview() {
    val tab = ThreadTabInfo(
        id = ThreadId.of("example.com", "board", "123"),
        title = "スレッドのタイトル",
        boardName = "板",
        boardUrl = "https://example.com/board/",
        boardId = 1L,
    )
    val uiState = ThreadUiState(
        threadInfo = ThreadInfo(title = tab.title),
        bookmarkStatusState = BookmarkStatusState(),
    )
    ThreadToolBar(
        uiState = uiState,
        isTreeSort = false,
        onSortClick = {},
        onPostClick = {},
        onTabListClick = {},
        onSearchClick = {},
        onMoreClick = {},
        onAutoScrollClick = {},
        canOpenBoard = false,
        onOpenBoardClick = {},
        titleContent = { modifier ->
            ThreadTabTitleCard(
                modifier = modifier,
                tab = tab,
                uiState = uiState,
                actionProgress = 1f,
                onTitleClick = {},
                onBookmarkClick = {},
                onRefreshClick = {},
            )
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "ThreadToolBar Collapsed")
@Composable
fun ThreadToolBarCollapsedPreview() {
    val tab = ThreadTabInfo(
        id = ThreadId.of("example.com", "board", "123"),
        title = "スレッドのタイトル",
        boardName = "板",
        boardUrl = "https://example.com/board/",
        boardId = 1L,
    )
    val uiState = ThreadUiState(
        threadInfo = ThreadInfo(title = tab.title),
        bookmarkStatusState = BookmarkStatusState(),
    )
    ThreadToolBar(
        uiState = uiState,
        isTreeSort = false,
        onSortClick = {},
        onPostClick = {},
        onTabListClick = {},
        onSearchClick = {},
        onMoreClick = {},
        onAutoScrollClick = {},
        actionsProgress = 0f,
        canOpenBoard = false,
        onOpenBoardClick = {},
        titleContent = { modifier ->
            ThreadTabTitleCard(
                modifier = modifier,
                tab = tab,
                uiState = uiState,
                actionProgress = 0f,
                onTitleClick = {},
                onBookmarkClick = {},
                onRefreshClick = {},
            )
        },
    )
}
