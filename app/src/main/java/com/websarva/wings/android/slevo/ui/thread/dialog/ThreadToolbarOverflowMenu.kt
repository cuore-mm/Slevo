package com.websarva.wings.android.slevo.ui.thread.dialog

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.slevo.ui.common.BbsToolbarMenuContent
import com.websarva.wings.android.slevo.ui.common.BbsToolbarOverflowMenu

/**
 * スレッド画面のその他メニューを表示する。
 *
 * 表示設定を含む既存の5項目を共通メニューへ委譲する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadToolbarOverflowMenu(
    onDismissRequest: () -> Unit,
    onBookmarkClick: () -> Unit,
    onBoardListClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDisplaySettingsClick: () -> Unit,
) {
    BbsToolbarOverflowMenu(
        onDismissRequest = onDismissRequest,
        onBookmarkClick = onBookmarkClick,
        onBoardListClick = onBoardListClick,
        onHistoryClick = onHistoryClick,
        onSettingsClick = onSettingsClick,
        onDisplaySettingsClick = onDisplaySettingsClick,
    )
}

/**
 * スレッド画面のその他メニュー項目を描画する互換ラッパー。
 */
@Composable
fun ThreadToolbarMenuContent(
    onBookmarkClick: () -> Unit,
    onBoardListClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDisplaySettingsClick: () -> Unit,
) {
    BbsToolbarMenuContent(
        onBookmarkClick = onBookmarkClick,
        onBoardListClick = onBoardListClick,
        onHistoryClick = onHistoryClick,
        onSettingsClick = onSettingsClick,
        onDisplaySettingsClick = onDisplaySettingsClick,
    )
}

@Preview(showBackground = true)
@Composable
fun ThreadToolbarMenuContentPreview() {
    ThreadToolbarMenuContent(
        onBookmarkClick = {},
        onBoardListClick = {},
        onHistoryClick = {},
        onSettingsClick = {},
        onDisplaySettingsClick = {},
    )
}
