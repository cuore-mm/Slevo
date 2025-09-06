package com.websarva.wings.android.slevo.ui.thread.dialog

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.websarva.wings.android.slevo.R
import kotlinx.coroutines.launch

@Composable
fun ThreadCopyDialog(
    threadTitle: String,
    threadUrl: String,
    onDismissRequest: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val threadTitleLabel = stringResource(R.string.title)
    val threadUrlLabel = stringResource(R.string.url)
    val titleAndUrlLabel = stringResource(R.string.title_and_url)

    Dialog(onDismissRequest = onDismissRequest) {
        Card(shape = MaterialTheme.shapes.medium) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.copy),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                CopyCard(
                    text = threadTitle,
                    label = threadTitleLabel,
                    onClick = {
                        scope.launch {
                            val clip =
                                ClipData.newPlainText(threadTitleLabel, threadTitle).toClipEntry()
                            clipboard.setClipEntry(clip)
                        }
                        onDismissRequest()
                    }
                )
                Spacer(Modifier.height(8.dp))
                CopyCard(
                    text = threadUrl,
                    label = threadUrlLabel,
                    onClick = {
                        scope.launch {
                            val clip =
                                ClipData.newPlainText(threadUrlLabel, threadUrl).toClipEntry()
                            clipboard.setClipEntry(clip)
                        }
                        onDismissRequest()
                    }
                )
                Spacer(Modifier.height(12.dp))
                val titleAndUrl = "$threadTitle\n$threadUrl"
                CopyCard(
                    text = titleAndUrl,
                    label = titleAndUrlLabel,
                    onClick = {
                        scope.launch {
                            val clip =
                                ClipData.newPlainText(titleAndUrlLabel, titleAndUrl).toClipEntry()
                            clipboard.setClipEntry(clip)
                        }
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
    label: String,
    onClick: () -> Unit,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        OutlinedCard(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
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
