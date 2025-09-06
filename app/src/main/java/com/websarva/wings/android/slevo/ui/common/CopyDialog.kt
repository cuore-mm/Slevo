package com.websarva.wings.android.slevo.ui.common

import android.content.ClipData
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.slevo.R
import kotlinx.coroutines.launch

// Data class representing an item to copy
data class CopyItem(val text: String, val label: String)

// Generic dialog for copying multiple items
@Composable
fun CopyDialog(
    items: List<CopyItem>,
    onDismissRequest: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

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
                items.forEachIndexed { index, item ->
                    CopyCard(
                        text = item.text,
                        label = item.label,
                        onClick = {
                            scope.launch {
                                val clip = ClipData.newPlainText(item.label, item.text).toClipEntry()
                                clipboard.setClipEntry(clip)
                            }
                            onDismissRequest()
                        }
                    )
                    if (index != items.lastIndex) {
                        Spacer(Modifier.height(8.dp))
                    }
                }
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
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CopyDialogPreview() {
    val sample = listOf(
        CopyItem(text = "サンプルテキスト1", label = "ラベル1"),
        CopyItem(text = "サンプルテキスト2", label = "ラベル2"),
    )
    MaterialTheme {
        CopyDialog(items = sample, onDismissRequest = {})
    }
}
