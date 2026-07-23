package com.websarva.wings.android.slevo.ui.settings.backup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme

/**
 * Cookie のバックアップ要否をトグルする Card コンポーネント。
 *
 * [BackupConfirmDialog] および [RestoreConfirmDialog] の中で
 * チェックボックス付きの説明付きカードを描画するために使われる。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CookieToggleCard(
    checked: Boolean,
    label: String,
    warning: String,
    onToggle: (Boolean) -> Unit,
) {
    val shape = MaterialTheme.shapes.largeIncreased
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .toggleable(
                value = checked,
                enabled = true,
                role = Role.Checkbox,
                onValueChange = onToggle,
            ),
        shape = shape,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = null,
                        enabled = true,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = warning,
                modifier = Modifier.padding(start = 32.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 確認ダイアログの共通骨格。
 *
 * タイトル・確認ボタン・キャンセルボタン・onDismissRequest を提供し、
 * ダイアログ本体のコンテンツは [content] スロットで注入する。
 */
@Composable
private fun GenericConfirmDialog(
    titleText: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = { Column(content = content) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        },
    )
}

/**
 * バックアップ作成の確認ダイアログ。
 *
 * Cookie の要否を [CookieToggleCard] で切り替えられる。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BackupConfirmDialog(
    includeCookies: Boolean,
    onCookiesToggle: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onCreate: () -> Unit,
) {
    GenericConfirmDialog(
        titleText = stringResource(id = R.string.backup_confirm_title),
        confirmText = stringResource(id = R.string.backup_confirm_create),
        dismissText = stringResource(id = android.R.string.cancel),
        onConfirm = onCreate,
        onDismiss = onCancel,
    ) {
        Text(
            text = stringResource(id = R.string.backup_confirm_description),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        CookieToggleCard(
            checked = includeCookies,
            label = stringResource(id = R.string.backup_include_cookies),
            warning = stringResource(id = R.string.backup_confirm_cookie_warning),
            onToggle = onCookiesToggle,
        )
    }
}

/**
 * 復元の確認ダイアログ。
 *
 * Cookie の要否を [CookieToggleCard] で切り替えられる。
 * バックアップからの上書きに関する警告文を表示する。
 * [containsCookies] が false の場合は Cookie 復元セクションを表示しない。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RestoreConfirmDialog(
    includeCookies: Boolean,
    containsCookies: Boolean,
    onCookiesToggle: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onRestore: () -> Unit,
) {
    GenericConfirmDialog(
        titleText = stringResource(id = R.string.restore_confirm_title),
        confirmText = stringResource(id = R.string.restore_confirm_restore),
        dismissText = stringResource(id = android.R.string.cancel),
        onConfirm = onRestore,
        onDismiss = onCancel,
    ) {
        Text(
            text = stringResource(id = R.string.restore_confirm_description),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (containsCookies) {
            Spacer(modifier = Modifier.height(24.dp))
            CookieToggleCard(
                checked = includeCookies,
                label = stringResource(id = R.string.restore_include_cookies),
                warning = stringResource(id = R.string.backup_confirm_cookie_warning),
                onToggle = onCookiesToggle,
            )
        }
    }
}

/**
 * 復元準備完了ダイアログ。
 *
 * pending restore 作成完了後に表示する。
 * 「あとで」で閉じても pending restore は保持され、
 * [次回アプリ起動時](PendingRestoreApplier)に復元が適用される。
 */
@Composable
internal fun RestorePreparedDialog(
    onDismiss: () -> Unit,
    onFinishActivity: () -> Unit,
) {
    GenericConfirmDialog(
        titleText = stringResource(id = R.string.restore_prepared_title),
        confirmText = stringResource(id = R.string.restore_prepared_exit),
        dismissText = stringResource(id = R.string.restore_prepared_later),
        onConfirm = onFinishActivity,
        onDismiss = onDismiss,
    ) {
        Text(
            text = stringResource(id = R.string.restore_prepared_description),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BackupConfirmDialogPreview() {
    SlevoTheme {
        BackupConfirmDialog(
            includeCookies = false,
            onCookiesToggle = {},
            onCancel = {},
            onCreate = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RestoreConfirmDialogPreview() {
    SlevoTheme {
        RestoreConfirmDialog(
            includeCookies = false,
            containsCookies = true,
            onCookiesToggle = {},
            onCancel = {},
            onRestore = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RestorePreparedDialogPreview() {
    SlevoTheme {
        RestorePreparedDialog(
            onDismiss = {},
            onFinishActivity = {},
        )
    }
}
