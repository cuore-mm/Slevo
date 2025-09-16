package com.websarva.wings.android.slevo.ui.thread.item

import android.content.ClipData
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.DEFAULT_THREAD_LINE_HEIGHT
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.ui.common.CopyDialog
import com.websarva.wings.android.slevo.ui.common.CopyItem
import com.websarva.wings.android.slevo.ui.common.ImageThumbnailGrid
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.theme.idColor
import com.websarva.wings.android.slevo.ui.theme.replyCountColor
import com.websarva.wings.android.slevo.ui.thread.dialog.NgDialogRoute
import com.websarva.wings.android.slevo.ui.thread.dialog.NgSelectDialog
import com.websarva.wings.android.slevo.ui.thread.dialog.PostMenuDialog
import com.websarva.wings.android.slevo.ui.thread.dialog.TextMenuDialog
import com.websarva.wings.android.slevo.ui.thread.state.ReplyInfo
import com.websarva.wings.android.slevo.ui.util.buildUrlAnnotatedString
import com.websarva.wings.android.slevo.ui.util.extractImageUrls
import com.websarva.wings.android.slevo.ui.util.parseThreadUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

private const val PressFeedbackDelayMillis = 80L

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
    headerTextScale: Float,
    bodyTextScale: Float,
    lineHeight: Float,
    indentLevel: Int = 0,
    replyFromNumbers: List<Int> = emptyList(),
    isMyPost: Boolean = false,
    dimmed: Boolean = false,
    onReplyFromClick: ((List<Int>) -> Unit)? = null,
    onReplyClick: ((Int) -> Unit)? = null,
    onMenuReplyClick: ((Int) -> Unit)? = null,
    onIdClick: ((String) -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }
    var textMenuData by remember { mutableStateOf<Pair<String, NgType>?>(null) }
    var ngDialogData by remember { mutableStateOf<Pair<String, NgType>?>(null) }
    var showNgSelectDialog by remember { mutableStateOf(false) }
    var isColumnPressed by remember { mutableStateOf(false) }
    var isHeaderPressed by remember { mutableStateOf(false) }
    var isContentPressed by remember { mutableStateOf(false) }
    var pressedUrl by remember { mutableStateOf<String?>(null) }
    var pressedReply by remember { mutableStateOf<String?>(null) }
    var pressedHeaderPart by remember { mutableStateOf<String?>(null) }
    val isPressed = isColumnPressed || isHeaderPressed || isContentPressed
    val idText = if (idTotal > 1) "${post.id} (${idIndex}/${idTotal})" else post.id
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val boundaryColor = MaterialTheme.colorScheme.outlineVariant
    val bodyFontSize = MaterialTheme.typography.bodyMedium.fontSize * bodyTextScale
    val headerFontSize = MaterialTheme.typography.bodyMedium.fontSize * headerTextScale
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp * indentLevel)
            .alpha(if (dimmed) 0.6f else 1f)
            .drawBehind {
                if (indentLevel > 0) {
                    val strokeWidth = 1.dp.toPx()
                    drawLine(
                        color = boundaryColor,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = strokeWidth
                    )
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            if (isMyPost) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        if (isPressed) MaterialTheme.colorScheme.surfaceVariant
                        else Color.Transparent
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                handlePressFeedback(
                                    scope = scope,
                                    onFeedbackStart = { isContentPressed = true },
                                    onFeedbackEnd = { isContentPressed = false },
                                    awaitRelease = { awaitRelease() }
                                )
                            },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuExpanded = true
                            }
                        )
                    }
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
                        fun appendSpaceIfNeeded() {
                            if (!first) append(" ") else first = false
                        }
                        if (post.name.isNotBlank()) {
                            appendSpaceIfNeeded()
                            pushStringAnnotation(tag = "NAME", annotation = post.name)
                            withStyle(
                                SpanStyle(
                                    color = if (pressedHeaderPart == "NAME") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textDecoration = if (pressedHeaderPart == "NAME") TextDecoration.Underline else TextDecoration.None
                                )
                            ) {
                                append(post.name)
                            }
                            pop()
                        }
                        val currentYearPrefix = run {
                            val year = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                LocalDate.now().year
                            } else {
                                java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                            }
                            "$year/"
                        }
                        val displayDate =
                            if (post.date.startsWith(currentYearPrefix)) {
                                post.date.removePrefix(currentYearPrefix)
                            } else {
                                post.date
                            }
                        val emailDate =
                            listOf(post.email, displayDate).filter { it.isNotBlank() }
                                .joinToString(" ")
                        if (emailDate.isNotBlank()) {
                            appendSpaceIfNeeded()
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                append(emailDate)
                            }
                        }
                        if (post.id.isNotBlank()) {
                            appendSpaceIfNeeded()
                            pushStringAnnotation(tag = "ID", annotation = post.id)
                            withStyle(
                                SpanStyle(
                                    color = if (pressedHeaderPart == "ID") MaterialTheme.colorScheme.primary else idColor,
                                    textDecoration = if (pressedHeaderPart == "ID") TextDecoration.Underline else TextDecoration.None
                                )
                            ) {
                                append(idText)
                            }
                            pop()
                        }
                        if (post.beRank.isNotBlank()) {
                            appendSpaceIfNeeded()
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
                                    onPress = { offset ->
                                        headerLayout?.let { layout ->
                                            val pos = layout.getOffsetForPosition(offset)
                                            val nameAnn =
                                                headerText.getStringAnnotations("NAME", pos, pos)
                                                    .firstOrNull()
                                            val idAnn =
                                                headerText.getStringAnnotations("ID", pos, pos)
                                                    .firstOrNull()
                                            when {
                                                nameAnn != null ->
                                                    handlePressFeedback(
                                                        scope = scope,
                                                        feedbackDelayMillis = 0L,
                                                        onFeedbackStart = {
                                                            pressedHeaderPart = "NAME"
                                                        },
                                                        onFeedbackEnd = {
                                                            pressedHeaderPart = null
                                                        },
                                                        awaitRelease = { awaitRelease() }
                                                    )

                                                idAnn != null ->
                                                    handlePressFeedback(
                                                        scope = scope,
                                                        feedbackDelayMillis = 0L,
                                                        onFeedbackStart = {
                                                            pressedHeaderPart = "ID"
                                                        },
                                                        onFeedbackEnd = {
                                                            pressedHeaderPart = null
                                                        },
                                                        awaitRelease = { awaitRelease() }
                                                    )

                                                else ->
                                                    handlePressFeedback(
                                                        scope = scope,
                                                        onFeedbackStart = {
                                                            isContentPressed = true
                                                        },
                                                        onFeedbackEnd = {
                                                            isContentPressed = false
                                                        },
                                                        awaitRelease = { awaitRelease() }
                                                    )
                                            }
                                        } ?: handlePressFeedback(
                                            scope = scope,
                                            onFeedbackStart = { isContentPressed = true },
                                            onFeedbackEnd = { isContentPressed = false },
                                            awaitRelease = { awaitRelease() }
                                        )
                                    },
                                    onTap = { offset ->
                                        headerLayout?.let { layout ->
                                            val pos = layout.getOffsetForPosition(offset)
                                            headerText.getStringAnnotations("ID", pos, pos)
                                                .firstOrNull()?.let { ann ->
                                                    onIdClick?.invoke(post.id)
                                                }
                                        }
                                    },
                                    onLongPress = { offset ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        headerLayout?.let { layout ->
                                            val pos = layout.getOffsetForPosition(offset)
                                            val nameAnn =
                                                headerText.getStringAnnotations("NAME", pos, pos)
                                                    .firstOrNull()
                                            val idAnn =
                                                headerText.getStringAnnotations("ID", pos, pos)
                                                    .firstOrNull()
                                            when {
                                                nameAnn != null ->
                                                    textMenuData = nameAnn.item to NgType.USER_NAME

                                                idAnn != null ->
                                                    textMenuData = idAnn.item to NgType.USER_ID

                                                else -> menuExpanded = true
                                            }
                                        } ?: run { menuExpanded = true }
                                    }
                                )
                            },
                        text = headerText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = headerFontSize
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = lineHeight.em,
                        onTextLayout = { headerLayout = it }
                    )
                }

                val uriHandler = LocalUriHandler.current
                val annotatedText = buildUrlAnnotatedString(
                    text = post.content,
                    onOpenUrl = { uriHandler.openUri(it) },
                    pressedUrl = pressedUrl,
                    pressedReply = pressedReply
                )
                var contentLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

                Column(horizontalAlignment = Alignment.Start) {
                    if (post.beIconUrl.isNotBlank()) {
                        AsyncImage(
                            model = post.beIconUrl,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = { offset ->
                                        contentLayout?.let { layout ->
                                            val pos = layout.getOffsetForPosition(offset)
                                            val urlAnn =
                                                annotatedText.getStringAnnotations("URL", pos, pos)
                                                    .firstOrNull()
                                            val replyAnn =
                                                annotatedText.getStringAnnotations(
                                                    "REPLY",
                                                    pos,
                                                    pos
                                                )
                                                    .firstOrNull()
                                            when {
                                                urlAnn != null -> {
                                                    handlePressFeedback(
                                                        scope = scope,
                                                        feedbackDelayMillis = 0L,
                                                        onFeedbackStart = {
                                                            pressedUrl = urlAnn.item
                                                        },
                                                        onFeedbackEnd = { pressedUrl = null },
                                                        awaitRelease = { awaitRelease() }
                                                    )
                                                }

                                                replyAnn != null -> {
                                                    handlePressFeedback(
                                                        scope = scope,
                                                        feedbackDelayMillis = 0L,
                                                        onFeedbackStart = {
                                                            pressedReply = replyAnn.item
                                                        },
                                                        onFeedbackEnd = { pressedReply = null },
                                                        awaitRelease = { awaitRelease() }
                                                    )
                                                }

                                                else -> {
                                                    handlePressFeedback(
                                                        scope = scope,
                                                        onFeedbackStart = {
                                                            isContentPressed = true
                                                        },
                                                        onFeedbackEnd = {
                                                            isContentPressed = false
                                                        },
                                                        awaitRelease = { awaitRelease() }
                                                    )
                                                }
                                            }
                                        } ?: handlePressFeedback(
                                            scope = scope,
                                            onFeedbackStart = { isContentPressed = true },
                                            onFeedbackEnd = { isContentPressed = false },
                                            awaitRelease = { awaitRelease() }
                                        )
                                    },
                                    onTap = { offset ->
                                        contentLayout?.let { layout ->
                                            val pos = layout.getOffsetForPosition(offset)
                                            annotatedText.getStringAnnotations("URL", pos, pos)
                                                .firstOrNull()?.let { ann ->
                                                    val url = ann.item
                                                    parseThreadUrl(url)?.let { (host, board, key) ->
                                                        val boardUrl = "https://$host/$board/"
                                                        navController.navigate(
                                                            AppRoute.Thread(
                                                                threadKey = key,
                                                                boardUrl = boardUrl,
                                                                boardName = board,
                                                                threadTitle = url
                                                            )
                                                        ) { launchSingleTop = true }
                                                    } ?: uriHandler.openUri(url)
                                                }
                                            annotatedText.getStringAnnotations("REPLY", pos, pos)
                                                .firstOrNull()?.let { ann ->
                                                    ann.item.toIntOrNull()
                                                        ?.let { onReplyClick?.invoke(it) }
                                                }
                                        }
                                    },
                                    onLongPress = { offset ->
                                        contentLayout?.let { layout ->
                                            val pos = layout.getOffsetForPosition(offset)
                                            val urlAnn =
                                                annotatedText.getStringAnnotations("URL", pos, pos)
                                                    .firstOrNull()
                                            val replyAnn =
                                                annotatedText.getStringAnnotations(
                                                    "REPLY",
                                                    pos,
                                                    pos
                                                )
                                                    .firstOrNull()
                                            if (urlAnn == null && replyAnn == null) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuExpanded = true
                                            }
                                        } ?: run {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuExpanded = true
                                        }
                                    }
                                )
                            },
                        text = annotatedText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = bodyFontSize
                        ),
                        lineHeight = lineHeight.em,
                        onTextLayout = { contentLayout = it }
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
        }

        if (menuExpanded) {
            PostMenuDialog(
                postNum = postNum,
                onReplyClick = {
                    menuExpanded = false
                    onMenuReplyClick?.invoke(postNum)
                },
                onCopyClick = {
                    menuExpanded = false
                    showCopyDialog = true
                },
                onNgClick = {
                    menuExpanded = false
                    showNgSelectDialog = true
                },
                onDismiss = { menuExpanded = false }
            )
        }
        if (showCopyDialog) {
            val header = buildString {
                append(postNum)
                if (post.name.isNotBlank()) append(" ${post.name}")
                if (post.date.isNotBlank()) append(" ${post.date}")
                if (post.id.isNotBlank()) append(" ID:${post.id}")
            }
            CopyDialog(
                items = listOf(
                    CopyItem(postNum.toString(), stringResource(R.string.res_number_label)),
                    CopyItem(post.name, stringResource(R.string.name_label)),
                    CopyItem(post.id, stringResource(R.string.id_label)),
                    CopyItem(post.content, stringResource(R.string.post_message)),
                    CopyItem("$header\n${post.content}", stringResource(R.string.header_and_body)),
                ),
                onDismissRequest = { showCopyDialog = false }
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
            val clipboard = LocalClipboard.current
            TextMenuDialog(
                text = text,
                onCopyClick = {
                    scope.launch {
                        val clip = ClipData.newPlainText("", text).toClipEntry()
                        clipboard.setClipEntry(clip)
                    }
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

suspend fun handlePressFeedback(
    scope: CoroutineScope,
    feedbackDelayMillis: Long = PressFeedbackDelayMillis,
    onFeedbackStart: () -> Unit,
    onFeedbackEnd: () -> Unit,
    awaitRelease: suspend () -> Unit
) {
    var job: Job? = null
    try {
        job = scope.launch {
            delay(feedbackDelayMillis)
            onFeedbackStart()
        }
        awaitRelease()
    } finally {
        job?.cancel()
        onFeedbackEnd()
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
        headerTextScale = 0.85f,
        bodyTextScale = 1f,
        lineHeight = DEFAULT_THREAD_LINE_HEIGHT,
    )
}
