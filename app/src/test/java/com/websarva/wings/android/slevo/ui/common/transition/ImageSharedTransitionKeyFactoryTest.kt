package com.websarva.wings.android.slevo.ui.common.transition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test
    fun replyPreviewNamespaceSeparatesKindAndHostPost() {
        val targetNamespace = ImageSharedTransitionKeyFactory.replyPreviewNamespace(
            hostNamespace = "thread-post-12",
            previewType = ImageReplyPreviewType.REPLY_TARGET,
            targetPostNumber = 4,
        )
        val sourceNamespace = ImageSharedTransitionKeyFactory.replyPreviewNamespace(
            hostNamespace = "thread-post-12",
            previewType = ImageReplyPreviewType.REPLY_SOURCE,
            targetPostNumber = 4,
        )
        val otherHostNamespace = ImageSharedTransitionKeyFactory.replyPreviewNamespace(
            hostNamespace = "thread-post-13",
            previewType = ImageReplyPreviewType.REPLY_TARGET,
            targetPostNumber = 4,
        )

        assertEquals("thread-post-12-reply-target-reply-4", targetNamespace)
        assertEquals(targetNamespace, ImageSharedTransitionKeyFactory.replyPreviewNamespace(
            "thread-post-12", ImageReplyPreviewType.REPLY_TARGET, 4,
        ))
        assertNotEquals(targetNamespace, sourceNamespace)
        assertNotEquals(targetNamespace, otherHostNamespace)
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
