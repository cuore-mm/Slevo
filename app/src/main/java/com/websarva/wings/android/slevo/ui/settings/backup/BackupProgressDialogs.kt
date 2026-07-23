package com.websarva.wings.android.slevo.ui.settings.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R

/**
 * 処理中を示す閉じられないダイアログの共通骨格。
 *
 * タイトルとメッセージを表示し、[CircularProgressIndicator] を描画する。
 */
@Composable
private fun GenericProgressDialog(
    titleText: String,
    messageText: String,
) {
    AlertDialog(
        onDismissRequest = { /* 処理中は閉じられない */ },
        title = { Text(titleText) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(messageText)
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }
        },
        confirmButton = { /* 処理中はボタンなし */ },
    )
}

/**
 * バックアップ作成中の処理中ダイアログ。
 */
@Composable
internal fun ExportingDialog() {
    GenericProgressDialog(
        titleText = stringResource(id = R.string.backup_exporting_title),
        messageText = stringResource(id = R.string.backup_exporting_message),
    )
}

/**
 * 復元準備中の処理中ダイアログ。
 */
@Composable
internal fun RestoringDialog() {
    GenericProgressDialog(
        titleText = stringResource(id = R.string.restore_preparing_title),
        messageText = stringResource(id = R.string.restore_preparing_message),
    )
}

@Preview(showBackground = true)
@Composable
private fun ExportingDialogPreview() {
    ExportingDialog()
}

@Preview(showBackground = true)
@Composable
private fun RestoringDialogPreview() {
    RestoringDialog()
}
