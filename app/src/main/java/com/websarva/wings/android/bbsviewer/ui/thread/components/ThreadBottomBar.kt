package com.websarva.wings.android.bbsviewer.ui.thread.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.bbsviewer.R

@Composable
fun ThreadBottomBar(
    modifier: Modifier = Modifier,
    isTreeSort: Boolean,
    onSortClick: () -> Unit,
    onPostClick: () -> Unit,
    onTabListClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSearchClick: () -> Unit,
) {
    BottomAppBar(
        modifier = modifier,
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
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
                IconButton(onClick = onRefreshClick) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh)
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
    )
}

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
    )
}
