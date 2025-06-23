package com.websarva.wings.android.bbsviewer.ui.board

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.websarva.wings.android.bbsviewer.R

@Composable
fun BoardInfoDialog(
    serviceName: String,
    boardName: String,
    boardUrl: String,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.board_information)) },
        text = {
            Column {
                Text(text = stringResource(R.string.service_name) + ": " + serviceName)
                Text(text = stringResource(R.string.board_name) + ": " + boardName)
                Text(text = stringResource(R.string.board_url) + ": " + boardUrl)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.ok))
            }
        }
    )
}
