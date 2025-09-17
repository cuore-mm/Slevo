package com.websarva.wings.android.slevo.ui.thread.item

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryHighlighterTest {

    @Test
    fun highlightSearchQuery_addsSpanToMatches() {
        val base = AnnotatedString("テスト投稿です")
        val highlightStyle = SpanStyle(background = Color.Magenta)

        val highlighted = base.highlightSearchQuery(
            rawContent = "テスト投稿です",
            searchQuery = "投稿",
            highlightStyle = highlightStyle
        )

        assertEquals(base.text, highlighted.text)
        val spans = highlighted.spanStyles
        assertEquals(1, spans.size)
        val span = spans.first()
        assertEquals(highlightStyle, span.item)
        assertEquals(3, span.start)
        assertEquals(5, span.end)
    }

    @Test
    fun highlightSearchQuery_returnsOriginalWhenQueryBlank() {
        val base = AnnotatedString("テスト")
        val highlightStyle = SpanStyle(background = Color.Magenta)

        val highlighted = base.highlightSearchQuery(
            rawContent = "テスト",
            searchQuery = "",
            highlightStyle = highlightStyle
        )

        assertEquals(base, highlighted)
    }

    @Test
    fun calculateHighlightRanges_normalizesKana() {
        val ranges = calculateHighlightRanges(
            rawContent = "カタカナのテキスト",
            searchQuery = "かたか",
        )

        assertEquals(listOf(0 until 3), ranges)
    }

    @Test
    fun calculateHighlightRanges_returnsEmptyWhenNotFound() {
        val ranges = calculateHighlightRanges(
            rawContent = "sample",
            searchQuery = "missing"
        )

        assertTrue(ranges.isEmpty())
    }
}
