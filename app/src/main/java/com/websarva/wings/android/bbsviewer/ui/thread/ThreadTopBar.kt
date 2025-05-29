package com.websarva.wings.android.bbsviewer.ui.thread

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.bbsviewer.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadTopBar(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onFavoriteClick: () -> Unit,
    uiState: ThreadUiState,
    onNavigationClick: () -> Unit
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
        navigationIcon = {
            IconButton(onClick = onNavigationClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.open_tablist)
                )
            }
        },
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
fun ThreadTopBarPreview() {
    ThreadTopBar(
        onFavoriteClick = { /* お気に入り処理 */ },
        uiState = ThreadUiState(),
        onNavigationClick = { /* ナビゲーション処理 */ },
    )
}
