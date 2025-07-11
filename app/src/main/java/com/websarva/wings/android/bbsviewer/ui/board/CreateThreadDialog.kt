package com.websarva.wings.android.bbsviewer.ui.board

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.websarva.wings.android.bbsviewer.R

@Composable
fun CreateThreadDialog(
    onDismissRequest: () -> Unit,
    formState: CreateThreadFormState,
    onNameChange: (String) -> Unit,
    onMailChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onMessageChange: (String) -> Unit,
    onCreateClick: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = MaterialTheme.shapes.medium,
        ) {
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = formState.name,
                        onValueChange = onNameChange,
                        label = { Text("name") },
                        modifier = Modifier
                            .weight(1f)
                            .padding(8.dp)
                    )
                    TextField(
                        value = formState.mail,
                        onValueChange = onMailChange,
                        label = { Text(stringResource(R.string.e_mail)) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(8.dp)
                    )
                }
                TextField(
                    value = formState.title,
                    onValueChange = onTitleChange,
                    label = { Text(stringResource(R.string.title)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
                TextField(
                    value = formState.message,
                    onValueChange = onMessageChange,
                    label = { Text(stringResource(R.string.post_message)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
                Button(
                    onClick = onCreateClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text(text = stringResource(R.string.create_thread))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CreateThreadDialogPreview() {
    CreateThreadDialog(
        onDismissRequest = {},
        formState = CreateThreadFormState(),
        onNameChange = {},
        onMailChange = {},
        onTitleChange = {},
        onMessageChange = {},
        onCreateClick = {}
    )
}
