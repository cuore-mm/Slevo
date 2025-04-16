package com.websarva.wings.android.bbsviewer.ui.topbar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
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

@Composable
fun PopUpMenu(
    onDismissRequest: () -> Unit,
    onEvaluateClick: () -> Unit,
    onNGThreadClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onArchiveClick: () -> Unit,
    uiState: ThreadUiState
) {
    Dialog(onDismissRequest = onDismissRequest) {
        // ダイアログの内容をCardで包むことで見た目を整える
        Card(
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = uiState.threadInfo?.title ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                with(uiState.threadInfo.date) {
                    Text(
                        text = "${year}年${month}月${day}日${dayOfWeek}曜日 $hour:%02d".format(minute),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                // ★評価のボタン（例）
                Button(
                    onClick = {
                        onEvaluateClick()
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(5) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("評価する")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onNGThreadClick()
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("NGThread")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onDeleteClick()
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("削除")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onArchiveClick()
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("アーカイブ")
                }
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun ThreadTopBarPreview() {
    ThreadTopBar(
        onFavoriteClick = { /* お気に入り処理 */ },
        uiState = ThreadUiState()
    )
}

@Preview(showBackground = true)
@Composable
fun PopUpMenuPreview() {
    PopUpMenu(
        onDismissRequest = { /* ダイアログを閉じる処理 */ },
        onEvaluateClick = { /* 評価処理 */ },
        onNGThreadClick = { /* NGスレッド処理 */ },
        onDeleteClick = { /* 削除処理 */ },
        onArchiveClick = { /* アーカイブ処理 */ },
        uiState = ThreadUiState(
            threadInfo = ThreadInfo(
                title = "スレッドタイトル",
            )
        )
    )
}
