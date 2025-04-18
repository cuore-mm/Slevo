package com.websarva.wings.android.bbsviewer.ui.thread

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.websarva.wings.android.bbsviewer.R

@Composable
fun ThreadScreen(
    modifier: Modifier = Modifier,
    posts: List<ReplyInfo>
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        itemsIndexed(posts) { index, post ->
            PostCard(
                post = post,
                postNum = index + 1
            )
        }
    }
}

@Composable
fun PostCard(
    modifier: Modifier = Modifier,
    post: ReplyInfo,
    postNum: Int
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = { }),
        shape = RectangleShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            Row {
                Text(
                    text = postNum.toString(),
                    modifier = Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${post.name} ${post.email} ${post.date} ${post.id}",
                    modifier = Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Text(
                text = post.content,
            )
        }
    }
}

@Composable
fun PostDialog(
    onDismissRequest: () -> Unit,
    postFormState: PostFormState,
    onNameChange: (String) -> Unit,
    onMailChange: (String) -> Unit,
    onMessageChange: (String) -> Unit,
    onPostClick: () -> Unit
) {
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

@Composable
fun ConfirmationWebView(
    htmlContent: String,
    onDismissRequest: () -> Unit,
    onPostClick: () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        // ダイアログの背景とカードで囲む
        Card(
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f)
        ) {
            Column {
                // ヘッダー部
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "書き込み確認画面",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onPostClick) {
                        Icon(Icons.Default.Add, contentDescription = "書き込む")
                    }
                }
                HorizontalDivider()
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.apply {
                                javaScriptEnabled = false
                                // Shift_JIS をデフォルトエンコーディングに設定
                                defaultTextEncodingName = "Shift_JIS"
                            }
                        }
                    },
                    update = { webView ->
                        webView.loadDataWithBaseURL(
                            /* baseUrl = */ null,
                            /* data    = */ htmlContent,
                            /* mimeType= */ "text/html",
                            /* encoding= */ "Shift_JIS",
                            /* historyUrl = */ null
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ReplyCardPreview() {
    PostCard(
        post = ReplyInfo(
            name = "風吹けば名無し (ｵｰﾊﾟｲW ddad-g3Sx [2001:268:98f4:c793:*])",
            email = "sage",
            date = "1/21(月) 15:43:45.34",
            id = "testnanjj",
            content = "ガチで終わった模様"
        ),
        postNum = 1
    )
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
        onPostClick = { /* 投稿処理 */ }
    )
}
