package com.websarva.wings.android.bbsviewer.ui.bbslist.service

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.bbsviewer.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceListTopBarScreen(
    modifier: Modifier = Modifier,
    onNavigationClick: () -> Unit,
    onAddClick: () -> Unit, // 編集処理のためのコールバック
    onSearchClick: () -> Unit, // 検索処理のためのコールバック
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,          // 通常時の色
            scrolledContainerColor = MaterialTheme.colorScheme.surface   // 折りたたみ後も同じ色
        ),
        scrollBehavior = scrollBehavior,
        title = {},
        actions = {
            IconButton(onClick = onAddClick) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_board)
                )
            }
//            IconButton(onClick = onSearchClick) {
//                Icon(
//                    imageVector = Icons.Default.Search,
//                    contentDescription = stringResource(R.string.search)
//                )
//            }
        },
        modifier = modifier
    )
}

// BBSListTopBarScreenのプレビュー
@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun ServiceListTopBarScreenPreview() {
    ServiceListTopBarScreen(
        onNavigationClick = { /* doSomething() */ },
        onAddClick = { /* doSomething() */ },
        onSearchClick = { /* doSomething() */ }
    )
}
