package com.websarva.wings.android.bbsviewer.ui.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute
import com.websarva.wings.android.bbsviewer.ui.theme.idColor
import com.websarva.wings.android.bbsviewer.ui.theme.imageUrlColor
import com.websarva.wings.android.bbsviewer.ui.theme.replyColor
import com.websarva.wings.android.bbsviewer.ui.theme.replyCountColor
import com.websarva.wings.android.bbsviewer.ui.theme.threadUrlColor
import com.websarva.wings.android.bbsviewer.ui.theme.urlColor
import com.websarva.wings.android.bbsviewer.ui.util.buildUrlAnnotatedString
import com.websarva.wings.android.bbsviewer.ui.util.extractImageUrls
import java.net.URLEncoder
import java.nio.charset.StandardCharsets


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
    var menuExpanded by remember { mutableStateOf(false) }
    var idMenuExpanded by remember { mutableStateOf(false) }
    val idText = if (idTotal > 1) "${post.id} (${idIndex}/${idTotal})" else post.id

    Box {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { /* クリック処理が必要な場合はここに実装 */ },
                    onLongClick = { menuExpanded = true }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val idColor = idColor(idTotal)

            Row {
                val replyCount = replyFromNumbers.size
                val postNumColor =
                    if (replyCount > 0) replyCountColor(replyCount) else MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    modifier = Modifier
                        .alignByBaseline()
                        .clickable(enabled = replyCount > 0) {
                            onReplyFromClick?.invoke(
                                replyFromNumbers
                            )
                        },
                    text = if (replyCount > 0) "$postNum ($replyCount)" else postNum.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = postNumColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                FlowRow(
                    modifier = Modifier.alignByBaseline(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${post.name} ${post.email} ${post.date}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (post.id.isNotBlank()) {
                        Text(
                            modifier = Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = { idMenuExpanded = true }
                            ),
                            text = idText,
                            style = MaterialTheme.typography.labelMedium,
                            color = idColor
                        )
                    }
                    if (post.beRank.isNotBlank()) {
                        Text(
                            text = post.beRank,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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

            Column(horizontalAlignment = Alignment.Start) {
                if (post.beIconUrl.isNotBlank()) {
                    AsyncImage(
                        model = post.beIconUrl,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
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
            }

            val imageUrls = remember(post.content) { extractImageUrls(post.content) }
            if (imageUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    imageUrls.chunked(3).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            rowItems.forEach { url ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f) // Row内の各要素の幅を均等に
                                        .aspectRatio(1f)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            navController.navigate(
                                                AppRoute.ImageViewer(
                                                    imageUrl = URLEncoder.encode(
                                                        url,
                                                        StandardCharsets.UTF_8.toString()
                                                    )
                                                )
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
                            // 行のアイテムが3つ未満の場合、残りをSpacerで埋めてレイアウトを維持
                            repeat(3 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        if (menuExpanded) {
            PostMenuDialog(
                postNum = postNum,
                onCopyClick = { menuExpanded = false },
                onNgClick = { menuExpanded = false },
                onDismiss = { menuExpanded = false }
            )
        }
        if (idMenuExpanded) {
            val clipboardManager = LocalClipboardManager.current
            IdMenuDialog(
                idText = idText,
                onCopyClick = {
                    clipboardManager.setText(AnnotatedString(post.id))
                    idMenuExpanded = false
                },
                onNgClick = { idMenuExpanded = false },
                onDismiss = { idMenuExpanded = false }
            )
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
            beLoginId = "12345",
            beRank = "PLT(2000)",
            beIconUrl = "https://img.5ch.net/ico/1fu.gif",
            content = "ガチで終わった模様"
        ),
        postNum = 1,
        idIndex = 1,
        idTotal = 1,
        navController = NavHostController(LocalContext.current),
    )
}
