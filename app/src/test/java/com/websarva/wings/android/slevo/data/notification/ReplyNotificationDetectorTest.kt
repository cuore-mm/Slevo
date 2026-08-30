package com.websarva.wings.android.slevo.data.notification

import com.websarva.wings.android.slevo.data.model.ReplyInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/** [ReplyNotificationDetector] の新着返信判定を検証するテスト。 */
class ReplyNotificationDetectorTest {
    @Test
    fun detectsReplyAfterPreviousBoundary() {
        val result = ReplyNotificationDetector.detect(
            posts = listOf(
                post("root"),
                post("mine"),
                post(">>2 reply"),
            ),
            previousResCount = 2,
            ownPostNumbers = setOf(2),
        )

        assertEquals(listOf(3), result.map { it.replyResNo })
        assertEquals(listOf(2), result.single().targetOwnResNumbers)
    }

    @Test
    fun initialLoadDoesNotDetectExistingReplies() {
        val result = ReplyNotificationDetector.detect(
            posts = listOf(post(">>2 reply")),
            previousResCount = null,
            ownPostNumbers = setOf(2),
        )

        assertEquals(emptyList<ReplyNotificationCandidate>(), result)
    }

    @Test
    fun doesNotDetectWhenResponseCountDecreases() {
        val result = ReplyNotificationDetector.detect(
            posts = listOf(post(">>2 reply")),
            previousResCount = 2,
            ownPostNumbers = setOf(2),
        )

        assertEquals(emptyList<ReplyNotificationCandidate>(), result)
    }

    @Test
    fun doesNotDetectOwnReply() {
        val result = ReplyNotificationDetector.detect(
            posts = listOf(post("root"), post(">>1 my reply")),
            previousResCount = 1,
            ownPostNumbers = setOf(2),
        )

        assertEquals(emptyList<ReplyNotificationCandidate>(), result)
    }

    @Test
    fun combinesMultipleOwnPostReferencesIntoOneCandidate() {
        val result = ReplyNotificationDetector.detect(
            posts = listOf(post("root"), post("mine"), post(">>1 >>2 reply")),
            previousResCount = 2,
            ownPostNumbers = setOf(1, 2),
        )

        assertEquals(1, result.size)
        assertEquals(listOf(1, 2), result.single().targetOwnResNumbers)
    }

    private fun post(content: String) = ReplyInfo(
        name = "name",
        email = "",
        date = "2024/01/01 00:00:00",
        id = "id",
        content = content,
    )
}
