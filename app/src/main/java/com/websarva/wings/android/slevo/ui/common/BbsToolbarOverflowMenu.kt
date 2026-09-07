package com.websarva.wings.android.slevo.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R

/**
 * BBS画面のその他メニューをダイアログとして表示する。
 *
 * 表示設定コールバックが指定された場合だけ、スレッド画面向けの表示設定項目を追加する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BbsToolbarOverflowMenu(
    onDismissRequest: () -> Unit,
    onBookmarkClick: () -> Unit,
    onBoardListClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDisplaySettingsClick: (() -> Unit)? = null,
) {
    BottomAlignedDialog(
        onDismiss = onDismissRequest,
    ) {
        BbsToolbarMenuContent(
            onBookmarkClick = onBookmarkClick,
            onBoardListClick = onBoardListClick,
            onHistoryClick = onHistoryClick,
            onSettingsClick = onSettingsClick,
            onDisplaySettingsClick = onDisplaySettingsClick,
        )
    }
}

/**
 * BBS画面のその他メニュー項目を画面種別に依存せず描画する。
 *
 * 共通4項目は常に表示し、表示設定コールバックが非nullの場合だけ先頭に表示設定を置く。
 */
@Composable
fun BbsToolbarMenuContent(
    onBookmarkClick: () -> Unit,
    onBoardListClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDisplaySettingsClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            onDisplaySettingsClick?.let { onClick ->
                LabeledIconButton(
                    icon = Icons.Filled.Tune,
                    label = stringResource(R.string.display_settings),
                    onClick = onClick,
                )
            }
            LabeledIconButton(
                icon = Icons.Filled.Star,
                label = stringResource(R.string.bookmark),
                onClick = onBookmarkClick,
            )
            LabeledIconButton(
                icon = Icons.AutoMirrored.Filled.List,
                label = stringResource(R.string.boardList),
                onClick = onBoardListClick,
            )
            LabeledIconButton(
                icon = Icons.Filled.History,
                label = stringResource(R.string.history),
                onClick = onHistoryClick,
            )
            if (onDisplaySettingsClick == null) {
                LabeledIconButton(
                    icon = Icons.Filled.Settings,
                    label = stringResource(R.string.settings),
                    onClick = onSettingsClick,
                )
            }
        }
        if (onDisplaySettingsClick != null) {
            Spacer(modifier = Modifier.height(8.dp))
            LabeledIconButton(
                icon = Icons.Filled.Settings,
                label = stringResource(R.string.settings),
                onClick = onSettingsClick,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BbsToolbarMenuContentPreview() {
    BbsToolbarMenuContent(
        onBookmarkClick = {},
        onBoardListClick = {},
        onHistoryClick = {},
        onSettingsClick = {},
    )
}
