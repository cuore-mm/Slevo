package com.websarva.wings.android.bbsviewer.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun BoardInfoDialog(
    serviceName: String,
    boardName: String,
    boardUrl: String,
    onDismissRequest: () -> Unit,
    onLocalRuleClick: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = boardName,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = serviceName,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = boardUrl,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(
                    onClick = {
                        onDismissRequest()
                        onLocalRuleClick()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ローカルルール")
                }
            }
        }

    }
}

@Composable
@Preview(showBackground = true)
fun BoardInfoDialogPreview() {
    BoardInfoDialog(
        serviceName = "5ch",
        boardName = "なんでも実況J",
        boardUrl = "https://example.com/board",
        onDismissRequest = {},
        onLocalRuleClick = {}
    )
}
