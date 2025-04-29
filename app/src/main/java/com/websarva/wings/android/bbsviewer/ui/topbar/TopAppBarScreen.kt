package com.websarva.wings.android.bbsviewer.ui.topbar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.ui.thread.PopUpMenu
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopAppBarScreen(
    title: String,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    CenterAlignedTopAppBar(
        title = { Text(title) },
        modifier = modifier,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor            = MaterialTheme.colorScheme.primary)
        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmallTopAppBarScreen(
    modifier: Modifier = Modifier,
    title: String,
    onNavigateUp: () -> Unit, // 戻る処理のためのコールバック
    scrollBehavior: TopAppBarScrollBehavior? = null
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
            IconButton(onClick = onNavigateUp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        modifier = modifier,
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BbsServiceListTopBarScreen(
    modifier: Modifier = Modifier,
    onNavigationClick: () -> Unit,
    onAddClick: () -> Unit, // 編集処理のためのコールバック
    onSearchClick: () -> Unit, // 検索処理のためのコールバック

) {
    CenterAlignedTopAppBar(
        title = {},
        navigationIcon = { // 左端にボタンを追加
            IconButton(onClick = onNavigationClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = stringResource(R.string.open_tablist)
                )
            }
        },
        actions = {
            IconButton(onClick = onAddClick) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_board)
                )
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BbsCategoryListTopBarScreen(
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
                    imageVector = Icons.AutoMirrored.Filled.ViewList,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectedBbsListTopBarScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    selectedCount: Int
) {
    TopAppBar(
        navigationIcon ={
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        title = {
            Text(
                text = "$selectedCount"+stringResource(R.string.selected_count_label),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadTopBar(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onFavoriteClick: () -> Unit, // お気に入りボタンのコールバック
    uiState: ThreadUiState
) {
    // 中央ダイアログの表示状態を管理する変数
    var dialogVisible by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                    text = uiState.threadInfo.title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
        },
        modifier = modifier,

        scrollBehavior = scrollBehavior,
        actions = {
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "お気に入り"
                )
            }
            IconButton(onClick = { dialogVisible = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "その他の操作"
                )
            }
        }
    )

    // ダイアログ表示
    if (dialogVisible) {
        PopUpMenu(
            onDismissRequest = { dialogVisible = false },
            onEvaluateClick = {
                // 評価時の処理をここに記述
            },
            onNGThreadClick = {
                // NGThread時の処理をここに記述
            },
            onDeleteClick = {
                // 削除時の処理をここに記述
            },
            onArchiveClick = {
                // アーカイブ時の処理をここに記述
            },
            uiState = uiState
        )
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun CenterAlignedTopAppBarScreenPreview() {
    HomeTopAppBarScreen(
        title = "お気に入り"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun SmallTopAppBarScreenPreview() {
    SmallTopAppBarScreen(
        title = "お気に入り",
        onNavigateUp = { /* 戻る処理 */ }
    )
}

// BBSListTopBarScreenのプレビュー
@Preview(showBackground = true)
@Composable
fun BbsServiceListTopBarScreenPreview() {
    BbsServiceListTopBarScreen(
        onNavigationClick = { /* doSomething() */ },
        onAddClick = { /* doSomething() */ },
        onSearchClick = { /* doSomething() */ }
    )
}

@Preview(showBackground = true)
@Composable
fun BbsCategoryListTopBarScreenPreview() {
    BbsCategoryListTopBarScreen(
        title = "カテゴリ",
        onNavigationClick = { /* doSomething() */ },
        onSearchClick = { /* doSomething() */ }
    )
}

// EditableBBSListTopBarScreenのプレビュー
@Preview(showBackground = true)
@Composable
fun SelectedBbsListTopBarScreenPreview() {
    SelectedBbsListTopBarScreen(
        onBack = { /* doSomething() */ },
        selectedCount = 3
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun ThreadTopBarPreview() {
    ThreadTopBar(
        onFavoriteClick = { /* お気に入り処理 */ },
        uiState = ThreadUiState()
    )
}
