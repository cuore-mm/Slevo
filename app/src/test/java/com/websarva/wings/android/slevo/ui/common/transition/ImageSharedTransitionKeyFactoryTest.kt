package com.websarva.wings.android.slevo.ui.common.transition

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for image shared transition key generation.
 */
class ImageSharedTransitionKeyFactoryTest {
    /**
     * Confirms thread post namespace format.
     */
    @Test
    fun threadPostNamespace_buildsExpectedPattern() {
        val result = ImageSharedTransitionKeyFactory.threadPostNamespace(postNumber = 12)

        assertEquals("thread-post-12", result)
    }

    /**
     * Confirms popup post namespace includes popupId and post number.
     */
    @Test
    fun popupPostNamespace_buildsStablePattern() {
        val result = ImageSharedTransitionKeyFactory.popupPostNamespace(
            popupId = 55L,
            postNumber = 9,
        )

        assertEquals("popup-55-post-9", result)
    }

    /**
     * Confirms full key uses shared separator and preserves order.
     */
    @Test
    fun buildKey_combinesNamespaceUrlAndIndex() {
        val result = ImageSharedTransitionKeyFactory.buildKey(
            transitionNamespace = "popup-55-post-9",
            imageUrl = "https://example.com/a.jpg",
            imageIndex = 2,
        )

        assertEquals("popup-55-post-9|https://example.com/a.jpg|2", result)
    }
}
