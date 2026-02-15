package com.websarva.wings.android.slevo.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for PostReplacer.
 */
class PostReplacerTest {

    /**
     * Keeps Shift_JIS-compatible text unchanged.
     */
    @Test
    fun replaceEmojisWithNCR_keepsShiftJisCompatibleText() {
        val input = "テストabc123"

        val result = PostReplacer.replaceEmojisWithNCR(input)

        assertEquals(input, result)
    }

    /**
     * Converts single-code-point emoji to NCR.
     */
    @Test
    fun replaceEmojisWithNCR_convertsEmojiToNcr() {
        val result = PostReplacer.replaceEmojisWithNCR("hello😀")

        assertEquals("hello&#128512;", result)
    }

    /**
     * Converts multi-code-point emoji to ordered NCR sequence.
     */
    @Test
    fun replaceEmojisWithNCR_convertsMultiCodePointEmojiToNcrSequence() {
        val result = PostReplacer.replaceEmojisWithNCR("x👋🏾y")

        assertEquals("x&#128075;&#127998;y", result)
    }
}
