package com.websarva.wings.android.bbsviewer.ui.thread

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun ThreadScreen(
    modifier: Modifier = Modifier,
    posts: List<ReplyInfo>,
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
            Text(
                text = "$postNum ${post.name} ${post.date} ${post.id}",
            )
            Text(
                text = post.content,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ReplyCardPreview() {
    PostCard(
        post = ReplyInfo(
            name = "風吹けば名無し",
            email = "email",
            date = "1/21(月) 15:43:45.34",
            id = "testnanjj",
            content = "ガチで終わった模様"
        ),
        postNum = 1
    )
}
