package com.websarva.wings.android.slevo.ui.thread.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.websarva.wings.android.slevo.R

@Composable
fun ThreadCopyDialog(
    threadTitle: String,
    threadUrl: String,
    onDismissRequest: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    Dialog(onDismissRequest = onDismissRequest) {
        Card(shape = MaterialTheme.shapes.medium) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.copy),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                CopyCard(
                    text = threadTitle,
                    onClick = {
                        clipboardManager.setText(AnnotatedString(threadTitle))
                        onDismissRequest()
                    }
                )
                Spacer(Modifier.height(8.dp))
                CopyCard(
                    text = threadUrl,
                    onClick = {
                        clipboardManager.setText(AnnotatedString(threadUrl))
                        onDismissRequest()
                    }
                )
                Spacer(Modifier.height(8.dp))
                val titleAndUrl = "$threadTitle\n$threadUrl"
                CopyCard(
                    text = titleAndUrl,
                    onClick = {
                        clipboardManager.setText(AnnotatedString(titleAndUrl))
                        onDismissRequest()
                    }
                )
            }
        }
    }
}

@Composable
private fun CopyCard(
    text: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ThreadCopyDialogPreview() {
    ThreadCopyDialog(
        threadTitle = "スレッドタイトル",
        threadUrl = "https://example.com/test/read.cgi/board/1234567890/",
        onDismissRequest = {}
    )
}
