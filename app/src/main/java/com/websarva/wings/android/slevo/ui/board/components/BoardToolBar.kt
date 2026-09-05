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

/**
 * 板画面固有のアクションとタイトル領域を共通TabToolBarへ渡す。
 *
 * Pager連動中のタイトルカードは呼び出し元から受け取り、「スレ」ボタンだけをカード外へ固定する。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BoardToolBar(
    modifier: Modifier = Modifier,
    uiState: BoardUiState,
    onSortClick: () -> Unit,
    onPostClick: () -> Unit,
    onTabListClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSearchClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onBoardInfoClick: () -> Unit,
    actionsProgress: Float = 1f,
    canOpenThread: Boolean,
    onOpenThreadClick: () -> Unit,
    titleCardContent: (@Composable (Modifier) -> Unit)? = null,
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
    val titleContent = titleCardContent?.let { content ->
        @Composable { cardModifier: Modifier ->
            Row(
                modifier = cardModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                content(Modifier.weight(1f))
                TabDestinationButton(
                    labelRes = R.string.open_thread_screen,
                    contentDescriptionRes = R.string.open_thread_screen_description,
                    enabled = canOpenThread,
                    onClick = onOpenThreadClick,
                )
            }
        }
    }

    TabToolBar(
        modifier = modifier,
        title = uiState.boardInfo.name,
        bookmarkState = uiState.bookmarkStatusState,
        onBookmarkClick = onBookmarkClick,
        actions = actions,
        onTabListClick = onTabListClick,
        onPostClick = onPostClick,
        tabIconContentDescriptionRes = R.string.open_tablist,
        postIconContentDescriptionRes = R.string.create_thread,
        actionsProgress = actionsProgress,
        onTitleClick = onBoardInfoClick,
        onRefreshClick = onRefreshClick,
        isLoading = uiState.isLoading,
        loadProgress = uiState.loadProgress,
        titleStyle = MaterialTheme.typography.titleMedium,
        titleFontWeight = FontWeight.Bold,
        titleMaxLines = 1,
        titleTextAlign = TextAlign.Center,
        titleCardContent = titleContent,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun BoardToolBarPreview() {
    val uiState = BoardUiState(
        boardInfo = BoardInfo(
            boardId = 1L,
            name = "板のタイトル",
            url = "https://example.com/board/",
        ),
    )
    BoardToolBar(
        uiState = uiState,
        onSortClick = {},
        onPostClick = {},
        onTabListClick = {},
        onRefreshClick = {},
        onSearchClick = {},
        onBookmarkClick = {},
        onBoardInfoClick = {},
        canOpenThread = true,
        onOpenThreadClick = {},
        titleCardContent = { modifier ->
            TabTitleCard(
                modifier = modifier,
                title = uiState.boardInfo.name,
                bookmarkState = uiState.bookmarkStatusState,
                onTitleClick = {},
                onBookmarkClick = {},
                onRefreshClick = {},
                titleStyle = MaterialTheme.typography.titleMedium,
                titleTextAlign = TextAlign.Center,
                titleFontWeight = FontWeight.Bold,
                titleMaxLines = 1,
            )
        },
    )
}
