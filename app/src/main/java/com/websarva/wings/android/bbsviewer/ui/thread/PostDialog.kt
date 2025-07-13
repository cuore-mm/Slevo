package com.websarva.wings.android.bbsviewer.ui.thread

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.websarva.wings.android.bbsviewer.R

@Composable
fun PostDialog(
    onDismissRequest: () -> Unit,
    postFormState: PostFormState,
    onNameChange: (String) -> Unit,
    onMailChange: (String) -> Unit,
    onMessageChange: (String) -> Unit,
    onImageSelect: (android.net.Uri) -> Unit,
    onPostClick: () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onImageSelect(uri)
    }

    Dialog(onDismissRequest = onDismissRequest) {
        // ダイアログの内容をCardで包むことで見た目を整える
        Card(
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    TextField(
                        value = postFormState.name,
                        onValueChange = { onNameChange(it) },
                        label = { Text("name") },
                        modifier = Modifier
                            .weight(1f)
                            .padding(8.dp)
                    )
                    TextField(
                        value = postFormState.mail,
                        onValueChange = { onMailChange(it) },
                        label = { Text(stringResource(R.string.e_mail)) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(8.dp)
                    )
                }
                TextField(
                    value = postFormState.message,
                    onValueChange = { onMessageChange(it) },
                    label = { Text(stringResource(R.string.post_message)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { launcher.launch("image/*") }) {
                        Icon(Icons.Filled.Image, contentDescription = stringResource(id = R.string.select_image))
                    }
                }
                Button(
                    onClick = { onPostClick() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text(text = stringResource(R.string.post))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PostDialogPreview() {
    PostDialog(
        onDismissRequest = { /* ダイアログを閉じる処理 */ },
        postFormState = PostFormState(),
        onNameChange = { /* 名前変更処理 */ },
        onMailChange = { /* メール変更処理 */ },
        onMessageChange = { /* メッセージ変更処理 */ },
        onImageSelect = { },
        onPostClick = { /* 投稿処理 */ }
    )
}
