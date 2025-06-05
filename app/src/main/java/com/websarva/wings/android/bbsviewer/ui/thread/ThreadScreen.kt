package com.websarva.wings.android.bbsviewer.ui.thread

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import java.util.regex.Pattern
import androidx.compose.ui.graphics.Color
import com.websarva.wings.android.bbsviewer.ui.common.InAppWebViewScreen // Import the InAppWebViewScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState

@Composable
fun ThreadScreen(
    modifier: Modifier = Modifier,
    // posts: List<ReplyInfo>, // This will come from uiState
    listState: LazyListState = rememberLazyListState(),
    viewModel: ThreadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // Remove local state:
    // var showInAppWebView by remember { mutableStateOf(false) }
    // var webViewUrl by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            // リスト全体の先頭に区切り線を追加
            if (uiState.posts?.isNotEmpty() == true) { // Use uiState.posts
                item {
                    HorizontalDivider()
                }
            }

            itemsIndexed(uiState.posts ?: emptyList()) { index, post -> // Use uiState.posts
                PostItem(
                    post = post,
                    postNum = index + 1,
                    onUrlClick = { url ->
                        viewModel.openInAppWebView(url) // Call ViewModel function
                    }
                )
                // 各アイテムの下に区切り線を表示
                HorizontalDivider()
            }
        }

        if (uiState.showInAppWebView && uiState.webViewUrl != null) {
            InAppWebViewScreen(
                url = uiState.webViewUrl!!,
                onDismiss = { viewModel.closeInAppWebView() } // Call ViewModel function
            )
        }
    }
}

@Composable
fun PostItem(
    modifier: Modifier = Modifier,
    post: ReplyInfo,
    postNum: Int,
    onUrlClick: (String) -> Unit // Callback to handle URL click
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Consider removing this top-level clickable if it interferes with ClickableText
            // .clickable(onClick = { /* クリック処理が必要な場合はここに実装 */ })
            .padding(horizontal = 16.dp, vertical = 8.dp),
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

        val annotatedString = buildAnnotatedString {
            append(post.content)
            // Regex to find URLs
            val urlPattern = Pattern.compile(
                "(?:^|[\\W])((ht|f)tp(s?):\\/\\/|www\\.)"
                        + "(([\\w\\-]+\\.){1,}?([\\w\\-.~]+\\/?)*"
                        + "[\\p{Alnum}.,%_=?&#\\-+()\\[\\]\\*$~@!:/{};']*)",
                Pattern.CASE_INSENSITIVE or Pattern.MULTILINE or Pattern.DOTALL
            )
            val matcher = urlPattern.matcher(post.content)
            while (matcher.find()) {
                val start = matcher.start(1)
                val end = matcher.end()
                val url = post.content.substring(start, end)
                addStyle(
                    style = SpanStyle(
                        color = Color.Blue, // Or MaterialTheme.colorScheme.primary
                        textDecoration = TextDecoration.Underline
                    ),
                    start = start,
                    end = end
                )
                addStringAnnotation(
                    tag = "URL",
                    annotation = url,
                    start = start,
                    end = end
                )
            }
        }

        // val uriHandler = LocalUriHandler.current // Not needed here anymore
        ClickableText(
            text = annotatedString,
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            onClick = { offset ->
                annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        onUrlClick(annotation.item) // Call the callback
                    }
            }
        )
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

@Composable
fun PopUpMenu(
    onDismissRequest: () -> Unit,
    onEvaluateClick: () -> Unit,
    onNGThreadClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onArchiveClick: () -> Unit,
    uiState: ThreadUiState
) {
    Dialog(onDismissRequest = onDismissRequest) {
        // ダイアログの内容をCardで包むことで見た目を整える
        Card(
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = uiState.threadInfo?.title ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                with(uiState.threadInfo.date) {
                    Text(
                        text = "${year}年${month}月${day}日${dayOfWeek}曜日 $hour:%02d".format(
                            minute
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                // ★評価のボタン（例）
                Button(
                    onClick = {
                        onEvaluateClick()
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(5) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("評価する")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onNGThreadClick()
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("NGThread")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onDeleteClick()
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("削除")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onArchiveClick()
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("アーカイブ")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ReplyCardPreview() {
    PostItem(
        post = ReplyInfo(
            name = "風吹けば名無し (ｵｰﾊﾟｲW ddad-g3Sx [2001:268:98f4:c793:*])",
            email = "sage",
            date = "1/21(月) 15:43:45.34",
            id = "testnanjj",
            content = "ガチで終わった模様 http://example.com" // Added a URL for preview
        ),
        postNum = 1,
        onUrlClick = {} // Dummy lambda for preview
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

@Preview(showBackground = true)
@Composable
fun PopUpMenuPreview() {
    PopUpMenu(
        onDismissRequest = { /* ダイアログを閉じる処理 */ },
        onEvaluateClick = { /* 評価処理 */ },
        onNGThreadClick = { /* NGスレッド処理 */ },
        onDeleteClick = { /* 削除処理 */ },
        onArchiveClick = { /* アーカイブ処理 */ },
        uiState = ThreadUiState(
            threadInfo = ThreadInfo(
                title = "スレッドタイトル",
            )
        )
    )
}
