package com.websarva.wings.android.bbsviewer.ui.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import java.util.regex.Pattern

private val urlRegex: Pattern = Pattern.compile("(https?://[\\w\\-._~:/?#[\\]@!$&'()*+,;=%]+)")

/**
 * 入力されたテキストから URL を検出し、クリック可能な AnnotatedString を生成します。
 */
fun buildUrlAnnotatedString(text: String): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var lastIndex = 0
    val matcher = urlRegex.matcher(text)
    while (matcher.find()) {
        val start = matcher.start()
        val end = matcher.end()
        val url = matcher.group()
        if (start > lastIndex) {
            builder.append(text.substring(lastIndex, start))
        }
        builder.pushStringAnnotation(tag = "URL", annotation = url)
        builder.pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
        builder.append(url)
        builder.pop() // style
        builder.pop() // annotation
        lastIndex = end
    }
    if (lastIndex < text.length) {
        builder.append(text.substring(lastIndex))
    }
    return builder.toAnnotatedString()
}
