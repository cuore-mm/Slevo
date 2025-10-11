package com.websarva.wings.android.slevo.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R

@Composable
fun ConfirmBottomDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    titleText: String? = null,
    messageText: String? = null,
    confirmLabel: String? = null,
    cancelLabel: String? = null,
    confirmEnabled: Boolean = true,
) {
    val confirmText = confirmLabel ?: stringResource(id = R.string.reset)
    val cancelText = cancelLabel ?: stringResource(id = R.string.cancel)

    BottomAlignedDialog(onDismiss = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            if (titleText != null) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (titleText != null && messageText != null) {
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (messageText != null) {
                Text(text = messageText, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text(text = cancelText)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onConfirm,
                    enabled = confirmEnabled
                ) {
                    Text(text = confirmText)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ConfirmBottomDialogPreview() {
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            ConfirmBottomDialog(
                onDismissRequest = {},
                onConfirm = {},
                titleText = "リセットしますか？",
                messageText = "この操作は元に戻せません。よろしいですか？",
                confirmLabel = "リセット",
                cancelLabel = "キャンセル",
                confirmEnabled = true
            )
        }
    }
}
