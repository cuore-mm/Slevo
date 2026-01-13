package com.websarva.wings.android.slevo.ui.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import com.websarva.wings.android.slevo.ui.theme.imageUrlColor
import com.websarva.wings.android.slevo.ui.theme.replyColor
import com.websarva.wings.android.slevo.ui.theme.threadUrlColor
import com.websarva.wings.android.slevo.ui.theme.urlColor
import java.util.regex.Pattern

private val urlRegex: Pattern =
    Pattern.compile("(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)")
private val replyRegex: Pattern = Pattern.compile(">>(\\d+)")

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
    return buildAnnotatedString {
        // --- パターン準備 ---
        var lastIndex = 0
        val pattern = Pattern.compile("${urlRegex.pattern()}|${replyRegex.pattern()}")
        val matcher = pattern.matcher(text)
        // --- マッチ処理 ---
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            if (start > lastIndex) {
                append(text.substring(lastIndex, start))
            }
            val match = text.substring(start, end)
            when {
                urlRegex.matcher(match).matches() -> {
                    val color = when {
                        match == pressedUrl -> pressedColor
                        imageExtensions.any { match.endsWith(it, ignoreCase = true) } -> imageColor
                        parseThreadUrl(match) != null -> threadColor
                        else -> urlColor
                    }
                    pushStringAnnotation(tag = "URL", annotation = match)
                    addStyle(
                        SpanStyle(color = color, textDecoration = TextDecoration.Underline),
                        start = length,
                        end = length + match.length
                    )
                    append(match)
                    pop()
                }

                replyRegex.matcher(match).matches() -> {
                    val number = replyRegex.matcher(match).run { if (find()) group(1) else null }
                    val color = if (number == pressedReply) pressedColor else replyColor
                    val decoration = if (number == pressedReply) TextDecoration.Underline else TextDecoration.None
                    pushStringAnnotation(tag = "REPLY", annotation = number ?: "")
                    addStyle(
                        SpanStyle(color = color, textDecoration = decoration),
                        start = length,
                        end = length + match.length
                    )
                    append(match)
                    pop()
                }
            }
            lastIndex = end
        }
        // --- 末尾追加 ---
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}

private val imageExtensions = listOf("jpg", "jpeg", "png", "gif")

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
    val matcher = urlRegex.matcher(text)
    val urls = mutableListOf<String>()
    while (matcher.find()) {
        val url = matcher.group(1) ?: continue
        if (imageExtensions.any { url.endsWith(it, ignoreCase = true) }) {
            urls.add(url)
        }
    }
    return urls
}

/**
 * 画像URLの一覧から重複を除外し、出現順を保ったまま返します。
 */
fun distinctImageUrls(urls: List<String>): List<String> {
    return urls.distinct()
}
