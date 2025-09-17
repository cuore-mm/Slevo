package com.websarva.wings.android.slevo.ui.thread.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.websarva.wings.android.slevo.ui.util.toHiragana

@Composable
fun rememberHighlightedText(
    baseText: AnnotatedString,
    rawContent: String,
    searchQuery: String,
    highlightColor: Color
): AnnotatedString {
    return remember(baseText, rawContent, searchQuery, highlightColor) {
        val highlightStyle = SpanStyle(background = highlightColor)
        baseText.highlightSearchQuery(
            rawContent = rawContent,
            searchQuery = searchQuery,
            highlightStyle = highlightStyle
        )
    }
}

fun AnnotatedString.highlightSearchQuery(
    rawContent: String,
    searchQuery: String,
    highlightStyle: SpanStyle
): AnnotatedString {
    val highlightRanges = calculateHighlightRanges(rawContent, searchQuery)
    if (highlightRanges.isEmpty()) {
        return this
    }
    val builder = AnnotatedString.Builder(this)
    val textLength = length
    highlightRanges.forEach { range ->
        val start = range.first.coerceIn(0, textLength)
        val end = (range.last + 1).coerceAtMost(textLength)
        if (start < end) {
            builder.addStyle(
                style = highlightStyle,
                start = start,
                end = end
            )
        }
    }
    return builder.toAnnotatedString()
}

fun calculateHighlightRanges(
    rawContent: String,
    searchQuery: String
): List<IntRange> {
    if (searchQuery.isBlank()) {
        return emptyList()
    }
    val normalizedContent = rawContent.toHiragana()
    val normalizedQuery = searchQuery.toHiragana()
    if (normalizedQuery.isBlank()) {
        return emptyList()
    }
    val ranges = mutableListOf<IntRange>()
    var startIndex = 0
    val step = normalizedQuery.length.coerceAtLeast(1)
    while (true) {
        val foundIndex = normalizedContent.indexOf(
            normalizedQuery,
            startIndex = startIndex,
            ignoreCase = true
        )
        if (foundIndex == -1) break
        val end = foundIndex + normalizedQuery.length
        if (end > foundIndex) {
            ranges.add(foundIndex until end)
        }
        startIndex = foundIndex + step
    }
    return ranges
}
