package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.navigateToThread
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.thread.item.rememberHighlightedText
import com.websarva.wings.android.slevo.data.model.ReplyInfo
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.util.buildUrlAnnotatedString
import com.websarva.wings.android.slevo.ui.util.parseThreadUrl
import kotlinx.coroutines.CoroutineScope

@Composable
internal fun PostItemBody(
    post: ThreadPostUiModel,
    bodyTextStyle: TextStyle,
    lineHeightEm: Float,
    searchQuery: String,
    pressedUrl: String?,
    pressedReply: String?,
    scope: CoroutineScope,
    onPressedUrlChange: (String?) -> Unit,
    onPressedReplyChange: (String?) -> Unit,
    onContentPressedChange: (Boolean) -> Unit,
    onRequestMenu: () -> Unit,
    onReplyClick: ((Int) -> Unit)?,
    navController: NavHostController,
    tabsViewModel: TabsViewModel?,
) {
    // --- フィードバック ---
    val haptic = LocalHapticFeedback.current

    // --- 文字列処理 ---
    val uriHandler = LocalUriHandler.current
    val annotatedText = buildUrlAnnotatedString(
        text = post.body.content,
        onOpenUrl = { uriHandler.openUri(it) },
        pressedUrl = pressedUrl,
        pressedReply = pressedReply
    )
    val highlightBackground = MaterialTheme.colorScheme.tertiaryContainer
    val highlightedText = rememberHighlightedText(
        baseText = annotatedText,
        rawContent = post.body.content,
        searchQuery = searchQuery,
        highlightColor = highlightBackground
    )

    // --- レイアウト参照 ---
    var contentLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Column(horizontalAlignment = Alignment.Start) {
        // --- BEアイコン ---
        if (post.header.beIconUrl.isNotBlank()) {
            AsyncImage(
                model = post.header.beIconUrl,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            modifier = Modifier.pointerInput(Unit) {
                // --- タップ判定 ---
                detectTapGestures(
                    onPress = { offset ->
                        contentLayout?.let { layout ->
                            val pos = layout.getOffsetForPosition(offset)
                            val urlAnn = highlightedText.getStringAnnotations("URL", pos, pos).firstOrNull()
                            val replyAnn = highlightedText.getStringAnnotations("REPLY", pos, pos).firstOrNull()
                            when {
                                urlAnn != null -> {
                                    handlePressFeedback(
                                        scope = scope,
                                        feedbackDelayMillis = 0L,
                                        onFeedbackStart = { onPressedUrlChange(urlAnn.item) },
                                        onFeedbackEnd = { onPressedUrlChange(null) },
                                        awaitRelease = { awaitRelease() }
                                    )
                                }

                                replyAnn != null -> {
                                    handlePressFeedback(
                                        scope = scope,
                                        feedbackDelayMillis = 0L,
                                        onFeedbackStart = { onPressedReplyChange(replyAnn.item) },
                                        onFeedbackEnd = { onPressedReplyChange(null) },
                                        awaitRelease = { awaitRelease() }
                                    )
                                }

                                else -> {
                                    handlePressFeedback(
                                        scope = scope,
                                        onFeedbackStart = { onContentPressedChange(true) },
                                        onFeedbackEnd = { onContentPressedChange(false) },
                                        awaitRelease = { awaitRelease() }
                                    )
                                }
                            }
                        } ?: run {
                            // レイアウト未取得時は本文押下として扱う。
                            handlePressFeedback(
                                scope = scope,
                                onFeedbackStart = { onContentPressedChange(true) },
                                onFeedbackEnd = { onContentPressedChange(false) },
                                awaitRelease = { awaitRelease() }
                            )
                        }
                    },
                    onTap = { offset ->
                        contentLayout?.let { layout ->
                            val pos = layout.getOffsetForPosition(offset)
                            highlightedText.getStringAnnotations("URL", pos, pos)
                                .firstOrNull()
                                ?.let { ann ->
                                    val url = ann.item
                                    parseThreadUrl(url)?.let { (host, board, key) ->
                                        val boardUrl = "https://$host/$board/"
                                        val route = AppRoute.Thread(
                                            threadKey = key,
                                            boardUrl = boardUrl,
                                            boardName = board,
                                            threadTitle = url
                                        )
                                        navController.navigateToThread(
                                            route = route,
                                            tabsViewModel = tabsViewModel,
                                        )
                                    } ?: uriHandler.openUri(url)
                                }
                            highlightedText.getStringAnnotations("REPLY", pos, pos)
                                .firstOrNull()
                                ?.let { ann ->
                                    ann.item.toIntOrNull()?.let { onReplyClick?.invoke(it) }
                                }
                        }
                    },
                    onLongPress = { offset ->
                        contentLayout?.let { layout ->
                            val pos = layout.getOffsetForPosition(offset)
                            val urlAnn = highlightedText.getStringAnnotations("URL", pos, pos).firstOrNull()
                            val replyAnn = highlightedText.getStringAnnotations("REPLY", pos, pos).firstOrNull()
                            if (urlAnn == null && replyAnn == null) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRequestMenu()
                            }
                        } ?: run {
                            // レイアウト未取得時は長押しメニュー扱いにする。
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRequestMenu()
                        }
                    }
                )
            },
            // --- テキスト描画 ---
            text = highlightedText,
            style = bodyTextStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            lineHeight = lineHeightEm.em,
            onTextLayout = { contentLayout = it }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PostItemBodyPreview() {
    val scope = rememberCoroutineScope()
    val navController = NavHostController(LocalContext.current)
    var pressedUrl by remember { mutableStateOf<String?>(null) }
    var pressedReply by remember { mutableStateOf<String?>(null) }

    PostItemBody(
        post = ThreadPostUiModel(
            header = ThreadPostUiModel.Header(
                name = "風吹けば名無し",
                email = "sage",
                date = "2025/12/16(火) 12:34:56.78",
                id = "testid",
                beRank = "PLT(2000)",
                beIconUrl = "https://img.5ch.net/ico/1fu.gif",
            ),
            body = ThreadPostUiModel.Body(
                content = "リンク https://example.com と >>12 を含む本文",
            ),
            meta = ThreadPostUiModel.Meta(
                urlFlags = ReplyInfo.HAS_OTHER_URL or ReplyInfo.HAS_THREAD_URL
            ),
        ),
        bodyTextStyle = MaterialTheme.typography.bodyMedium,
        lineHeightEm = 1.4f,
        searchQuery = "本文",
        pressedUrl = pressedUrl,
        pressedReply = pressedReply,
        scope = scope,
        onPressedUrlChange = { pressedUrl = it },
        onPressedReplyChange = { pressedReply = it },
        onContentPressedChange = {},
        onRequestMenu = {},
        onReplyClick = {},
        navController = navController,
        tabsViewModel = null,
    )
}
