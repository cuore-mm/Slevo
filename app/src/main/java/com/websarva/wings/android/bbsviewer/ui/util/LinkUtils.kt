package com.websarva.wings.android.bbsviewer.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import java.util.regex.Pattern

private val urlRegex: Pattern =
    Pattern.compile("(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)")
private val replyRegex: Pattern = Pattern.compile(">>(\\d+)")

/**
 * 入力されたテキストからURLと返信アンカー（>>1など）を検出し、
 * それぞれクリック可能な注釈（Annotation）を付けたAnnotatedStringを生成します。
 */
fun buildUrlAnnotatedString(
    text: String,
    onOpenUrl: (String) -> Unit,
    replyColor: Color = Color.Blue,
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
                    pushStringAnnotation(tag = "URL", annotation = match)
                    addStyle(
                        SpanStyle(textDecoration = TextDecoration.Underline),
                        start = length,
                        end = length + match.length
                    )
                    append(match)
                    pop()
                }

                replyRegex.matcher(match).matches() -> {
                    val number = replyRegex.matcher(match).run { if (find()) group(1) else null }
                    pushStringAnnotation(tag = "REPLY", annotation = number ?: "")
                    addStyle(
                        SpanStyle(color = replyColor),
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
