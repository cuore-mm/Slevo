package com.websarva.wings.android.slevo.ui.thread.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.ui.common.bookmark.SingleBookmarkState
import com.websarva.wings.android.slevo.ui.theme.bookmarkColor
import com.websarva.wings.android.slevo.ui.thread.state.ThreadUiState

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ThreadBottomBar(
    modifier: Modifier = Modifier,
    isTreeSort: Boolean,
    onSortClick: () -> Unit,
    onPostClick: () -> Unit,
    onTabListClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSearchClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    uiState: ThreadUiState,
    scrollBehavior: BottomAppBarScrollBehavior? = null,
) {
    FlexibleBottomAppBar(
        modifier = modifier,
        scrollBehavior = scrollBehavior,
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBookmarkClick) {
                        if (uiState.singleBookmarkState.isBookmarked) {
                            Box {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = uiState.singleBookmarkState.selectedGroup?.colorName?.let {
                                        bookmarkColor(it)
                                    } ?: LocalContentColor.current
                                )
                                Icon(
                                    imageVector = Icons.Outlined.StarOutline,
                                    contentDescription = stringResource(R.string.bookmark)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.StarOutline,
                                contentDescription = stringResource(R.string.bookmark),
                                tint = LocalContentColor.current
                            )
                        }
                    }
                    Text(
                        text = uiState.threadInfo.title,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = onRefreshClick) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onSortClick) {
                        Icon(
                            if (isTreeSort) Icons.Default.AccountTree else Icons.Default.FormatListNumbered,
                            contentDescription = stringResource(
                                if (isTreeSort) R.string.tree_order else R.string.number_order
                            )
                        )
                    }
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.search)
                        )
                    }
                    IconButton(onClick = onTabListClick) {
                        Icon(
                            Icons.Default.CropSquare,
                            contentDescription = stringResource(R.string.open_tablist)
                        )
                    }
                    IconButton(onClick = onPostClick) {
                        Icon(
                            Icons.Default.Create,
                            contentDescription = stringResource(R.string.post)
                        )
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun ThreadBottomBarPreview() {
    ThreadBottomBar(
        isTreeSort = false,
        onSortClick = {},
        onPostClick = {},
        onTabListClick = {},
        onRefreshClick = {},
        onSearchClick = {},
        onBookmarkClick = {},
        uiState = ThreadUiState(
            threadInfo = ThreadInfo(title = "スレッドのタイトル"),
            singleBookmarkState = SingleBookmarkState(
                isBookmarked = false,
                selectedGroup = null
            )
        )
    )
}
