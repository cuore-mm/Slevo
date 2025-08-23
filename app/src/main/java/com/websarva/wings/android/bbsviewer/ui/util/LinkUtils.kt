package com.websarva.wings.android.bbsviewer.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import com.websarva.wings.android.bbsviewer.ui.theme.imageUrlColor
import com.websarva.wings.android.bbsviewer.ui.theme.replyColor
import com.websarva.wings.android.bbsviewer.ui.theme.threadUrlColor
import com.websarva.wings.android.bbsviewer.ui.theme.urlColor
import java.util.regex.Pattern

private val urlRegex: Pattern =
    Pattern.compile("(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)")
private val replyRegex: Pattern = Pattern.compile(">>(\\d+)")

/**
 * 入力されたテキストからURLと返信アンカー（>>1など）を検出し、
 * それぞれクリック可能な注釈（Annotation）を付けたAnnotatedStringを生成します。
 */
@Composable
fun buildUrlAnnotatedString(
    text: String,
    onOpenUrl: (String) -> Unit,
    replyColor: Color = replyColor(),
    imageColor: Color = imageUrlColor(),
    threadColor: Color = threadUrlColor(),
    urlColor: Color = urlColor(),
    pressedUrl: String? = null,
    pressedReply: String? = null,
    pressedColor: Color = MaterialTheme.colorScheme.primary,
): AnnotatedString {
    return buildAnnotatedString {
        var lastIndex = 0
        val pattern = Pattern.compile("${urlRegex.pattern()}|${replyRegex.pattern()}")
        val matcher = pattern.matcher(text)
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
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}

private val imageExtensions = listOf("jpg", "jpeg", "png", "gif")

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
