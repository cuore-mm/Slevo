package com.websarva.wings.android.bbsviewer.ui.topbar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.EditNote
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
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
        scrollBehavior = scrollBehavior
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
fun BBSListTopBarScreen(
    modifier: Modifier = Modifier,
    onNavigationClick: () -> Unit,
    onEditClick: () -> Unit, // 編集処理のためのコールバック
    onSearchClick: () -> Unit, // 検索処理のためのコールバック

) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = stringResource(R.string.BBSList),
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
            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Default.EditNote,
                    contentDescription = stringResource(R.string.edit)
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
fun EditableBBSListTopBarScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onAddBoard: () -> Unit, // 新規掲示板追加処理のためのコールバック
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
                text = stringResource(R.string.edit),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        actions = {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .clickable(onClick = onAddBoard),
                contentAlignment = Alignment.Center
            ) {
                Row {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                    Text(
                        text = stringResource(R.string.add_board),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
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
fun BBSListTopBarScreenPreview() {
    BBSListTopBarScreen(
        onNavigationClick = { /* doSomething() */ },
        onEditClick = { /* doSomething() */ },
        onSearchClick = { /* doSomething() */ }
    )
}

// EditableBBSListTopBarScreenのプレビュー
@Preview(showBackground = true)
@Composable
fun EditableBBSListTopBarScreenPreview() {
    EditableBBSListTopBarScreen(
        onBack = { /* doSomething() */ },
        onAddBoard = { /* doSomething() */ },
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
