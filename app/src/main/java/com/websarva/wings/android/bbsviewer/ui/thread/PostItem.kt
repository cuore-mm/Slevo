package com.websarva.wings.android.bbsviewer.ui.thread

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute
import com.websarva.wings.android.bbsviewer.ui.theme.idColor
import com.websarva.wings.android.bbsviewer.ui.theme.replyColor
import com.websarva.wings.android.bbsviewer.ui.theme.replyCountColor
import com.websarva.wings.android.bbsviewer.ui.theme.imageUrlColor
import com.websarva.wings.android.bbsviewer.ui.theme.threadUrlColor
import com.websarva.wings.android.bbsviewer.ui.theme.urlColor
import com.websarva.wings.android.bbsviewer.ui.util.buildUrlAnnotatedString
import com.websarva.wings.android.bbsviewer.ui.util.extractImageUrls
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage


@Composable
fun PostItem(
    modifier: Modifier = Modifier,
    post: ReplyInfo,
    postNum: Int,
    idIndex: Int,
    idTotal: Int,
    navController: NavHostController,
    replyFromNumbers: List<Int> = emptyList(),
    onReplyFromClick: ((List<Int>) -> Unit)? = null,
    onReplyClick: ((Int) -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = { /* クリック処理が必要な場合はここに実装 */ })
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        val idColor = idColor(idTotal)
        val headerText = buildAnnotatedString {
            append("${post.name} ${post.email} ${post.date}")
            if (post.id.isNotBlank()) {
                append(" ")
                withStyle(style = SpanStyle(color = idColor)) {
                    append(if (idTotal > 1) "${post.id} (${idIndex}/${idTotal})" else post.id)
                }
            }
        }

        Row {
            val replyCount = replyFromNumbers.size
            val postNumColor = if (replyCount > 0) replyCountColor(replyCount) else MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                modifier = Modifier
                    .alignByBaseline()
                    .clickable(enabled = replyCount > 0) { onReplyFromClick?.invoke(replyFromNumbers) },
                text = if (replyCount > 0) "$postNum ($replyCount)" else postNum.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = postNumColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                modifier = Modifier.alignByBaseline(),
                text = headerText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val uriHandler = LocalUriHandler.current
        val annotatedText = buildUrlAnnotatedString(
            text = post.content,
            onOpenUrl = { uriHandler.openUri(it) },
            replyColor = replyColor(),
            imageColor = imageUrlColor(),
            threadColor = threadUrlColor(),
            urlColor = urlColor()
        )
        ClickableText(
            text = annotatedText,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            onClick = { offset ->
                annotatedText.getStringAnnotations("URL", offset, offset).firstOrNull()
                    ?.let { ann ->
                        uriHandler.openUri(ann.item)
                    }
                annotatedText.getStringAnnotations("REPLY", offset, offset).firstOrNull()
                    ?.let { ann ->
                        ann.item.toIntOrNull()?.let { onReplyClick?.invoke(it) }
                    }
            }
        )

        val imageUrls = remember(post.content) { extractImageUrls(post.content) }
        if (imageUrls.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                userScrollEnabled = false
            ) {
                items(imageUrls) { url ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                navController.navigate(
                                    AppRoute.ImageViewer(imageUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString()))
                                )
                            }
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
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
            content = "ガチで終わった模様"
        ),
        postNum = 1,
        idIndex = 1,
        idTotal = 1,
        navController = NavHostController(LocalContext.current),
    )
}
