package com.websarva.wings.android.slevo.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.websarva.wings.android.slevo.data.datasource.local.entity.notification.ReplyNotificationEntity
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.notification.ReplyNotificationPublishResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Robolectric上で通知内容、安定ID、PendingIntent属性を検証する。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
class AndroidReplyNotificationPublisherTest {
    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    /** 通知Managerと返信通知チャネルをテスト用に初期化する。 */
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
        shadowOf(notificationManager).setNotificationsEnabled(true)
        NotificationChannels.createReplyNotificationChannel(context)
    }

    /** テスト通知を削除して次のテストへ状態を持ち越さない。 */
    @After
    fun tearDown() {
        notificationManager.cancelAll()
    }

    /** 通知本文、安定したPendingIntent、再通知時に置換可能な属性を確認する。 */
    @Test
    fun publish_postsReadableNotificationWithStablePendingIntent() {
        val publisher = AndroidReplyNotificationPublisher(context)
        val entity = notification()

        assertEquals(ReplyNotificationPublishResult.DELIVERED, publisher.publish(entity))
        assertEquals(1, shadowOf(notificationManager).allNotifications.size)

        val posted = shadowOf(notificationManager).allNotifications.single()
        assertEquals("Thread title", posted.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("レス 3", posted.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(posted.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertEquals(NotificationChannels.REPLY_NOTIFICATION_CHANNEL_ID, posted.channelId)

        val pendingIntent = posted.contentIntent
        val shadowPendingIntent = shadowOf(pendingIntent)
        assertTrue(shadowPendingIntent.isActivity)
        assertTrue(shadowPendingIntent.isImmutable)
        assertEquals(
            "https://example.com/test/read.cgi/test/123/",
            shadowPendingIntent.savedIntent.dataString,
        )
    }

    /** OS側で通知が無効な場合は取得処理を失敗させず抑止結果を返すことを確認する。 */
    @Test
    fun publish_whenSystemNotificationsDisabled_returnsSuppressed() {
        shadowOf(notificationManager).setNotificationsEnabled(false)

        val result = AndroidReplyNotificationPublisher(context).publish(notification())

        assertEquals(ReplyNotificationPublishResult.SUPPRESSED, result)
        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }

    private fun notification() = ReplyNotificationEntity(
        threadId = ThreadId.of("example.com", "test", "123"),
        replyResNo = 3,
        targetOwnResNumbers = "2",
        boardUrl = "https://example.com/test/",
        threadKey = "123",
        threadTitle = "Thread title",
        messagePreview = "reply preview",
        detectedAt = 100L,
    )
}
