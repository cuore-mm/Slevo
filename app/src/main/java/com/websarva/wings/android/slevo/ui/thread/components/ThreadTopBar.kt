package com.websarva.wings.android.slevo.ui.thread.components

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.ui.common.bookmark.SingleBookmarkState
import com.websarva.wings.android.slevo.ui.theme.bookmarkColor
import com.websarva.wings.android.slevo.ui.thread.dialog.ThreadInfoDialog
import com.websarva.wings.android.slevo.ui.thread.state.ThreadUiState

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
                if (uiState.singleBookmarkState.isBookmarked) {
                    // ブックマークされている場合：アイコンを重ねて表示
                    Box {
                        // ① 背景（内側の色）となる塗りつぶしアイコン
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null, // contentDescriptionは前景のアイコンに設定
                            tint = if (uiState.singleBookmarkState.selectedGroup?.colorName != null) {
                                // 動的に内側の色を指定
                                bookmarkColor(uiState.singleBookmarkState.selectedGroup.colorName)
                            } else {
                                // 色が指定されていない場合のデフォルトの塗りつぶし色
                                LocalContentColor.current
                            }
                        )
                        // ② 前景（縁取り）となるアイコン
                        Icon(
                            imageVector = Icons.Outlined.StarOutline,
                            contentDescription = stringResource(R.string.bookmark),
                        )
                    }
                } else {
                    // ブックマークされていない場合：縁取りアイコンのみを表示
                    Icon(
                        imageVector = Icons.Outlined.StarOutline,
                        contentDescription = stringResource(R.string.bookmark),
                        tint = LocalContentColor.current
                    )
                }
            }
//            IconButton(onClick = { dialogVisible = true }) {
//                Icon(
//                    imageVector = Icons.Outlined.Info,
//                    contentDescription = ""
//                )
//            }
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
