package com.websarva.wings.android.bbsviewer.ui.bookmark

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.websarva.wings.android.bbsviewer.ui.thread.ReplyInfo
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadViewModel

@Composable
fun ThreadFetcherScreen(
    modifier: Modifier = Modifier,
    viewModel: ThreadViewModel
) {
    val threadUiState by viewModel.uiState.collectAsState()

    Column(modifier = modifier.padding(16.dp)) {
        ThreadUrlInput(
            url = viewModel.enteredUrl,
            onValueChange = { viewModel.updateTextField(it) },
            onUrlEntered = { viewModel.parseUrl() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // DATデータの表示
        threadUiState.posts?.let {
            if (it.isEmpty()) {
                Text("スレッドが見つかりません")
            } else {
                TmpThreadScreen(posts = it)
            }
        }
    }
}

@Composable
fun ThreadUrlInput(
    url: String,
    onValueChange: (String) -> Unit,
    onUrlEntered: () -> Unit
) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        TextField(
            value = url,
            onValueChange = onValueChange,
            label = { Text("5chスレのURLを入力") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onUrlEntered,
            enabled = url.isNotBlank()
        ) {
            Text("取得")
        }
    }
}

@Composable
fun TmpThreadScreen(posts: List<ReplyInfo>) {
    LazyColumn {
        items(posts) { post ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(text = "名前: ${post.name}", fontWeight = FontWeight.Bold)
                    Text(text = "日時: ${post.date}", fontSize = 12.sp, color = Color.Gray)
                    Text(text = post.content, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/*
@Preview(showBackground = true, backgroundColor = 0xFFF5F0EE)
@Composable
fun SearchBarPreview() {
    BBSViewerTheme { ThreadFetcherScreen() }
}

@Preview(showBackground = true)
@Composable
fun ThreadFetcherScreenPreview() {
    ThreadFetcherScreen()
}
*/
