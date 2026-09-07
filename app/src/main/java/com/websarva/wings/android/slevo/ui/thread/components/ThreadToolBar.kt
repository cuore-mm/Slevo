package com.websarva.wings.android.slevo.ui.thread.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.ui.common.TabDestinationAction
import com.websarva.wings.android.slevo.ui.common.TabDestinationPosition
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
 * Pager連動中のタイトルカードは必須のtitleContentとして受け取り、「板」アクションの内容を共通Toolbarへ渡す。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ThreadToolBar(
    modifier: Modifier = Modifier,
    destinationModifier: Modifier = Modifier,
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
    val sortIcon = if (isTreeSort) Icons.Outlined.AccountTree else Icons.Outlined.FormatListNumbered
    val sortContentDescription = if (isTreeSort) R.string.tree_order else R.string.number_order
    val autoScrollIcon =
        if (uiState.isAutoScroll) Icons.Outlined.Pause else Icons.Outlined.PlayArrow
    val autoScrollContentDescription =
        if (uiState.isAutoScroll) R.string.stop_auto_scroll else R.string.start_auto_scroll

    // --- Destination action ---
    val destinationAction = TabDestinationAction(
        icon = Icons.AutoMirrored.Outlined.ViewList,
        label = stringResource(R.string.open_board_screen),
        contentDescription = stringResource(R.string.open_board_screen_description),
        position = TabDestinationPosition.Start,
        enabled = canOpenBoard,
        onClick = onOpenBoardClick,
    )

    val actions = listOf(
        TabToolBarAction(
            icon = sortIcon,
            contentDescriptionRes = sortContentDescription,
            onClick = onSortClick,
        ),
        TabToolBarAction(
            icon = Icons.Outlined.Search,
            contentDescriptionRes = R.string.search,
            onClick = onSearchClick,
        ),
        TabToolBarAction(
            icon = Icons.Outlined.CropSquare,
            contentDescriptionRes = R.string.open_tablist,
            onClick = onTabListClick,
        ),
        TabToolBarAction(
            icon = Icons.Outlined.Create,
            contentDescriptionRes = R.string.post,
            onClick = onPostClick,
        ),
        TabToolBarAction(
            icon = autoScrollIcon,
            contentDescriptionRes = autoScrollContentDescription,
            onClick = onAutoScrollClick,
        ),
        TabToolBarAction(
            icon = Icons.Outlined.Menu,
            contentDescriptionRes = R.string.other_options,
            onClick = onMoreClick,
        ),
    )

    TabToolBar(
        modifier = modifier,
        destinationModifier = destinationModifier,
        actions = actions,
        onTabListClick = onTabListClick,
        onPostClick = onPostClick,
        tabIconContentDescriptionRes = R.string.open_tablist,
        postIconContentDescriptionRes = R.string.post,
        destinationAction = destinationAction,
        actionsProgress = actionsProgress,
        titleContent = titleContent,
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
