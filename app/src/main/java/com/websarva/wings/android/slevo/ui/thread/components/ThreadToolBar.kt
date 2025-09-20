package com.websarva.wings.android.slevo.ui.thread.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.ui.common.TabToolBar
import com.websarva.wings.android.slevo.ui.common.TabToolBarAction
import com.websarva.wings.android.slevo.ui.common.bookmark.SingleBookmarkState
import com.websarva.wings.android.slevo.ui.thread.state.ThreadUiState

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ThreadToolBar(
    modifier: Modifier = Modifier,
    uiState: ThreadUiState,
    isTreeSort: Boolean,
    onSortClick: () -> Unit,
    onPostClick: () -> Unit,
    onTabListClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSearchClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onThreadInfoClick: () -> Unit,
    onMoreClick: () -> Unit,
    onAutoScrollClick: () -> Unit,
    scrollBehavior: BottomAppBarScrollBehavior? = null,
) {
    val sortIcon = if (isTreeSort) Icons.Filled.AccountTree else Icons.Filled.FormatListNumbered
    val sortContentDescription = if (isTreeSort) R.string.tree_order else R.string.number_order
    val autoScrollIcon = if (uiState.isAutoScroll) Icons.Filled.Pause else Icons.Filled.PlayArrow
    val autoScrollContentDescription =
        if (uiState.isAutoScroll) R.string.stop_auto_scroll else R.string.start_auto_scroll

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
            contentDescriptionRes = R.string.more,
            onClick = onMoreClick,
        ),
    )

    TabToolBar(
        modifier = modifier,
        title = uiState.threadInfo.title,
        bookmarkState = uiState.singleBookmarkState,
        onBookmarkClick = onBookmarkClick,
        actions = actions,
        scrollBehavior = scrollBehavior,
        onTitleClick = onThreadInfoClick,
        onRefreshClick = onRefreshClick,
        titleStyle = MaterialTheme.typography.titleSmall,
        titleFontWeight = FontWeight.Bold,
        titleMaxLines = 2,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun ThreadToolBarPreview() {
    ThreadToolBar(
        uiState = ThreadUiState(
            threadInfo = ThreadInfo(
                title = "スレッドのタイトル"
            ),
            singleBookmarkState = SingleBookmarkState(
                isBookmarked = false,
                selectedGroup = null
            )
        ),
        isTreeSort = false,
        onSortClick = {},
        onPostClick = {},
        onTabListClick = {},
        onRefreshClick = {},
        onSearchClick = {},
        onBookmarkClick = {},
        onThreadInfoClick = {},
        onMoreClick = {},
        onAutoScrollClick = {}
    )
}
