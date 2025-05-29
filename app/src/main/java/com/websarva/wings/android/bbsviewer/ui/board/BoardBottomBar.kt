package com.websarva.wings.android.bbsviewer.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
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
fun BoardBottomBar(
    modifier: Modifier = Modifier,
    onSortClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    BottomAppBar(
        modifier = modifier,
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // IconButtonをクリックするとメニューが展開される
                IconButton(onClick = onSortClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = stringResource(R.string.sort)
                    )
                }
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                }
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = stringResource(R.string.home)
                    )
                }
                IconButton(onClick = onRefreshClick) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh)
                    )
                }
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(
                        Icons.Default.Create,
                        contentDescription = stringResource(R.string.create_thread)
                    )
                }
                IconButton(onClick = {}) {
                    Icon(
                        Icons.Default.MoreHoriz,
                        contentDescription = stringResource(R.string.more)
                    )
                }
            }

        }
    )
}

@Preview(showBackground = true)
@Composable
fun BoardBottomBarPreview() {
    BoardBottomBar(
        onSortClick = {},
        onRefreshClick = { /* do something */ },
        onSearchClick = { /* do something */ }
    )
}
