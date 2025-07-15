package com.websarva.wings.android.bbsviewer.ui.bookmarklist

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.bbsviewer.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkTopBar(
    modifier: Modifier = Modifier,
    onNavigationClick: () -> Unit,
    onAddClick: () -> Unit,
    onSearchClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    TopAppBar(
        scrollBehavior = scrollBehavior,
        title = {},
//        actions = {
//            IconButton(onClick = onAddClick) {
//                Icon(
//                    imageVector = Icons.Default.Add,
//                    contentDescription = stringResource(R.string.add_board)
//                )
//            }
//            IconButton(onClick = onSearchClick) {
//                Icon(
//                    imageVector = Icons.Default.Search,
//                    contentDescription = stringResource(R.string.search)
//                )
//            }
//        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun BookmarkTopBarPreview() {
    BookmarkTopBar(
        onNavigationClick = { /* ナビゲーション処理 */ },
        onAddClick = { /* 追加処理 */ },
        onSearchClick = { /* 検索処理 */ }
    )
}
