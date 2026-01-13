package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.websarva.wings.android.slevo.R

/**
 * URL入力用のダイアログを表示する。
 *
 * 入力エラーがある場合はテキストフィールドをエラー状態で表示する。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UrlOpenDialog(
    onDismissRequest: () -> Unit,
    onOpen: (String) -> Unit,
    isError: Boolean = false,
    errorMessage: String? = null,
    isValidating: Boolean = false,
    onValueChange: (String) -> Unit = {}
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {
            if (!isValidating) {
                onDismissRequest()
            }
        },
        title = { Text(text = stringResource(R.string.open_url)) },
        text = {
            Box(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = url,
                    onValueChange = {
                        url = it
                        onValueChange(it)
                    },
                    label = { Text(stringResource(R.string.enter_url)) },
                    isError = isError,
                    enabled = !isValidating,
                    supportingText = {
                        if (!errorMessage.isNullOrBlank()) {
                            Text(text = errorMessage)
                        }
                    }
                )
                if (isValidating) {
                    Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ContainedLoadingIndicator()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onOpen(url) },
                enabled = !isValidating
            ) {
                Text(text = stringResource(R.string.open))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = !isValidating
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}
