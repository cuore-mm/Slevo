package com.websarva.wings.android.bbsviewer.ui.thread

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.toColorInt
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import com.websarva.wings.android.bbsviewer.ui.common.bookmark.SingleBookmarkState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadTopBar(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onBookmarkClick: () -> Unit,
    uiState: ThreadUiState,
    onNavigationClick: () -> Unit
) {
    // 中央ダイアログの表示状態を管理する変数
    var dialogVisible by remember { mutableStateOf(false) }

    MediumTopAppBar(
        title = {
            Text(
                text = uiState.threadInfo.title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = modifier,
        scrollBehavior = scrollBehavior,
        actions = {
            IconButton(onClick = onBookmarkClick) {
                val iconImage = if (uiState.singleBookmarkState.isBookmarked) {
                    Icons.Filled.Star
                } else {
                    Icons.Outlined.StarOutline
                }
                val iconTint = if (
                    uiState.singleBookmarkState.isBookmarked && uiState.singleBookmarkState.selectedGroup?.colorHex != null
                ) {
                    try {
                        Color(uiState.singleBookmarkState.selectedGroup!!.colorHex.toColorInt())
                    } catch (e: Exception) {
                        LocalContentColor.current
                    }
                } else {
                    LocalContentColor.current
                }
                Icon(
                    imageVector = iconImage,
                    contentDescription = stringResource(R.string.bookmark),
                    tint = iconTint
                )
            }
            IconButton(onClick = { dialogVisible = true }) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = ""
                )
            }
        }
    )

    // ダイアログ表示
    if (dialogVisible) {
        ThreadInfoDialog(
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
        onBookmarkClick = { /* お気に入り処理 */ },
        uiState = ThreadUiState(
            threadInfo = ThreadInfo(
                title = "スレッドのタイトル",
            ),
            singleBookmarkState = SingleBookmarkState(
                isBookmarked = false,
                selectedGroup = null
            )
        ),
        onNavigationClick = { /* ナビゲーション処理 */ },
    )
}
