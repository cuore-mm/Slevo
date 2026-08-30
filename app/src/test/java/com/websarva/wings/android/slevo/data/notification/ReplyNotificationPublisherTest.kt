package com.websarva.wings.android.slevo.data.notification

import com.websarva.wings.android.slevo.data.datasource.local.entity.notification.ReplyNotificationEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.notification.ReplyNotificationStatus
import com.websarva.wings.android.slevo.data.model.ThreadId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Android非依存Publisherの結果契約をFakeで検証する。 */
class ReplyNotificationPublisherTest {
    /** Publisherの全結果種別をAndroid非依存の呼び出し元へ伝播できることを確認する。 */
    @Test
    fun fakePublisher_preservesAllDeliveryOutcomes() {
        ReplyNotificationPublishResult.values().forEach { expected ->
            val publisher = FakePublisher(expected)
            assertEquals(expected, publisher.publish(notification()))
        }
    }

    /** Publisher契約の結果だけを固定して返すテスト用実装。 */
    private class FakePublisher(
        private val result: ReplyNotificationPublishResult,
    ) : ReplyNotificationPublisher {
        override fun publish(notification: ReplyNotificationEntity): ReplyNotificationPublishResult = result
    }

    private fun notification() = ReplyNotificationEntity(
        threadId = ThreadId.of("example.com", "test", "123"),
        replyResNo = 3,
        targetOwnResNumbers = "2",
        boardUrl = "https://example.com/test/",
        threadKey = "123",
        threadTitle = "Thread",
        messagePreview = "reply",
        detectedAt = 100L,
        status = ReplyNotificationStatus.DETECTED.name,
    )
}
