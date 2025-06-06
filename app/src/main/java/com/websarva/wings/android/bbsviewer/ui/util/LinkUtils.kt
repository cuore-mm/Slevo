package com.websarva.wings.android.bbsviewer.ui.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import java.util.regex.Pattern

private val urlRegex: Pattern = Pattern.compile("(https?://[\\w\\-._~:/?#[\\]@!$&'()*+,;=%]+)")

/**
 * 入力されたテキストから URL を検出し、クリック可能な AnnotatedString を生成します。
 */
fun buildUrlAnnotatedString(
    text: String,
    onOpenUrl: (String) -> Unit,
): AnnotatedString {
    return buildAnnotatedString {
        var lastIndex = 0
        val matcher = urlRegex.matcher(text)
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            val url = matcher.group()
            if (start > lastIndex) {
                append(text.substring(lastIndex, start))
            }
            val link = LinkAnnotation.Url(
                url = url,
                styles = TextLinkStyles(
                    style = SpanStyle(textDecoration = TextDecoration.Underline)
                ),
                linkInteractionListener = LinkInteractionListener { onOpenUrl(url) }
            )
            withLink(link) {
                append(url)
            }
            lastIndex = end
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}
