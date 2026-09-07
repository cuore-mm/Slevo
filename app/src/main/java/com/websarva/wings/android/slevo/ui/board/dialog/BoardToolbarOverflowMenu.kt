package com.websarva.wings.android.slevo.ui.board.dialog

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.slevo.ui.common.BbsToolbarOverflowMenu

/**
 * 板画面のその他メニューを表示する。
 *
 * 板画面では表示設定を扱わないため、共通4項目だけを表示する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardToolbarOverflowMenu(
    onDismissRequest: () -> Unit,
    onBookmarkClick: () -> Unit,
    onBoardListClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    BbsToolbarOverflowMenu(
        onDismissRequest = onDismissRequest,
        onBookmarkClick = onBookmarkClick,
        onBoardListClick = onBoardListClick,
        onHistoryClick = onHistoryClick,
        onSettingsClick = onSettingsClick,
    )
}

@Preview(showBackground = true)
@Composable
fun BoardToolbarOverflowMenuPreview() {
    BoardToolbarOverflowMenu(
        onDismissRequest = {},
        onBookmarkClick = {},
        onBoardListClick = {},
        onHistoryClick = {},
        onSettingsClick = {},
    )
}
