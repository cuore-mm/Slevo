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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.thread.item.rememberHighlightedText
import com.websarva.wings.android.slevo.data.model.ReplyInfo
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.util.rememberUrlAnnotatedString
import com.websarva.wings.android.slevo.ui.util.ResolvedUrl
import com.websarva.wings.android.slevo.ui.util.resolveUrl
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback

private const val BodyUrlTag = "URL"
private const val BodyReplyTag = "REPLY"

/**
 * 本文内のタップ対象を表すヒット結果。
 *
 * URL/返信番号のどちらか一方のみを保持し、両方 null は未ヒット扱いとする。
 */
private data class BodyHit(
    val url: String?,
    val reply: String?,
)

/**
 * 本文のタップ判定と描画を担当する。
 *
 * URL/返信番号/本文押下の状態を切り替える。
 */
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
    onUrlClick: (String) -> Unit,
    onThreadUrlClick: (AppRoute.Thread) -> Unit,
    onBodyClick: (() -> Unit)? = null,
) {
    // --- フィードバック ---
    val haptic = LocalHapticFeedback.current

    // --- 文字列処理 ---
    val annotatedText = rememberUrlAnnotatedString(
        text = post.body.content,
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
            modifier = Modifier.postBodyGestures(
                highlightedText = highlightedText,
                layoutProvider = { contentLayout },
                scope = scope,
                haptic = haptic,
                onPressedUrlChange = onPressedUrlChange,
                onPressedReplyChange = onPressedReplyChange,
                onContentPressedChange = onContentPressedChange,
                onRequestMenu = onRequestMenu,
                onReplyClick = onReplyClick,
                onUrlClick = onUrlClick,
                onThreadUrlClick = onThreadUrlClick,
                onBodyClick = onBodyClick,
            ),
            // --- テキスト描画 ---
            text = highlightedText,
            style = bodyTextStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            lineHeight = lineHeightEm.em,
            onTextLayout = { contentLayout = it }
        )
    }
}

/**
 * 本文のタップ対象を判定する。
 *
 * URL → 返信番号の順で検索し、見つからない場合は未ヒットを返す。
 */
private fun findBodyHit(
    text: AnnotatedString,
    layout: TextLayoutResult,
    offset: Offset,
): BodyHit {
    val pos = layout.getOffsetForPosition(offset)
    val url = text.getStringAnnotations(BodyUrlTag, pos, pos).firstOrNull()?.item
    if (url != null) {
        return BodyHit(url = url, reply = null)
    }
    val reply = text.getStringAnnotations(BodyReplyTag, pos, pos).firstOrNull()?.item
    return BodyHit(url = null, reply = reply)
}

/**
 * 本文のジェスチャー処理をまとめる。
 *
 * レイアウト未取得時は本文押下/長押しメニューとして扱う。
 */
private fun Modifier.postBodyGestures(
    highlightedText: AnnotatedString,
    layoutProvider: () -> TextLayoutResult?,
    scope: CoroutineScope,
    haptic: HapticFeedback,
    onPressedUrlChange: (String?) -> Unit,
    onPressedReplyChange: (String?) -> Unit,
    onContentPressedChange: (Boolean) -> Unit,
    onRequestMenu: () -> Unit,
    onReplyClick: ((Int) -> Unit)?,
    onUrlClick: (String) -> Unit,
    onThreadUrlClick: (AppRoute.Thread) -> Unit,
    onBodyClick: (() -> Unit)?,
): Modifier {
    return pointerInput(Unit) {
        // --- タップ判定 ---
        detectTapGestures(
            // --- 押下 ---
            onPress = { offset ->
                layoutProvider()?.let { layout ->
                    // --- ヒット判定 ---
                    val hit = findBodyHit(highlightedText, layout, offset)
                    handleBodyPressFeedback(
                        scope = scope,
                        hit = hit,
                        onPressedUrlChange = onPressedUrlChange,
                        onPressedReplyChange = onPressedReplyChange,
                        onContentPressedChange = onContentPressedChange,
                        awaitRelease = { awaitRelease() }
                    )
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
            // --- タップ ---
            onTap = { offset ->
                layoutProvider()?.let { layout ->
                    val hit = findBodyHit(highlightedText, layout, offset)
                    hit.url?.let { url ->
                        val resolved = resolveUrl(url)
                        if (resolved is ResolvedUrl.Thread) {
                            val boardUrl = "https://${resolved.host}/${resolved.boardKey}/"
                            val route = AppRoute.Thread(
                                threadKey = resolved.threadKey,
                                boardUrl = boardUrl,
                                boardName = resolved.boardKey,
                                threadTitle = url
                            )
                            onThreadUrlClick(route)
                        } else {
                            // スレッドURLでない場合は通常のURLとして開く。
                            onUrlClick(url)
                        }
                    }
                    hit.reply?.toIntOrNull()?.let { onReplyClick?.invoke(it) }
                    if (hit.url == null && hit.reply == null) {
                        onBodyClick?.invoke()
                    }
                }
            },
            // --- 長押し ---
            onLongPress = { offset ->
                layoutProvider()?.let { layout ->
                    val hit = findBodyHit(highlightedText, layout, offset)
                    if (hit.url == null && hit.reply == null) {
                        // リンク/返信以外は投稿メニューを表示する。
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
    }
}

/**
 * 押下対象に応じたフィードバック処理を実行する。
 *
 * URL/返信/本文のどれを押したかに応じて押下状態を切り替える。
 */
private suspend fun handleBodyPressFeedback(
    scope: CoroutineScope,
    hit: BodyHit,
    onPressedUrlChange: (String?) -> Unit,
    onPressedReplyChange: (String?) -> Unit,
    onContentPressedChange: (Boolean) -> Unit,
    awaitRelease: suspend () -> Unit,
) {
    when {
        hit.url != null -> {
            handlePressFeedback(
                scope = scope,
                feedbackDelayMillis = 0L,
                onFeedbackStart = { onPressedUrlChange(hit.url) },
                onFeedbackEnd = { onPressedUrlChange(null) },
                awaitRelease = awaitRelease
            )
        }

        hit.reply != null -> {
            handlePressFeedback(
                scope = scope,
                feedbackDelayMillis = 0L,
                onFeedbackStart = { onPressedReplyChange(hit.reply) },
                onFeedbackEnd = { onPressedReplyChange(null) },
                awaitRelease = awaitRelease
            )
        }

        else -> {
            handlePressFeedback(
                scope = scope,
                onFeedbackStart = { onContentPressedChange(true) },
                onFeedbackEnd = { onContentPressedChange(false) },
                awaitRelease = awaitRelease
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PostItemBodyPreview() {
    val scope = rememberCoroutineScope()
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
        onPressedUrlChange = { },
        onPressedReplyChange = { },
        onContentPressedChange = {},
        onRequestMenu = {},
        onReplyClick = {},
        onUrlClick = {},
        onThreadUrlClick = {},
    )
}
