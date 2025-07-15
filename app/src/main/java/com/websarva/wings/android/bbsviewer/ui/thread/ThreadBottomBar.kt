package com.websarva.wings.android.bbsviewer.ui.thread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.bbsviewer.R

@Composable
fun ThreadBottomBar(
    modifier: Modifier = Modifier,
    onPostClick: () -> Unit,
    onTabListClick: () -> Unit,
    onRefreshClick: () -> Unit,
) {
    BottomAppBar(
        modifier = modifier,
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = { /* doSomething() */ }) {
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
                IconButton(onClick = onPostClick) {
                    Icon(
                        Icons.Default.Create,
                        contentDescription = stringResource(R.string.post)
                    )
                }
                IconButton(onClick = onTabListClick) {
                    Icon(
                        Icons.Default.CropSquare,
                        contentDescription = stringResource(R.string.open_tablist)
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
        onPostClick = {},
        onTabListClick = {},
        onRefreshClick = {}
    )
}
