package com.websarva.wings.android.bbsviewer.ui.bbslist

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.bbsviewer.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BbsListTopBarScreen(
    modifier: Modifier = Modifier,
    title: String,
    onNavigationClick: () -> Unit,
    onSearchClick: () -> Unit, // 検索処理のためのコールバック
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = { // 左端にボタンを追加
            IconButton(onClick = onNavigationClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.open_tablist)
                )
            }
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.search)
                )
            }
        },
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun BbsListTopBarScreenPreview() {
    BbsListTopBarScreen(
        title = "カテゴリ",
        onNavigationClick = { /* doSomething() */ },
        onSearchClick = { /* doSomething() */ }
    )
}
