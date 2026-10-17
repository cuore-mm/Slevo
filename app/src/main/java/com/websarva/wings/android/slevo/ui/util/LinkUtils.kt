package com.websarva.wings.android.slevo.ui.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import com.websarva.wings.android.slevo.data.util.ReplyAnchorParser
import com.websarva.wings.android.slevo.ui.theme.imageUrlColor
import com.websarva.wings.android.slevo.ui.theme.replyColor
import com.websarva.wings.android.slevo.ui.theme.threadUrlColor
import com.websarva.wings.android.slevo.ui.theme.urlColor
import java.util.regex.Pattern

private val urlRegex: Pattern =
    Pattern.compile("(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)")

/** URLまたは返信tokenの元本文範囲と注釈値を保持する。 */
private data class TextLinkMatch(
    val start: Int,
    val end: Int,
    val value: String,
    val isReply: Boolean,
)

/**
 * 入力されたテキストからURLと返信アンカー（>>1など）を検出し、
 * クリック可能な注釈を付けた AnnotatedString を生成する。
 */
fun buildUrlAnnotatedString(
    text: String,
    replyColor: Color,
    imageColor: Color,
    threadColor: Color,
    urlColor: Color,
    pressedUrl: String? = null,
    pressedReply: String? = null,
    pressedColor: Color,
): AnnotatedString {
    // --- 共通tokenとURLのspanを収集 ---
    val matches = mutableListOf<TextLinkMatch>()
    val urlMatcher = urlRegex.matcher(text)
    while (urlMatcher.find()) {
        val url = urlMatcher.group(1) ?: continue
        matches += TextLinkMatch(urlMatcher.start(1), urlMatcher.end(1), url, false)
    }
    ReplyAnchorParser.parseAnchors(text).forEach { token ->
        matches += TextLinkMatch(token.start, token.end, token.rawText, true)
    }
    matches.sortWith(compareBy<TextLinkMatch> { it.start }.thenByDescending { !it.isReply })

    // --- 順序付きの注釈と描画を構築 ---
    return buildAnnotatedString {
        var lastIndex = 0
        matches.forEach { link ->
            if (link.start < lastIndex) return@forEach
            append(text.substring(lastIndex, link.start))
            val color = if (link.isReply) {
                if (link.value == pressedReply) pressedColor else replyColor
            } else {
                when {
                    link.value == pressedUrl -> pressedColor
                    imageExtensions.any { link.value.endsWith(it, ignoreCase = true) } -> imageColor
                    parseThreadUrl(link.value) != null -> threadColor
                    else -> urlColor
                }
            }
            val tag = if (link.isReply) "REPLY" else "URL"
            pushStringAnnotation(tag = tag, annotation = link.value)
            addStyle(
                SpanStyle(
                    color = color,
                    textDecoration = if (link.isReply) {
                        if (link.value == pressedReply) TextDecoration.Underline else TextDecoration.None
                    } else {
                        TextDecoration.Underline
                    },
                ),
                start = length,
                end = length + link.value.length,
            )
            append(link.value)
            pop()
            lastIndex = link.end
        }
        if (lastIndex < text.length) append(text.substring(lastIndex))
    }
}

private val imageExtensions = listOf("jpg", "jpeg", "png", "gif")

/** 元本文内の認識済み画像URLのoffsetとURL文字列を表す。 */
data class ImageUrlSpan(
    val start: Int,
    val end: Int,
    val url: String,
)

/**
 * テーマ色と押下状態に応じて本文の AnnotatedString をメモ化して返す。
 *
 * URL/返信アンカーの描画色が変わる場合のみ再計算する。
 */
@Composable
fun rememberUrlAnnotatedString(
    text: String,
    pressedUrl: String? = null,
    pressedReply: String? = null,
): AnnotatedString {
    val replyColor = replyColor()
    val imageColor = imageUrlColor()
    val threadColor = threadUrlColor()
    val urlColor = urlColor()
    val pressedColor = MaterialTheme.colorScheme.primary
    return remember(
        text,
        pressedUrl,
        pressedReply,
        replyColor,
        imageColor,
        threadColor,
        urlColor,
        pressedColor
    ) {
        buildUrlAnnotatedString(
            text = text,
            replyColor = replyColor,
            imageColor = imageColor,
            threadColor = threadColor,
            urlColor = urlColor,
            pressedUrl = pressedUrl,
            pressedReply = pressedReply,
            pressedColor = pressedColor
        )
    }
}

/**
 * テキスト中の画像URLを抽出します。
 */
fun extractImageUrls(text: String): List<String> {
    return extractImageUrlSpans(text).map { span -> span.url }
}

/** 本文中の画像URLを既存URL規則で認識し、元本文offset順で返す。 */
fun extractImageUrlSpans(text: String): List<ImageUrlSpan> {
    val matcher = urlRegex.matcher(text)
    val spans = mutableListOf<ImageUrlSpan>()
    while (matcher.find()) {
        val url = matcher.group(1) ?: continue
        if (imageExtensions.any { url.endsWith(it, ignoreCase = true) }) {
            spans += ImageUrlSpan(matcher.start(1), matcher.end(1), url)
        }
    }
    return spans
}

/** 認識済み画像URLの範囲だけを本文から取り除き、その他の文字とoffset順を保つ。 */
fun removeImageUrls(text: String): String {
    val builder = StringBuilder(text)
    extractImageUrlSpans(text).asReversed().forEach { span ->
        builder.delete(span.start, span.end)
    }
    return builder.toString()
}

/**
 * 画像URLの一覧から重複を除外し、出現順を保ったまま返します。
 */
fun distinctImageUrls(urls: List<String>): List<String> {
    return urls.distinct()
}
