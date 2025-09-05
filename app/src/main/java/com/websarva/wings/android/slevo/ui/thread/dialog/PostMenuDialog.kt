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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.slevo.R

@Composable
fun PostMenuDialog(
    postNum: Int,
    onReplyClick: () -> Unit,
    onCopyClick: () -> Unit,
    onNgClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.medium) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "$postNum",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onReplyClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.reply))
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onCopyClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.copy))
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onNgClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.ng_registration))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PostMenuDialogPreview() {
    PostMenuDialog(
        postNum = 123,
        onReplyClick = {},
        onCopyClick = {},
        onNgClick = {},
        onDismiss = {}
    )
}
