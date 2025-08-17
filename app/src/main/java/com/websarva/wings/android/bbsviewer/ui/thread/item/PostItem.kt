package com.websarva.wings.android.bbsviewer.ui.thread.item

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute
import com.websarva.wings.android.bbsviewer.ui.theme.idColor
import com.websarva.wings.android.bbsviewer.ui.theme.replyCountColor
import com.websarva.wings.android.bbsviewer.ui.util.buildUrlAnnotatedString
import com.websarva.wings.android.bbsviewer.ui.util.extractImageUrls
import com.websarva.wings.android.bbsviewer.ui.common.ImageThumbnailGrid
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.ui.thread.dialog.PostMenuDialog
import com.websarva.wings.android.bbsviewer.ui.thread.dialog.TextMenuDialog
import com.websarva.wings.android.bbsviewer.ui.thread.dialog.NgDialogRoute
import com.websarva.wings.android.bbsviewer.ui.thread.dialog.NgSelectDialog
import com.websarva.wings.android.bbsviewer.data.model.NgType
import com.websarva.wings.android.bbsviewer.ui.thread.state.ReplyInfo


@Composable
fun PostItem(
    modifier: Modifier = Modifier,
    post: ReplyInfo,
    postNum: Int,
    idIndex: Int,
    idTotal: Int,
    navController: NavHostController,
    boardName: String,
    boardId: Long,
    replyFromNumbers: List<Int> = emptyList(),
    onReplyFromClick: ((List<Int>) -> Unit)? = null,
    onReplyClick: ((Int) -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var textMenuData by remember { mutableStateOf<Pair<String, NgType>?>(null) }
    var ngDialogData by remember { mutableStateOf<Pair<String, NgType>?>(null) }
    var showNgSelectDialog by remember { mutableStateOf(false) }
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
                val headerText = buildAnnotatedString {
                    var first = true
                    fun appendLineIfNeeded() {
                        if (!first) append("\n") else first = false
                    }
                    if (post.name.isNotBlank()) {
                        appendLineIfNeeded()
                        pushStringAnnotation(tag = "NAME", annotation = post.name)
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            append(post.name)
                        }
                        pop()
                    }
                    val emailDate = listOf(post.email, post.date).filter { it.isNotBlank() }.joinToString(" ")
                    if (emailDate.isNotBlank()) {
                        appendLineIfNeeded()
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            append(emailDate)
                        }
                    }
                    if (post.id.isNotBlank()) {
                        appendLineIfNeeded()
                        pushStringAnnotation(tag = "ID", annotation = idText)
                        withStyle(SpanStyle(color = idColor)) {
                            append(idText)
                        }
                        pop()
                    }
                    if (post.beRank.isNotBlank()) {
                        appendLineIfNeeded()
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            append(post.beRank)
                        }
                    }
                }
                var headerLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
                Text(
                    modifier = Modifier
                        .alignByBaseline()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = { offset ->
                                    headerLayout?.let { layout ->
                                        val pos = layout.getOffsetForPosition(offset)
                                        headerText.getStringAnnotations("NAME", pos, pos).firstOrNull()?.let {
                                            textMenuData = it.item to NgType.USER_NAME
                                        }
                                        headerText.getStringAnnotations("ID", pos, pos).firstOrNull()?.let {
                                            textMenuData = it.item to NgType.USER_ID
                                        }
                                    }
                                }
                            )
                        },
                    text = headerText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    onTextLayout = { headerLayout = it }
                )
            }

            val uriHandler = LocalUriHandler.current
            val annotatedText = buildUrlAnnotatedString(
                text = post.content,
                onOpenUrl = { uriHandler.openUri(it) }
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

            val imageUrls = remember(post.content, post.urlFlags) {
                if (post.urlFlags and ReplyInfo.HAS_IMAGE_URL != 0) {
                    extractImageUrls(post.content)
                } else {
                    emptyList()
                }
            }
            if (imageUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                ImageThumbnailGrid(
                    imageUrls = imageUrls,
                    onImageClick = { url ->
                        navController.navigate(
                            AppRoute.ImageViewer(
                                imageUrl = URLEncoder.encode(
                                    url,
                                    StandardCharsets.UTF_8.toString()
                                )
                            )
                        )
                    }
                )
            }
        }

        if (menuExpanded) {
            PostMenuDialog(
                postNum = postNum,
                onCopyClick = { menuExpanded = false },
                onNgClick = {
                    menuExpanded = false
                    showNgSelectDialog = true
                },
                onDismiss = { menuExpanded = false }
            )
        }
        if (showNgSelectDialog) {
            NgSelectDialog(
                onNgIdClick = {
                    showNgSelectDialog = false
                    ngDialogData = post.id to NgType.USER_ID
                },
                onNgNameClick = {
                    showNgSelectDialog = false
                    ngDialogData = post.name to NgType.USER_NAME
                },
                onNgWordClick = {
                    showNgSelectDialog = false
                    ngDialogData = post.content to NgType.WORD
                },
                onDismiss = { showNgSelectDialog = false }
            )
        }
        textMenuData?.let { (text, type) ->
            val clipboardManager = LocalClipboardManager.current
            TextMenuDialog(
                text = text,
                onCopyClick = {
                    clipboardManager.setText(AnnotatedString(text))
                    textMenuData = null
                },
                onNgClick = {
                    textMenuData = null
                    ngDialogData = text to type
                },
                onDismiss = { textMenuData = null }
            )
        }
        ngDialogData?.let { (text, type) ->
            NgDialogRoute(
                text = text,
                type = type,
                boardName = boardName,
                boardId = boardId.takeIf { it != 0L },
                onDismiss = { ngDialogData = null }
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
        boardName = "board",
        boardId = 0L,
    )
}
