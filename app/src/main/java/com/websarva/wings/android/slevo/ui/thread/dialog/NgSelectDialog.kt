package com.websarva.wings.android.slevo.ui.thread.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.websarva.wings.android.slevo.R

@Composable
fun NgSelectDialog(
    onNgIdClick: () -> Unit,
    onNgNameClick: () -> Unit,
    onNgWordClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.medium) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.ng_registration),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onNgIdClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.add_to_ng_id))
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onNgNameClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.add_to_ng_name))
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onNgWordClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.add_to_ng_word))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NgSelectDialogPreview() {
    NgSelectDialog(
        onNgIdClick = {},
        onNgNameClick = {},
        onNgWordClick = {},
        onDismiss = {},
    )
}
