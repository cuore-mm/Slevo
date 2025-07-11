package com.websarva.wings.android.bbsviewer.ui.tabs

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
import com.websarva.wings.android.bbsviewer.R

@Composable
fun UrlOpenDialog(
    onDismissRequest: () -> Unit,
    onOpen: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.open_url)) },
        text = {
            TextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.enter_url)) }
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
