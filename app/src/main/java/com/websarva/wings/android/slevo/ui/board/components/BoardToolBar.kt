package com.websarva.wings.android.slevo.ui.board.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Search
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
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.ui.board.state.BoardUiState
import com.websarva.wings.android.slevo.ui.common.TabDestinationButton
import com.websarva.wings.android.slevo.ui.common.TabTitleCard
import com.websarva.wings.android.slevo.ui.common.TabToolBar
import com.websarva.wings.android.slevo.ui.common.TabToolBarAction
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo

/**
 * 板Toolbarに表示するPager連動タイトルカードを構成する。
 *
 * 板固有のタイトル表示設定とカード操作をこのToolbarファイルに集約し、Pagerの移動計算は呼び出し元へ委譲する。
 */
@Composable
fun BoardTabTitleCard(
    tab: BoardTabInfo,
    uiState: BoardUiState,
    actionProgress: Float,
    modifier: Modifier = Modifier,
    onTitleClick: (BoardTabInfo) -> Unit,
    onBookmarkClick: (BoardTabInfo) -> Unit,
    onRefreshClick: (BoardTabInfo) -> Unit,
) {
    TabTitleCard(
        modifier = modifier,
        title = uiState.boardInfo.name,
        bookmarkState = uiState.bookmarkStatusState,
        onTitleClick = { onTitleClick(tab) },
        onBookmarkClick = { onBookmarkClick(tab) },
        onRefreshClick = { onRefreshClick(tab) },
        titleStyle = MaterialTheme.typography.titleMedium,
        titleTextAlign = TextAlign.Center,
        titleFontWeight = FontWeight.Bold,
        titleMaxLines = 1,
        actionsProgress = actionProgress,
        isLoading = uiState.isLoading,
        loadProgress = uiState.loadProgress,
    )
}

@Preview(showBackground = true)
@Composable
fun BoardTabTitleCardPreview() {
    val tab = BoardTabInfo(
        boardId = 1L,
        boardName = "板のタイトル",
        boardUrl = "https://example.com/board/",
        serviceName = "example",
    )
    BoardTabTitleCard(
        tab = tab,
        uiState = BoardUiState(
            boardInfo = BoardInfo(
                boardId = tab.boardId,
                name = tab.boardName,
                url = tab.boardUrl,
            ),
        ),
        actionProgress = 1f,
        onTitleClick = {},
        onBookmarkClick = {},
        onRefreshClick = {},
    )
}

/**
 * 板画面固有のアクションとタイトル領域を共通TabToolBarへ渡す。
 *
 * Pager連動中のタイトルカードは呼び出し元から受け取り、「スレ」ボタンだけをカード外へ固定する。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BoardToolBar(
    modifier: Modifier = Modifier,
    onSortClick: () -> Unit,
    onPostClick: () -> Unit,
    onTabListClick: () -> Unit,
    onSearchClick: () -> Unit,
    actionsProgress: Float = 1f,
    canOpenThread: Boolean,
    onOpenThreadClick: () -> Unit,
    titleContent: @Composable (Modifier) -> Unit,
) {
    // --- Board actions ---
    val actions = listOf(
        TabToolBarAction(
            icon = Icons.AutoMirrored.Filled.Sort,
            contentDescriptionRes = R.string.sort,
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
            contentDescriptionRes = R.string.create_thread,
            onClick = onPostClick,
        ),
    )

    // --- Title and destination slot ---
    val titleWithDestination: @Composable (Modifier) -> Unit = { cardModifier ->
        Row(
            modifier = cardModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            titleContent(Modifier.weight(1f))
            TabDestinationButton(
                labelRes = R.string.open_thread_screen,
                contentDescriptionRes = R.string.open_thread_screen_description,
                enabled = canOpenThread,
                onClick = onOpenThreadClick,
            )
        }
    }

    TabToolBar(
        modifier = modifier,
        actions = actions,
        onTabListClick = onTabListClick,
        onPostClick = onPostClick,
        tabIconContentDescriptionRes = R.string.open_tablist,
        postIconContentDescriptionRes = R.string.create_thread,
        actionsProgress = actionsProgress,
        titleContent = titleWithDestination,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun BoardToolBarPreview() {
    val tab = BoardTabInfo(
        boardId = 1L,
        boardName = "板のタイトル",
        boardUrl = "https://example.com/board/",
        serviceName = "example",
    )
    val uiState = BoardUiState(
        boardInfo = BoardInfo(
            boardId = tab.boardId,
            name = tab.boardName,
            url = tab.boardUrl,
        ),
    )
    BoardToolBar(
        onSortClick = {},
        onPostClick = {},
        onTabListClick = {},
        onSearchClick = {},
        canOpenThread = true,
        onOpenThreadClick = {},
        titleContent = { modifier ->
            BoardTabTitleCard(
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
