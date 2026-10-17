package com.websarva.wings.android.slevo.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [buildUrlAnnotatedString] のリンク注釈範囲と既存URL認識を検証するテスト。 */
class LinkUtilsTest {
    @Test
    fun annotatesWholeRangeAndCommaAnchorsAndKeepsUrlLinks() {
        val text = ">>1-5,8 https://example.com >>2"
        val annotated = buildUrlAnnotatedString(
            text = text,
            replyColor = Color.Blue,
            imageColor = Color.Green,
            threadColor = Color.Red,
            urlColor = Color.Cyan,
            pressedColor = Color.Magenta,
        )

        val replies = annotated.getStringAnnotations("REPLY", 0, annotated.length)
        val urls = annotated.getStringAnnotations("URL", 0, annotated.length)
        assertEquals(listOf(">>1-5,8", ">>2"), replies.map { it.item })
        assertEquals(listOf("https://example.com"), urls.map { it.item })
        assertEquals(">>1-5,8", annotated.text.substring(replies.first().start, replies.first().end))
        assertTrue(annotated.spanStyles.any { it.item.textDecoration == TextDecoration.Underline })
    }

    @Test
    fun doesNotAnnotateMalformedAnchorTokens() {
        val annotated = buildUrlAnnotatedString(
            text = ">>5-1 >>1, >>999999999999999999999",
            replyColor = Color.Blue,
            imageColor = Color.Green,
            threadColor = Color.Red,
            urlColor = Color.Cyan,
            pressedColor = Color.Magenta,
        )

        assertTrue(annotated.getStringAnnotations("REPLY", 0, annotated.length).isEmpty())
    }
}
