package com.websarva.wings.android.slevo.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for image URL extraction helpers.
 */
class LinkUtilsTest {
    /**
     * Confirms that only image URLs are extracted from text content.
     */
    @Test
    fun extractImageUrls_returnsOnlyImageUrls() {
        val text = "Image http://example.com/a.jpg and https://example.com/b.png plus https://example.com/page.html"

        val result = extractImageUrls(text)

        assertEquals(
            listOf(
                "http://example.com/a.jpg",
                "https://example.com/b.png",
            ),
            result,
        )
    }

    /**
     * Confirms that duplicate image URLs are removed while keeping order.
     */
    @Test
    fun distinctImageUrls_removesDuplicatesPreservingOrder() {
        val urls = listOf(
            "https://example.com/a.jpg",
            "https://example.com/b.png",
            "https://example.com/a.jpg",
        )

        val result = distinctImageUrls(urls)

        assertEquals(
            listOf(
                "https://example.com/a.jpg",
                "https://example.com/b.png",
            ),
            result,
        )
    }
}
