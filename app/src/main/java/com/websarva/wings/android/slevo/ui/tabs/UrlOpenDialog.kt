package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.websarva.wings.android.slevo.R

/**
 * URL入力用のダイアログを表示する。
 *
 * 入力エラーがある場合はテキストフィールドをエラー状態で表示する。
 */
@Composable
fun UrlOpenDialog(
    onDismissRequest: () -> Unit,
    onOpen: (String) -> Unit,
    isError: Boolean = false,
    errorMessage: String? = null,
    onValueChange: (String) -> Unit = {}
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.open_url)) },
        text = {
            TextField(
                value = url,
                onValueChange = {
                    url = it
                    onValueChange(it)
                },
                label = { Text(stringResource(R.string.enter_url)) },
                isError = isError,
                supportingText = {
                    if (!errorMessage.isNullOrBlank()) {
                        Text(text = errorMessage)
                    }
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { onOpen(url) }) {
                Text(text = stringResource(R.string.open))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}
