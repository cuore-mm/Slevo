package com.websarva.wings.android.slevo.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.slevo.R

@Composable
fun BoardBottomBar(
    modifier: Modifier = Modifier,
    onSortClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSearchClick: () -> Unit,
    onTabListClick: () -> Unit,
    onCreateThreadClick: () -> Unit,
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
                IconButton(onClick = onCreateThreadClick) {
                    Icon(
                        Icons.Default.Create,
                        contentDescription = stringResource(R.string.create_thread)
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
        onSearchClick = { /* do something */ },
        onTabListClick = {},
        onCreateThreadClick = {}
    )
}
