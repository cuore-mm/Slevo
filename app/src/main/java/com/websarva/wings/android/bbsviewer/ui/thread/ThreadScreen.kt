package com.websarva.wings.android.bbsviewer.ui.thread

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.bbsviewer.ui.util.buildUrlAnnotatedString

@Composable
fun ThreadScreen(
    modifier: Modifier = Modifier,
    posts: List<ReplyInfo>,
    listState: LazyListState = rememberLazyListState()
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        state = listState,
    ) {
        // リスト全体の先頭に区切り線を追加
        if (posts.isNotEmpty()) { // リストが空でない場合のみ線を表示
            item {
                HorizontalDivider()
            }
        }

        itemsIndexed(posts) { index, post ->
            PostItem(
                post = post,
                postNum = index + 1
            )
            // 各アイテムの下に区切り線を表示
            HorizontalDivider()
        }
    }
}

@Composable
fun PostItem(
    modifier: Modifier = Modifier,
    post: ReplyInfo,
    postNum: Int
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = { /* クリック処理が必要な場合はここに実装 */ })
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

        val uriHandler = LocalUriHandler.current
        val annotatedText = buildUrlAnnotatedString(post.content) { uriHandler.openUri(it) }
        Text(
            text = annotatedText,
            style = MaterialTheme.typography.bodyMedium
        )
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
            content = "ガチで終わった模様"
        ),
        postNum = 1
    )
}
