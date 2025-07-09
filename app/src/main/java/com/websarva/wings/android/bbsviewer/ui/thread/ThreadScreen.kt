package com.websarva.wings.android.bbsviewer.ui.thread

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.websarva.wings.android.bbsviewer.ui.util.buildUrlAnnotatedString

@Composable
fun ThreadScreen(
    modifier: Modifier = Modifier,
    posts: List<ReplyInfo>,
    listState: LazyListState = rememberLazyListState()
) {
    var popupPost by remember { mutableStateOf<ReplyInfo?>(null) }
    var anchorOffset by remember { mutableStateOf(IntOffset.Zero) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            if (posts.isNotEmpty()) {
                item {
                    HorizontalDivider()
                }
            }

            itemsIndexed(posts) { index, post ->
                var itemOffset by remember { mutableStateOf(IntOffset.Zero) }
                PostItem(
                    modifier = Modifier.onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        itemOffset = IntOffset(pos.x.toInt(), pos.y.toInt())
                    },
                    post = post,
                    postNum = index + 1,
                    onReplyClick = { num ->
                        if (num in 1..posts.size) {
                            popupPost = posts[num - 1]
                            anchorOffset = itemOffset
                        }
                    }
                )
                HorizontalDivider()
            }
        }

        popupPost?.let { reply ->
            Popup(
                popupPositionProvider = object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ): IntOffset {
                        return IntOffset(
                            anchorOffset.x,
                            (anchorOffset.y - popupContentSize.height).coerceAtLeast(0)
                        )
                    }
                },
                onDismissRequest = { popupPost = null }
            ) {
                Card {
                    PostItem(
                        post = reply,
                        postNum = posts.indexOf(reply) + 1,
                        onReplyClick = { num ->
                            if (num in 1..posts.size) {
                                popupPost = posts[num - 1]
                                // ポジションはそのまま
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PostItem(
    modifier: Modifier = Modifier,
    post: ReplyInfo,
    postNum: Int,
    onReplyClick: ((Int) -> Unit)? = null
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
        ClickableText(
            text = annotatedText,
            style = MaterialTheme.typography.bodyMedium,
            onClick = { offset ->
                annotatedText.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { ann ->
                    uriHandler.openUri(ann.item)
                }
                annotatedText.getStringAnnotations("REPLY", offset, offset).firstOrNull()?.let { ann ->
                    ann.item.toIntOrNull()?.let { onReplyClick?.invoke(it) }
                }
            }
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
